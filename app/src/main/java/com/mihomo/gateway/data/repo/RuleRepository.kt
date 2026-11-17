package com.mihomo.gateway.data.repo

import com.mihomo.gateway.core.AppConfig
import android.content.Context
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.store.RuleStore
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.RuleKind
import com.mihomo.gateway.data.config.DevicePolicyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** 最近一次成功应用时的规则快照；generation 变化表示外部完成了 apply。 */
data class RulesApplyState(
    val generation: Long = 0L,
    val rules: List<CustomRule> = emptyList()
)

interface RuleRepository {
    val rules: StateFlow<List<CustomRule>>

    /** 供规则页之外的调用方（恢复备份、订阅切换）同步「已应用」状态。 */
    val applyState: StateFlow<RulesApplyState>
    fun add(kind: RuleKind, value: String, target: String, noResolve: Boolean): Result<Unit>
    fun update(rule: CustomRule): Result<Unit>
    fun delete(id: String)
    fun move(id: String, up: Boolean)

    /** 注入到 config.yaml 顶部并重载；失败自动回滚。 */
    suspend fun apply(): Result<String>

    /** 只把启用规则写进 config.yaml（不重载内核），用于内核未运行时。 */
    suspend fun injectOnly(): Result<Unit>

    /** 读取 config.yaml 中当前已注入的自定义规则行（不含标记）。 */
    suspend fun appliedRuleLines(): List<String>

    /** 与订阅自带规则重复（同类型同内容）的规则 key 集合。 */
    suspend fun conflicts(): Set<String>
}

