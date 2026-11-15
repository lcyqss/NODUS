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
}

interface DevicePolicyRepository : DevicePolicyApplier {
    val policies: Flow<List<DevicePolicy>>

    suspend fun runtime(): List<PolicyRuntime>
    suspend fun devices(): List<DeviceTarget>
    suspend fun nodes(): List<NodeInfo>

    /** 保存并立即应用（运行中会热重载）。 */
    suspend fun save(policy: DevicePolicy): Result<String>

    suspend fun delete(id: String): Result<String>

    /** 重新解析设备/节点并应用。 */
    suspend fun apply(): Result<String>
}

private val IPV4 = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

class DevicePolicyRepositoryImpl(
    private val context: Context,
    private val settings: SettingsStore,
    private val catalog: NodeCatalogRepository,
    private val neighbors: NeighborResolver,
    private val shell: ShellExecutor,
    private val api: MihomoApi,
    private val base: String = "/data/adb/mihomo"
) : DevicePolicyRepository {

    override val policies: Flow<List<DevicePolicy>> =
        settings.settings.map { DevicePolicyCodec.decode(it.devicePolicies) }

    override suspend fun nodes(): List<NodeInfo> = catalog.nodes()

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
        val allNodes = catalog.nodes()
        val neighborsByMac = neighbors.resolve().values
            .filter { it.mac.isNotBlank() && it.ip.isNotBlank() }
            .groupBy { it.mac.lowercase() }
        DevicePolicyCodec.decode(settings.settings.first().devicePolicies).map { policy ->
            val primary = match(allNodes, policy.primary)
            val fallback = match(allNodes, policy.fallback)
            val ip = pickIp(neighborsByMac[policy.mac.lowercase()].orEmpty().map { it.ip })
            val error = when {
                !policy.enabled -> null
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
        val list = current.filterNot { it.id == policy.id } + policy
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
        if (applied.isFailure) return@withContext Result.failure(applied.exceptionOrNull()!!)
        if (!coreRunning()) return@withContext Result.success("已写入配置（内核未运行）")
        val reload = api.reloadConfigs()
        if (reload.isFailure) {
            shell.run("cp -f $base/config.yaml.bak.policy $base/config.yaml", 20_000)
            api.reloadConfigs()
            return@withContext Result.failure(
                IllegalStateException("重载失败，已回滚: ${reload.exceptionOrNull()?.message}")
            )
        }
        Result.success("设备策略已生效")
    }

    override suspend fun applyToConfig(): Result<Unit> = withContext(Dispatchers.IO) {
        val original = shell.run("cat $base/config.yaml 2>/dev/null").stdout
        if (original.isBlank()) return@withContext Result.failure(IllegalStateException("读取配置失败"))

        val plans = runtime()
        val groups = mutableListOf<String>()
        val rules = mutableListOf<String>()
        plans.filter { it.policy.enabled && it.targetGroup != null && it.ip != null }.forEach { plan ->
            groups += buildGroups(plan)
            rules += ruleLine(plan.ip ?: "", plan.targetGroup)
        }

        var text = DevicePolicyConfig.strip(original)
        text = DevicePolicyConfig.injectGroups(text, groups)
        text = DevicePolicyConfig.injectRules(text, rules)

        val cache = File(context.cacheDir, "config.device-policy.yaml")
        runCatching { cache.writeText(text) }
            .getOrElse { return@withContext Result.failure(it) }

        val replace = shell.run(
            "cp -f $base/config.yaml $base/config.yaml.bak.policy && " +
                "cp -f ${cache.absolutePath} $base/config.yaml",
            20_000
        )
        if (replace.code == 0) Result.success(Unit)
        else Result.failure(IllegalStateException("写入配置失败"))
    }

    private fun buildGroups(plan: PolicyRuntime): List<String> {
        val baseName = groupName(plan.policy)
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
                "fallback",
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
        return ips.firstOrNull { it.matches(IPV4) }
            ?: ips.firstOrNull { it.contains(":") && !it.startsWith("fe80", true) }
            ?: ips.firstOrNull { it.contains(":") }
    }

    private fun ruleLine(ip: String, target: String?): String {
        if (target.isNullOrBlank() || ip.isBlank()) return ""
        return if (ip.contains(":")) "SRC-IP-CIDR6,$ip/128,$target"
        else "SRC-IP-CIDR,$ip/32,$target"
    }

    private fun match(nodes: List<NodeInfo>, selector: DeviceSelector): List<NodeInfo> =
        nodes.filter { node ->
            (selector.region.isBlank() || node.region == selector.region) &&
                (selector.protocol.isBlank() || node.type == selector.protocol)
        }

    private suspend fun coreRunning(): Boolean =
        shell.run("pidof mihomo >/dev/null 2>&1 && echo yes || echo no").stdout.trim() == "yes"
}
