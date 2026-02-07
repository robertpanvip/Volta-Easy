package com.pan.volta

import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

// ---------------- Semver 手写 ----------------
data class Version(val major: Int, val minor: Int = 0, val patch: Int = 0) : Comparable<Version> {
    companion object {
        fun parse(v: String): Version? {
            val clean = v.trim().removePrefix("^").removePrefix("~")
            val parts = clean.split(".")
            return try {
                Version(
                    parts.getOrNull(0)?.toInt() ?: 0,
                    parts.getOrNull(1)?.toInt() ?: 0,
                    parts.getOrNull(2)?.toInt() ?: 0
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun compareTo(other: Version): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        return patch - other.patch
    }

    override fun toString(): String = "$major.$minor.$patch"
}

class VoltaService(private val project: Project) {

    fun getCurrentNodeVersion(): String {
        val output = execute(arrayOf("node", "-v"))
        return if (output.exitCode == 0) output.stdout.trim() else "Unknown"
    }

    fun isVoltaInstalled(): Boolean {
        val output = execute(arrayOf("volta", "--version"))
        return output.exitCode == 0 && output.stdout.trim().matches(Regex("^\\d+\\.\\d+\\.\\d+.*$"))
    }

    fun getInstalledVersions(): List<String> {
        val output = execute(arrayOf("volta", "list", "node", "--format", "plain"))
        if (output.exitCode != 0) return emptyList()

        return output.stdout.lines()
            .filter { it.trim().isNotEmpty() && it.contains("@") }
            .map { it.trim().substringAfter("@") }
            .map { if (it.startsWith("v")) it else "v$it" }
            .sortedDescending()
    }

    fun switchVersion(version: String): String {
        // Volta 没有独立的 "use"，项目级用 pin，全局用 install（这里假设用全局 install 作为切换）
        val output = execute(arrayOf("volta", "install", "node@$version"))
        return if (output.exitCode == 0) "成功切换到 Node $version（Volta）"
        else "切换失败：${output.stderr}"
    }

    fun installVersion(version: String): String {
        val output = execute(arrayOf("volta", "install", "node@$version"))
        return if (output.exitCode == 0) "安装成功：Node $version"
        else "安装失败：${output.stderr}"
    }

    fun uninstallVersion(version: String): String {
        val output = execute(arrayOf("volta", "uninstall", "node@$version"))
        return if (output.exitCode == 0) "卸载成功：Node $version"
        else "卸载失败：${output.stderr}"
    }

    fun pinToProject(version: String): String {
        val output = execute(arrayOf("volta", "pin", "node@$version"))
        return if (output.exitCode == 0) "已固定项目使用 Node $version（写入 package.json）\n立即生效！"
        else "固定失败：${output.stderr}"
    }

    fun getPackageManagerVersion(manager: String): String {
        val output = execute(arrayOf(manager, "-v"))
        return if (output.exitCode == 0) output.stdout.trim() else "N/A"
    }

    fun getPackageNodeVersion(): String? {
        val pkgFile = File("${project.basePath}/package.json")
        val pkgText = pkgFile.readText()
        val pkgJson = try { JsonParser.parseString(pkgText).asJsonObject } catch (_: Exception) { return null }

        // 1. 优先 Volta
        pkgJson.getAsJsonObject("volta")?.get("node")?.asString?.let { return it }

        // 2. engines.node
        pkgJson.getAsJsonObject("engines")?.get("node")?.asString?.let { return it }

        // 3. package-lock.json fallback
        val lockFile = File("${project.basePath}/package-lock.json")
        val lockText = lockFile.readText()
        val lockJson = try { JsonParser.parseString(lockText).asJsonObject } catch (_: Exception) { return null }

        val lockfileVersion = lockJson.get("lockfileVersion")?.asInt ?: 1
        val allVersions = mutableListOf<Version>()

        fun addVersionRange(rangeStr: String) {
            // 支持 || 分隔
            rangeStr.split("||").forEach { part ->
                Version.parse(part.trim().removePrefix("^").removePrefix("~").removePrefix(">=").removePrefix(">"))?.let {
                    allVersions.add(it)
                }
            }
        }

        if (lockfileVersion >= 3) {
            lockJson.getAsJsonObject("packages")?.entrySet()?.forEach { entry ->
                val pkg = entry.value.asJsonObject
                pkg.getAsJsonObject("engines")?.get("node")?.asString?.let { addVersionRange(it) }
            }
        } else {
            lockJson.getAsJsonObject("dependencies")?.entrySet()?.forEach { entry ->
                val dep = entry.value.asJsonObject
                dep.getAsJsonObject("engines")?.get("node")?.asString?.let { addVersionRange(it) }
            }
        }

        if (allVersions.isEmpty()) return null

        // 4. 取最大版本
        val maxVersion = allVersions.maxOrNull() ?: return null
        return maxVersion.toString()
    }

    fun getProjectRecommendedVersion(): String? {

        // 优先 Volta 的 package.json "volta" 字段，其次 .nvmrc
        val pkgFile = File("${project.basePath}/package.json")
        val pkgText = pkgFile.readText()
        val v = getPackageNodeVersion()
        if (v !== null) {
            return v;
        }

        if (pkgFile.exists()) {
            try {
                val match = Regex(""""node"\s*:\s*"([^"]+)"""").find(pkgText)
                match?.groupValues?.get(1)?.let { return "v$it" }
            } catch (_: Exception) {
            }
        }

        val nvmrc = File("${project.basePath}/.nvmrc")
        if (nvmrc.exists()) {
            try {
                var ver = nvmrc.readText().trim()
                if (!ver.startsWith("v")) ver = "v$ver"
                return ver
            } catch (_: Exception) {
            }
        }


        return null
    }

    private fun execute(command: Array<String>): ProcessOutput {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode = 1

        try {
            val pb = ProcessBuilder(*command)
                .directory(File(project.basePath ?: System.getProperty("user.dir")))
                .redirectErrorStream(false)

            val process = pb.start()

            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stdout.append(line).append("\n")
                }
            }

            process.errorStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stderr.append(line).append("\n")
                }
            }

            exitCode = process.waitFor()
        } catch (e: Exception) {
            stderr.append("执行异常：${e.message}")
        }

        return ProcessOutput(stdout.toString().trim(), stderr.toString().trim(), exitCode)
    }

    data class ProcessOutput(val stdout: String, val stderr: String, val exitCode: Int)
}