class RuleRepositoryImpl(
    private val context: Context,
    private val shell: ShellExecutor,
    private val api: MihomoApi,
    private val store: RuleStore,
    private val base: String = AppConfig.BASE_DIR
) : RuleRepository {

    override val rules: StateFlow<List<CustomRule>> = store.rules

    private val _applyState = MutableStateFlow(RulesApplyState())
    override val applyState: StateFlow<RulesApplyState> = _applyState

    override fun add(kind: RuleKind, value: String, target: String, noResolve: Boolean): Result<Unit> {
        val trimmed = value.trim()
        validate(kind, trimmed, target)?.let { return Result.failure(IllegalArgumentException(it)) }
        store.add(store.newRule(kind, trimmed, target.trim(), noResolve))
        return Result.success(Unit)
    }

    override fun update(rule: CustomRule): Result<Unit> {
        validate(rule.kind, rule.value.trim(), rule.target)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        store.update(rule.copy(value = rule.value.trim(), target = rule.target.trim()))
        return Result.success(Unit)
    }

    override fun delete(id: String) {
        store.delete(id)
    }

    override fun move(id: String, up: Boolean) {
        store.move(id, up)
    }

    override suspend fun apply(): Result<String> = withContext(Dispatchers.IO) {
        val enabled = store.rules.value.filter { it.enabled }
        val current = shell.run("cat $base/config.yaml 2>/dev/null").stdout
        if (current.isBlank()) return@withContext Result.failure(IllegalStateException("读取配置失败"))

        val injected = inject(current, enabled.map { it.toRuleLine() })
        val cache = File(context.cacheDir, "config.rules.yaml")
        cache.writeText(injected)

        val replace = shell.run(
            "cp -f $base/config.yaml $base/config.yaml.bak.rules && " +
                "cp -f ${cache.absolutePath} $base/config.yaml",
            20_000
        )
        if (replace.code != 0) {
            return@withContext Result.failure(IllegalStateException("写入配置失败"))
        }

        val reload = api.reloadConfigs()
        if (reload.isFailure) {
            shell.run("cp -f $base/config.yaml.bak.rules $base/config.yaml", 20_000)
            api.reloadConfigs()
            return@withContext Result.failure(
                IllegalStateException("重载失败，已回滚: ${reload.exceptionOrNull()?.message}")
            )
        }
        _applyState.value = RulesApplyState(
            generation = _applyState.value.generation + 1,
            rules = store.rules.value
        )
        Result.success(
            if (enabled.isEmpty()) "已清除自定义规则" else "已应用 ${enabled.size} 条自定义规则"
        )
    }

    override suspend fun appliedRuleLines(): List<String> = withContext(Dispatchers.IO) {
        val text = shell.run("cat $base/config.yaml 2>/dev/null").stdout
        val lines = mutableListOf<String>()
        var inside = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line == MARK_START -> inside = true
                line == MARK_END -> inside = false
                inside && line.startsWith("- ") -> lines += line.removePrefix("- ").trim()
            }
        }
        lines
    }

    override suspend fun injectOnly(): Result<Unit> = withContext(Dispatchers.IO) {
        val enabled = store.rules.value.filter { it.enabled }
        val current = shell.run("cat $base/config.yaml 2>/dev/null").stdout
        if (current.isBlank()) return@withContext Result.failure(IllegalStateException("读取配置失败"))

        val injected = inject(current, enabled.map { it.toRuleLine() })
        val cache = File(context.cacheDir, "config.rules.yaml")
        cache.writeText(injected)

        val replace = shell.run("cp -f ${cache.absolutePath} $base/config.yaml", 20_000)
        if (replace.code == 0) Result.success(Unit)
        else Result.failure(IllegalStateException("写入配置失败"))
    }

    override suspend fun conflicts(): Set<String> {
        val builtIn = api.rules().getOrNull().orEmpty()
            .map { it.type.uppercase() + "|" + it.payload }
            .toSet()
        return store.rules.value
            .map { it.key().uppercase() }
            .filter { builtIn.contains(it) }
            .toSet()
    }

    private fun validate(kind: RuleKind, value: String, target: String): String? {
        if (value.isBlank()) return "内容不能为空"
        if (target.isBlank()) return "目标策略不能为空"
        return when (kind) {
            RuleKind.IP_CIDR, RuleKind.SRC_IP_CIDR ->
                if (!value.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}$"))) {
                    "IPv4 网段格式不正确"
                } else {
                    null
                }
            RuleKind.IP_CIDR6 ->
                if (!value.contains(":") || !value.contains("/")) "IPv6 网段格式不正确" else null
            RuleKind.DOMAIN, RuleKind.DOMAIN_SUFFIX ->
                if (value.contains(" ")) "域名不能包含空格" else null
            else -> null
        }
    }

    companion object {
        const val MARK_START = "# >>> custom rules"
        const val MARK_END = "# <<< custom rules"

        /** 把自定义规则插入 `rules:` 之后，移除旧的标记块。 */
        fun inject(original: String, ruleLines: List<String>): String {
            val kept = mutableListOf<String>()
            var skipping = false
            original.lineSequence().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed == MARK_START -> skipping = true
                    trimmed == MARK_END -> skipping = false
                    !skipping -> kept.add(line)
                }
            }

            // 设备策略的 SRC-IP 规则必须保持在最前；自定义规则插在它之后
            val policyEnd = kept.indexOfFirst { it.trim() == DevicePolicyConfig.RULE_END }
            val rulesIndex = if (policyEnd >= 0) policyEnd else kept.indexOfFirst { it.trimEnd() == "rules:" }

            // 与订阅现有 rules 的缩进保持一致，否则 YAML 解析失败
            val indent = if (rulesIndex >= 0) {
                kept.drop(rulesIndex + 1)
                    .firstOrNull { it.startsWith(" ") && it.trimStart().startsWith("- ") }
                    ?.takeWhile { it == ' ' }
                    ?: "  "
            } else {
                "  "
            }

            val block = buildList {
                add("$indent$MARK_START")
                ruleLines.forEach { add("$indent- $it") }
                add("$indent$MARK_END")
            }
            return if (rulesIndex >= 0) {
                kept.addAll(rulesIndex + 1, block)
                kept.joinToString("\n")
            } else {
                (kept + "rules:" + block).joinToString("\n")
            }
        }
    }
}
