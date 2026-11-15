package com.mihomo.gateway.data.tags

import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.model.ConditionTemplate
import com.mihomo.gateway.model.ConditionTemplateCodec
import com.mihomo.gateway.model.TagExpr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/** 条件模板：保存一棵条件树，供其他设备策略套用。 */
interface ConditionTemplateRepository {
    val templates: Flow<List<ConditionTemplate>>

    suspend fun save(name: String, expr: TagExpr): Result<Unit>
    suspend fun rename(id: String, name: String): Result<Unit>
    suspend fun move(id: String, up: Boolean)
    suspend fun delete(id: String)
}

class ConditionTemplateRepositoryImpl(
    private val settings: SettingsStore
) : ConditionTemplateRepository {

    override val templates: Flow<List<ConditionTemplate>> =
        settings.settings.map { ConditionTemplateCodec.decode(it.conditionTemplates) }

    override suspend fun save(name: String, expr: TagExpr): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("模板名不能为空"))
            }
            val current = ConditionTemplateCodec.decode(settings.settings.first().conditionTemplates)
            val template = ConditionTemplate(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                expr = expr
            )
            settings.update {
                it.copy(conditionTemplates = ConditionTemplateCodec.encode(current + template))
            }
            Result.success(Unit)
        }

    override suspend fun rename(id: String, name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("模板名不能为空"))
            }
            val current = ConditionTemplateCodec.decode(settings.settings.first().conditionTemplates)
            settings.update {
                it.copy(
                    conditionTemplates = ConditionTemplateCodec.encode(
                        current.map { t -> if (t.id == id) t.copy(name = trimmed) else t }
                    )
                )
            }
            Result.success(Unit)
        }

    override suspend fun move(id: String, up: Boolean) {
        withContext(Dispatchers.IO) {
            val current = ConditionTemplateCodec.decode(settings.settings.first().conditionTemplates)
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return@withContext
            val target = if (up) index - 1 else index + 1
            if (target !in current.indices) return@withContext
            val list = current.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            settings.update { it.copy(conditionTemplates = ConditionTemplateCodec.encode(list)) }
        }
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            val current = ConditionTemplateCodec.decode(settings.settings.first().conditionTemplates)
            settings.update {
                it.copy(conditionTemplates = ConditionTemplateCodec.encode(current.filterNot { t -> t.id == id }))
            }
        }
    }
}
