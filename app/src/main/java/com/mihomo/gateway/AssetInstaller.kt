package com.mihomo.gateway

import android.content.Context
import java.io.File

object AssetInstaller {

    fun ensure(context: Context): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (t: Throwable) {
            "0"
        }

        val marker = Root.run("cat ${Root.BASE}/.app_version 2>/dev/null").trim()
        val binaryOk = Root.run("test -x ${Root.BASE}/mihomo && echo ok 2>/dev/null").trim() == "ok"
        if (marker == version && binaryOk) {
            return "核心已是 $version"
        }

        val cache = File(context.cacheDir, "install")
        cache.deleteRecursively()
        cache.mkdirs()

        copyAsset(context, "mihomo", File(cache, "mihomo"))
        copyAssetDir(context, "scripts", File(cache, "scripts"))
        copyAsset(context, "config.template.yaml", File(cache, "config.template.yaml"))
        copyAsset(context, "gateway.conf", File(cache, "gateway.conf"))
        copyAssetDir(context, "ui", File(cache, "ui"))

        val c = cache.absolutePath
        val cmd = """
            mkdir -p ${Root.BASE}
            cp -f $c/mihomo ${Root.BASE}/mihomo
            cp -f $c/scripts/*.sh ${Root.BASE}/
            chmod 755 ${Root.BASE}/mihomo ${Root.BASE}/*.sh
            [ -f ${Root.BASE}/config.yaml ] || cp -f $c/config.template.yaml ${Root.BASE}/config.yaml
            [ -f ${Root.BASE}/gateway.conf ] || cp -f $c/gateway.conf ${Root.BASE}/gateway.conf
            rm -rf ${Root.BASE}/ui
            cp -rf $c/ui ${Root.BASE}/ui
            [ -d ${Root.BASE}/ui/nuxt ] && mv ${Root.BASE}/ui/nuxt ${Root.BASE}/ui/_nuxt
            echo "$version" > ${Root.BASE}/.app_version
            echo installed
        """.trimIndent()

        val result = Root.run(cmd)
        cache.deleteRecursively()
        return result
    }

    private fun copyAsset(context: Context, name: String, out: File) {
        out.parentFile?.mkdirs()
        context.assets.open(name).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun copyAssetDir(context: Context, path: String, outDir: File) {
        outDir.mkdirs()
        val children = context.assets.list(path) ?: emptyArray()
        for (child in children) {
            val childPath = "$path/$child"
            val childOut = File(outDir, child)
            val nested = context.assets.list(childPath) ?: emptyArray()
            if (nested.isNotEmpty()) {
                copyAssetDir(context, childPath, childOut)
            } else {
                copyAsset(context, childPath, childOut)
            }
        }
    }
}