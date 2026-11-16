package com.mihomo.gateway.data.update

import com.mihomo.gateway.model.AppUpdateInfo
import java.io.File

interface AppUpdateRepository {
    suspend fun latest(currentVersion: String): Result<AppUpdateInfo?>
    suspend fun download(info: AppUpdateInfo, onProgress: (Int) -> Unit): Result<File>
}
