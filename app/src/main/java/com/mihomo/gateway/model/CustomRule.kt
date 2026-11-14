package com.mihomo.gateway.model

enum class RuleKind(val wire: String, val label: String, val needsNoResolve: Boolean) {
    DOMAIN("DOMAIN", "域名", false),
    DOMAIN_SUFFIX("DOMAIN-SUFFIX", "域名后缀", false),
    DOMAIN_KEYWORD("DOMAIN-KEYWORD", "域名关键字", false),
    IP_CIDR("IP-CIDR", "IPv4 网段", true),
    IP_CIDR6("IP-CIDR6", "IPv6 网段", true),
    SRC_IP_CIDR("SRC-IP-CIDR", "客户端网段", true);

    companion object {
        fun fromWire(value: String): RuleKind? = entries.firstOrNull { it.wire.equals(value, true) }
    }
}

data class CustomRule(
    val id: String,
    val kind: RuleKind,
    val value: String,
    val target: String,
    val enabled: Boolean = true,
    val noResolve: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 生成 mihomo 规则行内容，例如 DOMAIN-SUFFIX,example.com,DIRECT */
    fun toRuleLine(): String = buildString {
        append(kind.wire).append(',').append(value).append(',').append(target)
        if (noResolve || kind.needsNoResolve) append(",no-resolve")
    }

    fun key(): String = kind.wire + "|" + value
}
