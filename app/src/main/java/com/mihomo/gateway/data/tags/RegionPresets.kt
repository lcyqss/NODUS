package com.mihomo.gateway.data.tags

import com.mihomo.gateway.model.NodeTag

data class RegionPreset(
    val id: String,
    val name: String,
    val include: List<String>
)

object RegionPresets {

    val all = listOf(
        RegionPreset("__region_HK", "香港", listOf("香港", "hong kong", "hongkong", "hk", "hkg")),
        RegionPreset("__region_TW", "台湾", listOf("台湾", "台灣", "taiwan", "tw", "tpe")),
        RegionPreset("__region_SG", "新加坡", listOf("新加坡", "singapore", "sg", "sgp")),
        RegionPreset("__region_JP", "日本", listOf("日本", "japan", "tokyo", "osaka", "jp", "jpn")),
        RegionPreset("__region_KR", "韩国", listOf("韩国", "韓國", "korea", "seoul", "kr", "kor")),
        RegionPreset(
            "__region_US", "美国",
            listOf("美国", "美國", "united states", "los angeles", "seattle", "us", "usa")
        ),
        RegionPreset(
            "__region_UK", "英国",
            listOf("英国", "英國", "united kingdom", "great britain", "england", "london", "uk", "gbr")
        ),
        RegionPreset("__region_DE", "德国", listOf("德国", "德國", "germany", "frankfurt", "de")),
        RegionPreset("__region_AU", "澳大利亚", listOf("澳大利亚", "澳洲", "australia", "sydney", "au")),
        RegionPreset("__region_CA", "加拿大", listOf("加拿大", "canada", "toronto", "ca")),
        RegionPreset("__region_IN", "印度", listOf("印度", "india", "mumbai", "in")),
        RegionPreset("__region_TR", "土耳其", listOf("土耳其", "turkey", "istanbul", "tr")),
        RegionPreset("__region_RU", "俄罗斯", listOf("俄罗斯", "russia", "moscow", "ru"))
    )

    fun asTag(preset: RegionPreset): NodeTag = NodeTag(
        id = preset.id,
        name = preset.name,
        include = preset.include
    )
}
