package com.mihomo.gateway.core

import android.content.Context
import java.io.File

interface CoreInstaller {
    /** 首次或版本变化时把 assets 中的内核/脚本/模板解压到 /data/adb/mihomo。 */
    suspend fun ensure(): Result<String>
}

class AssetInstaller(
    private val context: Context,
    private val shell: ShellExecutor,
    private val base: String = "/data/adb/mihomo"
) : CoreInstaller {

    override suspend fun ensure(): Result<String> {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

        val marker = shell.run("cat $base/.app_version 2>/dev/null").stdout.trim()
        val binaryOk = shell.run("test -x $base/mihomo && echo ok 2>/dev/null").stdout.trim() == "ok"
        if (marker == version && binaryOk) {
            return Result.success("核心已是 $version")
        }

        val cache = File(context.cacheDir, "install")
        cache.deleteRecursively()
        cache.mkdirs()

        runCatching {
            copyAsset("mihomo", File(cache, "mihomo"))
            copyAssetDir("scripts", File(cache, "scripts"))
            copyAsset("ipv6/v6ra", File(cache, "v6ra"))
            copyAsset("ipv6/v6dhcp", File(cache, "v6dhcp"))
            copyAsset("config.template.yaml", File(cache, "config.template.yaml"))
            copyAsset("gateway.conf", File(cache, "gateway.conf"))
            copyAsset("GeoSite.dat", File(cache, "GeoSite.dat"))
            copyAsset("GeoIP.dat", File(cache, "GeoIP.dat"))
        }.onFailure {
            cache.deleteRecursively()
            return Result.failure(it)
        }

        val c = cache.absolutePath
        val command = """
            mkdir -p $base
            cp -f $c/mihomo $base/mihomo.new
            chmod 755 $base/mihomo.new
            mv -f $base/mihomo.new $base/mihomo
            cp -f $c/scripts/*.sh $base/
            cp -f $c/v6ra $base/v6ra.new
            chmod 755 $base/v6ra.new
            mv -f $base/v6ra.new $base/v6ra
            cp -f $c/v6dhcp $base/v6dhcp.new
            chmod 755 $base/v6dhcp.new
            mv -f $base/v6dhcp.new $base/v6dhcp
            chmod 755 $base/mihomo $base/*.sh $base/v6ra $base/v6dhcp
            [ -f $base/config.yaml ] || cp -f $c/config.template.yaml $base/config.yaml
            [ -f $base/gateway.conf ] || cp -f $c/gateway.conf $base/gateway.conf
            [ -f $base/GeoSite.dat ] || cp -f $c/GeoSite.dat $base/GeoSite.dat
            [ -f $base/GeoIP.dat ] || cp -f $c/GeoIP.dat $base/GeoIP.dat
            echo "$version" > $base/.app_version
            echo installed
        """.trimIndent()

        val result = shell.run(command, 120_000)
        cache.deleteRecursively()

        return if (result.code == 0) Result.success(result.stdout.trim())
        else Result.failure(IllegalStateException(result.stdout.take(300)))
    }

    private fun copyAsset(name: String, out: File) {
        out.parentFile?.mkdirs()
        context.assets.open(name).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun copyAssetDir(path: String, outDir: File) {
        outDir.mkdirs()
        val children = context.assets.list(path) ?: emptyArray()
        for (child in children) {
            val childPath = "$path/$child"
            val childOut = File(outDir, child)
            val nested = context.assets.list(childPath) ?: emptyArray()
            if (nested.isNotEmpty()) {
                copyAssetDir(childPath, childOut)
            } else {
                copyAsset(childPath, childOut)
            }
        }
    }
}
