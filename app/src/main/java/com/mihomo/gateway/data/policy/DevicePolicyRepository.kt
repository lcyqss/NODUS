package com.mihomo.gateway.data.policy

import android.content.Context
import com.mihomo.gateway.core.NeighborResolver
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.catalog.DeviceTarget
import com.mihomo.gateway.data.catalog.NodeCatalogRepository
import com.mihomo.gateway.data.catalog.NodeInfo
import com.mihomo.gateway.data.config.DevicePolicyConfig
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.data.tags.NodeTagRepository
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.model.TagExpr
import com.mihomo.gateway.model.DevicePolicy
import com.mihomo.gateway.model.DevicePolicyCodec
import com.mihomo.gateway.model.DeviceSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** 运行期解析结果：策略 + 实际 IP + 命中的节点 + 生成的目标组。 */
data class PolicyRuntime(
    val policy: DevicePolicy,
    val ip: String?,
    val primaryNodes: List<NodeInfo>,
    val fallbackNodes: List<NodeInfo>,
    val targetGroup: String?,
    val error: String?
)

/** 供订阅安装流程在写盘后调用：只改 config，不触发重载。 */
interface DevicePolicyApplier {
    suspend fun applyToConfig(): Result<Unit>

    /** 配置被外部回滚后调用，让下一次 sync 重新判断。 */
    fun invalidateAppliedState()
}

interface DevicePolicyRepository : DevicePolicyApplier {
    val policies: Flow<List<DevicePolicy>>

    suspend fun runtime(): List<PolicyRuntime>
    suspend fun devices(): List<DeviceTarget>
    suspend fun nodes(): List<NodeInfo>
    suspend fun tags(): List<NodeTag>

    /** 保存并立即应用（运行中会热重载）。 */
    suspend fun save(policy: DevicePolicy): Result<String>

    suspend fun delete(id: String): Result<String>

    /** 重新解析设备/节点并应用。 */
    suspend fun apply(): Result<String>

    /** 按延迟阈值切换优先/回落组（仅 maxDelayMs > 0 的策略）。 */
    suspend fun evaluateRouting(): Result<Int>

    /** 设备 IP 变化（DHCP 续租/重连）时自动重建规则；无变化返回 false。 */
    suspend fun syncIfNeeded(): Result<Boolean>
}

private val IPV4 = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

