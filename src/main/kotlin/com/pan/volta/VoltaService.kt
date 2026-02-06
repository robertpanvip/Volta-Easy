package com.pan.volta

import com.intellij.openapi.project.Project
import java.io.File

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

    fun getProjectRecommendedVersion(): String? {
        // 优先 Volta 的 package.json "volta" 字段，其次 .nvmrc
        val pkgFile = File("${project.basePath}/package.json")
        if (pkgFile.exists()) {
            try {
                val content = pkgFile.readText()
                val match = Regex(""""node"\s*:\s*"([^"]+)"""").find(content)
                match?.groupValues?.get(1)?.let { return "v$it" }
            } catch (_: Exception) {}
        }

        val nvmrc = File("${project.basePath}/.nvmrc")
        if (nvmrc.exists()) {
            try {
                var ver = nvmrc.readText().trim()
                if (!ver.startsWith("v")) ver = "v$ver"
                return ver
            } catch (_: Exception) {}
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