class DevicePolicyRepositoryImpl(
    private val context: Context,
    private val settings: SettingsStore,
    private val catalog: NodeCatalogRepository,
    private val tagRepository: NodeTagRepository,
    private val neighbors: NeighborResolver,
    private val shell: ShellExecutor,
    private val api: MihomoApi,
    private val base: String = "/data/adb/mihomo"
) : DevicePolicyRepository {

    /** 上次写入配置的 MAC→IP 映射；null 表示尚未从现有配置读取。 */
    private var appliedIps: Map<String, String>? = null

    /** 每台设备上次执行延迟评估的时间（毫秒），用于按设备间隔调度。 */
    private val lastEvaluatedAt = mutableMapOf<String, Long>()

    override val policies: Flow<List<DevicePolicy>> =
        settings.settings.map { DevicePolicyCodec.decode(it.devicePolicies) }

    override suspend fun nodes(): List<NodeInfo> = catalog.nodes()

    override suspend fun tags(): List<NodeTag> = tagRepository.allTags(catalog.nodes())

    override suspend fun devices(): List<DeviceTarget> = withContext(Dispatchers.IO) {
        neighbors.resolve().values
            .filter { it.mac.isNotBlank() && it.ip.isNotBlank() }
            .groupBy { it.mac.lowercase() }
            .mapNotNull { (mac, list) ->
                val ip = pickIp(list.map { it.ip }) ?: return@mapNotNull null
                DeviceTarget(
                    ip = ip,
                    mac = mac,
                    hostname = list.firstNotNullOfOrNull { it.hostname?.takeIf { name -> name.isNotBlank() } }
                )
            }
            .sortedBy { it.ip }
    }

    override suspend fun runtime(): List<PolicyRuntime> = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        val policies = DevicePolicyCodec.decode(current.devicePolicies)
        if (policies.isEmpty()) return@withContext emptyList()
        val allNodes = catalog.nodes()
        val allTags = tagRepository.allTags(allNodes)
        val missingTag = policies.any { policy ->
            (exprIds(policy.primary.expr) + exprIds(policy.fallback.expr)).any { tagId ->
                allTags.none { it.id == tagId }
            }
        }
        val neighborsByMac = neighbors.resolve().values
            .filter { it.mac.isNotBlank() && it.ip.isNotBlank() }
            .groupBy { it.mac.lowercase() }
        policies.map { policy ->
            val primary = allNodes.filter { node ->
                tagRepository.matches(allTags, policy.primary.expr, node)
            }
            val fallback = allNodes.filter { node ->
                tagRepository.matches(allTags, policy.fallback.expr, node)
            }
            val ip = pickIp(neighborsByMac[policy.mac.lowercase()].orEmpty().map { it.ip })
            val error = when {
                !policy.enabled -> null
                missingTag -> "引用的标签已被删除"
                primary.isEmpty() && fallback.isEmpty() -> "没有匹配的节点"
                ip == null -> "设备未在线（未解析到 IP）"
                else -> null
            }
            PolicyRuntime(
                policy = policy,
                ip = ip,
                primaryNodes = primary,
                fallbackNodes = fallback,
                targetGroup = if (error == null) groupTarget(policy, primary, fallback) else null,
                error = error
            )
        }
    }

    override suspend fun save(policy: DevicePolicy): Result<String> = withContext(Dispatchers.IO) {
        val current = DevicePolicyCodec.decode(settings.settings.first().devicePolicies)
        // 同一 MAC 只保留一条策略，避免出现多条 SRC-IP 规则只命中第一条
        val list = current.filterNot {
            it.id == policy.id || it.mac.equals(policy.mac, true)
        } + policy
        settings.update { it.copy(devicePolicies = DevicePolicyCodec.encode(list)) }
        apply().map { "已应用 ${policy.label.ifBlank { policy.mac }}" }
    }

    override suspend fun delete(id: String): Result<String> = withContext(Dispatchers.IO) {
        val current = DevicePolicyCodec.decode(settings.settings.first().devicePolicies)
        settings.update { it.copy(devicePolicies = DevicePolicyCodec.encode(current.filterNot { it.id == id })) }
        apply().map { "已删除设备策略" }
    }

    override suspend fun apply(): Result<String> = withContext(Dispatchers.IO) {
        val applied = applyToConfig()
        if (applied.isFailure) {
            // 写入/设备离线失败后不能相信旧的已应用映射，否则后续 sync 会误判为无变化
            appliedIps = null
            return@withContext Result.failure(applied.exceptionOrNull()!!)
        }
        if (!coreRunning()) return@withContext Result.success("已写入配置（内核未运行）")
        val reload = api.reloadConfigs()
        if (reload.isFailure) {
            shell.run("cp -f $base/config.yaml.bak.policy $base/config.yaml", 20_000)
            api.reloadConfigs()
            appliedIps = null
            return@withContext Result.failure(
                IllegalStateException("重载失败，已回滚: ${reload.exceptionOrNull()?.message}")
            )
        }
        evaluateRouting()
        Result.success("设备策略已生效")
    }

    /**
     * 延迟阈值调度：优先组最优延迟超过 maxDelayMs（或无可用节点）时，
     * 把 __dev_ 根组（select）切到回落组；恢复后切回优先组。
     */
    override suspend fun evaluateRouting(): Result<Int> = withContext(Dispatchers.IO) {
        if (!coreRunning()) return@withContext Result.success(0)
        val current = DevicePolicyCodec.decode(settings.settings.first().devicePolicies)
        if (current.none { it.enabled && it.maxDelayMs > 0 }) {
            return@withContext Result.success(0)
        }
        val now = System.currentTimeMillis()
        val defaultSec = settings.settings.first().policyIntervalSec.coerceIn(30, 300)
        val plans = runtime().filter {
            it.policy.enabled &&
                it.policy.maxDelayMs > 0 &&
                it.primaryNodes.isNotEmpty() &&
                it.fallbackNodes.isNotEmpty()
        }.filter { plan ->
            val interval = plan.policy.scheduleIntervalSec.takeIf { it > 0 } ?: defaultSec
            now - (lastEvaluatedAt[plan.policy.id] ?: 0L) >= interval * 1000L
        }
        var changed = 0
        plans.forEach { plan ->
            lastEvaluatedAt[plan.policy.id] = now
            val baseName = groupName(plan.policy)
            val delays = api.groupDelay("${baseName}_p").getOrNull().orEmpty()
            val best = delays.values.filter { it > 0 }.minOrNull() ?: -1
            val target = if (best in 1..plan.policy.maxDelayMs) "${baseName}_p" else "${baseName}_f"
            api.select(baseName, target).onSuccess { changed++ }
        }
        Result.success(changed)
    }

    override fun invalidateAppliedState() {
        appliedIps = null
    }

    override suspend fun applyToConfig(): Result<Unit> = withContext(Dispatchers.IO) {
        // 任何一次写入失败/中途退出都让已应用映射失效，下次 sync 会重试
        appliedIps = null
        val original = shell.run("cat $base/config.yaml 2>/dev/null").stdout
        if (original.isBlank()) return@withContext Result.failure(IllegalStateException("读取配置失败"))

        val plans = runtime()
        val enabledPlans = plans.filter { it.policy.enabled }
        val matchedPlans = enabledPlans.filter { it.targetGroup != null }
        // 有策略能匹配到节点、但设备全部离线：保留现有配置，避免临时离线把策略块清空
        if (matchedPlans.isNotEmpty() && matchedPlans.all { it.ip == null }) {
            return@withContext Result.failure(
                IllegalStateException("设备离线，已保留现有策略配置")
            )
        }
        val validPlans = matchedPlans.filter { it.ip != null }
        val groups = mutableListOf<String>()
        val rules = mutableListOf<String>()
        validPlans.forEach { plan ->
            groups += buildGroups(plan)
            rules += ruleLine(plan.ip ?: "", plan.targetGroup)
        }

        var text = DevicePolicyConfig.strip(original)
        text = DevicePolicyConfig.injectGroups(text, groups)
        text = DevicePolicyConfig.injectRules(text, rules)

        // 内容没有变化就不要重写配置/备份，避免无意义的 IO 与 flash 写入
        if (text.trimEnd('\n') == original.trimEnd('\n')) {
            appliedIps = validPlans.mapNotNull { plan ->
                plan.ip?.let { plan.policy.id to it }
            }.toMap()
            return@withContext Result.success(Unit)
        }

        val cache = File(context.cacheDir, "config.device-policy.yaml")
        runCatching { cache.writeText(text) }
            .getOrElse { return@withContext Result.failure(it) }

        val replace = shell.run(
            "cp -f $base/config.yaml $base/config.yaml.bak.policy && " +
                "cp -f ${cache.absolutePath} $base/config.yaml",
            20_000
        )
        if (replace.code == 0) {
            appliedIps = validPlans.mapNotNull { plan ->
                plan.ip?.let { plan.policy.id to it }
            }.toMap()
            Result.success(Unit)
        } else Result.failure(IllegalStateException("写入配置失败"))
    }

    override suspend fun syncIfNeeded(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (DevicePolicyCodec.decode(settings.settings.first().devicePolicies).none { it.enabled }) {
            return@withContext Result.success(false)
        }
        val plans = runtime().filter {
            it.policy.enabled && it.targetGroup != null && it.ip != null
        }
        if (plans.isEmpty()) return@withContext Result.success(false)
        if (appliedIps == null) appliedIps = readAppliedIps()
        val changed = plans.any { appliedIps?.get(it.policy.id) != it.ip }
        if (!changed) return@withContext Result.success(false)

        val applied = applyToConfig()
        if (applied.isFailure) {
            appliedIps = null
            return@withContext Result.failure(applied.exceptionOrNull()!!)
        }
        if (coreRunning()) {
            val reload = api.reloadConfigs()
            if (reload.isFailure) {
                shell.run("cp -f $base/config.yaml.bak.policy $base/config.yaml", 20_000)
                api.reloadConfigs()
                appliedIps = null
                return@withContext Result.failure(
                    IllegalStateException("设备 IP 变化后重载失败: ${reload.exceptionOrNull()?.message}")
                )
            }
        }
        Result.success(true)
    }

    /** 从现有 config 的 SRC-IP 规则反推已生效的 IP，避免进程重启后误判。 */
    private suspend fun readAppliedIps(): Map<String, String> {
        val text = shell.run("cat $base/config.yaml 2>/dev/null").stdout
        if (text.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        DevicePolicyCodec.decode(settings.settings.first().devicePolicies).forEach { policy ->
            val base = groupName(policy)
            val regex = Regex("""^-\s*SRC-IP-CIDR6?,([^/]+)/\d+,($base(?:_[pf])?)$""")
            text.lineSequence().forEach { line ->
                val match = regex.find(line.trim()) ?: return@forEach
                result[policy.id] = match.groupValues[1]
            }
        }
        return result
    }

    private fun exprIds(expr: TagExpr?): Set<String> = when (expr) {
        null -> emptySet()
        is TagExpr.Ref -> setOf(expr.tagId)
        is TagExpr.And -> expr.items.flatMap { exprIds(it) }.toSet()
        is TagExpr.Or -> expr.items.flatMap { exprIds(it) }.toSet()
        is TagExpr.Not -> exprIds(expr.item)
    }

    private fun buildGroups(plan: PolicyRuntime): List<String> {        val baseName = groupName(plan.policy)
        val out = mutableListOf<String>()
        if (plan.primaryNodes.isNotEmpty()) {
            out += groupLine("${baseName}_p", "url-test", plan.primaryNodes.map { it.name }, 300)
        }
        if (plan.fallbackNodes.isNotEmpty()) {
            out += groupLine("${baseName}_f", "url-test", plan.fallbackNodes.map { it.name }, 300)
        }
        if (plan.primaryNodes.isNotEmpty() && plan.fallbackNodes.isNotEmpty()) {
            out += groupLine(
                baseName,
                if (plan.policy.maxDelayMs > 0) "select" else "fallback",
                listOf("${baseName}_p", "${baseName}_f"),
                60
            )
        }
        return out
    }

    private fun groupTarget(
        policy: DevicePolicy,
        primary: List<NodeInfo>,
        fallback: List<NodeInfo>
    ): String? = when {
        primary.isNotEmpty() && fallback.isNotEmpty() -> groupName(policy)
        primary.isNotEmpty() -> "${groupName(policy)}_p"
        fallback.isNotEmpty() -> "${groupName(policy)}_f"
        else -> null
    }

    private fun groupName(policy: DevicePolicy): String =
        "__dev_" + policy.id.replace("-", "").take(10)

    private fun groupLine(
        name: String,
        type: String,
        members: List<String>,
        interval: Int
    ): String {
        val proxies = members.joinToString(", ") { "'" + it.replace("'", "''") + "'" }
        return "- { name: $name, type: $type, proxies: [$proxies], " +
            "url: 'https://www.gstatic.com/generate_204', interval: $interval }"
    }

    private fun pickIp(ips: List<String>): String? {
        if (ips.isEmpty()) return null
        // 当前产品拦截客户端 IPv6；只有 link-local 时视为设备离线，
        // 避免生成无法加载的 SRC-IP-CIDR6 规则。
        return ips.firstOrNull { IPV4.matches(it) }
    }

    private fun ruleLine(ip: String, target: String?): String {
        if (target.isNullOrBlank() || ip.isBlank()) return ""
        return if (ip.contains(":")) "SRC-IP-CIDR6,$ip/128,$target"
        else "SRC-IP-CIDR,$ip/32,$target"
    }


    private suspend fun coreRunning(): Boolean =
        shell.run("pidof mihomo >/dev/null 2>&1 && echo yes || echo no").stdout.trim() == "yes"
}
