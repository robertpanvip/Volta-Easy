package com.pan.volta

import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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

fun getVoltaHome(): File {

    val env = System.getenv("VOLTA_HOME")
    if (!env.isNullOrBlank()) {
        return File(env)
    }

    val userHome = System.getProperty("user.home")

    return if (System.getProperty("os.name").contains("Windows")) {
        File(System.getenv("LOCALAPPDATA"), "Volta")
    } else {
        File(userHome, ".volta")
    }
}

fun getVersionByFileName(fileName: String): String? {
    val versionRegex = Regex("node-v([\\d.]+)")
    val match = versionRegex.find(fileName)
        ?: throw RuntimeException("Cannot parse Node version from zip name")
    val version = match.groupValues[1]
    return version;
}

class VoltaService(private val project: Project) {
    companion object {
        @Volatile
        private var cachedVersions: List<String>? = null
        @Volatile
        private var versionsCacheTimestamp: Long = 0
        @Volatile
        private var cachedRecommendedVersion: String? = null
        @Volatile
        private var recommendedVersionTimestamp: Long = 0
        private const val cacheDuration = 30000
        
        fun preloadStaticVersions(project: Project) {
            Thread {
                try {
                    val service = VoltaService(project)
                    service.getInstalledVersions()
                    service.getProjectRecommendedVersion()
                } catch (_: Exception) {
                }
            }.start()
        }
    }

    fun installNodeFromZip(zipPath: String) {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            throw RuntimeException("Zip file not found: $zipPath")
        }

        val version = getVersionByFileName(zipFile.name)

        // 1. 获取 Volta Home
        val voltaHome = getVoltaHome()

        val inventoryDir = File(voltaHome, "tools${File.separator}inventory${File.separator}node")
        inventoryDir.mkdirs()

        // 2. 复制 zip 到 inventory
        val targetZip = File(inventoryDir, zipFile.name)
        zipFile.copyTo(targetZip, overwrite = true)

        // 3. 调用 Volta CLI 安装 Node
        val processBuilder = ProcessBuilder().apply {
            command("volta", "install", "node@$version")
            inheritIO() // 可选：把输出打印到控制台
        }

        val process = processBuilder.start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Volta install failed with exit code $exitCode")
        }
        
        clearCache()
        preloadVersions()
    }

    fun getCurrentNodeVersion(): String {
        val output = execute(arrayOf("volta", "list", "node", "--current"),8)
        if (output.exitCode != 0) return "Unknown"

        // 用正则提取 vX.Y.Z
        val regex = Regex("""node@(\d+\.\d+\.\d+)""")
        val version = regex.find(output.stdout.trim())?.groups?.get(1)?.value ?: "Unknown"
        return version
    }
    val os = System.getProperty("os.name").lowercase()

    fun isVoltaInstalledFast(): Boolean {
        val path = when {
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA") ?: return false
                Paths.get(localAppData, "Volta", "bin", "volta.exe")
            }

            else -> {
                val home = System.getProperty("user.home") ?: return false
                Paths.get(home, ".volta", "bin", "volta")
            }
        }

        return Files.exists(path)
    }

    fun isVoltaInstalled(): Boolean {
        if(isVoltaInstalledFast()){
            return true
        }
        val output = execute(arrayOf("volta", "--version"),40)
        return output.exitCode == 0 && output.stdout.trim().matches(Regex("^\\d+\\.\\d+\\.\\d+.*$"))
    }

    fun getInstalledVersions(): List<String> {
        val now = System.currentTimeMillis()
        if (cachedVersions != null && now - versionsCacheTimestamp < cacheDuration) {
            return cachedVersions!!
        }
        
        val output = execute(arrayOf("volta", "list", "node", "--format", "plain"))
        if (output.exitCode != 0) return emptyList()

        val versions = output.stdout.lines()
            .filter { it.trim().isNotEmpty() && it.contains("@") }
            .map { it.trim().substringAfter("@") }
            .map { if (it.startsWith("v")) it else "v$it" }
            .sortedDescending()
        
        cachedVersions = versions
        versionsCacheTimestamp = now
        return versions
    }

    fun preloadVersions() {
        Thread {
            try {
                getInstalledVersions()
            } catch (_: Exception) {
            }
        }.start()
    }

    fun clearCache() {
        cachedVersions = null
        versionsCacheTimestamp = 0
    }

    fun switchVersion(version: String): String {
        // Volta 没有独立的 "use"，项目级用 pin，全局用 install（这里假设用全局 install 作为切换）
        val output = execute(arrayOf("volta", "install", "node@$version"))
        val result = if (output.exitCode == 0) "成功切换到 Node $version（Volta）"
        else "切换失败：${output.stderr}"
        clearCache()
        preloadVersions()
        return result
    }

    fun installVersion(version: String): String {
        val output = execute(arrayOf("volta", "install", "node@$version"))
        val result = if (output.exitCode == 0) "安装成功：Node $version"
        else "安装失败：${output.stderr}"
        clearCache()
        preloadVersions()
        return result
    }

    fun uninstallVersion(version: String): String {
        try {
            // 1. 尝试用 Volta CLI 卸载
            val output = execute(arrayOf("volta", "uninstall", "node@$version"))

            val result = if (output.exitCode == 0) {
                "卸载成功：Node $version"
            } else {
                // CLI 卸载失败，尝试手动删除 image
                val voltaHome = getVoltaHome()
                // 去掉前缀 v
                val cleanVersion = version.removePrefix("v")
                val imageDir = File(voltaHome, "tools/image/node/$cleanVersion")
                if (imageDir.exists()) {
                    imageDir.deleteRecursively()
                }
                "卸载完成（手动删除）：Node $version"
            }
            clearCache()
            preloadVersions()
            return result
        } catch (e: Exception) {
            return "卸载失败：${e.message}"
        }
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
        if (!pkgFile.exists()) {
            return null;
        }
        val pkgText = pkgFile.readText()
        val pkgJson = try {
            JsonParser.parseString(pkgText).asJsonObject
        } catch (_: Exception) {
            return null
        }

        // 1. 优先 Volta
        pkgJson.getAsJsonObject("volta")?.get("node")?.asString?.let { return it }

        // 2. engines.node
        pkgJson.getAsJsonObject("engines")?.get("node")?.asString?.let { return it }

        // 3. package-lock.json fallback
        val lockFile = File("${project.basePath}/package-lock.json")
        if (!lockFile.exists()) {
            return null;
        }
        val lockText = lockFile.readText()
        val lockJson = try {
            JsonParser.parseString(lockText).asJsonObject
        } catch (_: Exception) {
            return null
        }

        val lockfileVersion = lockJson.get("lockfileVersion")?.asInt ?: 1
        val allVersions = mutableListOf<Version>()

        fun addVersionRange(rangeStr: String) {
            // 支持 || 分隔
            rangeStr.split("||").forEach { part ->
                Version.parse(part.trim().removePrefix("^").removePrefix("~").removePrefix(">=").removePrefix(">"))
                    ?.let {
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
        val now = System.currentTimeMillis()
        if (cachedRecommendedVersion != null && now - recommendedVersionTimestamp < cacheDuration) {
            return cachedRecommendedVersion
        }

        // 优先 Volta 的 package.json "volta" 字段，其次 .nvmrc
        val pkgFile = File("${project.basePath}/package.json")
        if (!pkgFile.exists()) {
            cachedRecommendedVersion = null
            recommendedVersionTimestamp = now
            return null
        }
        val pkgText = pkgFile.readText()
        val v = getPackageNodeVersion()
        if (v !== null) {
            cachedRecommendedVersion = v
            recommendedVersionTimestamp = now
            return v
        }

        if (pkgFile.exists()) {
            try {
                val match = Regex(""""node"\s*:\s*"([^"]+)"""").find(pkgText)
                val result = match?.groupValues?.get(1)?.let { "v$it" }
                if (result != null) {
                    cachedRecommendedVersion = result
                    recommendedVersionTimestamp = now
                    return result
                }
            } catch (_: Exception) {
            }
        }

        val nvmrc = File("${project.basePath}/.nvmrc")
        if (nvmrc.exists()) {
            try {
                var ver = nvmrc.readText().trim()
                if (!ver.startsWith("v")) ver = "v$ver"
                cachedRecommendedVersion = ver
                recommendedVersionTimestamp = now
                return ver
            } catch (_: Exception) {
            }
        }

        cachedRecommendedVersion = null
        recommendedVersionTimestamp = now
        return null
    }

    private fun execute(command: Array<String>, timeout: Long = 30): ProcessOutput {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode = -1

        var process: Process? = null
        try {
            val pb = ProcessBuilder(*command)
                .directory(File(project.basePath ?: return ProcessOutput("", "No project path", -1)))
                .redirectErrorStream(false)

            process = pb.start()

            // 用线程读取流，避免阻塞（推荐做法，虽然你当前代码已分别读，但加超时更安全）
            val stdoutThread = Thread {
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { stdout.append(it).append("\n") }
                }
            }.apply { start() }

            val stderrThread = Thread {
                process.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { stderr.append(it).append("\n") }
                }
            }.apply { start() }

            // 等待完成，但最多等 8 秒
            if (process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS)) {
                exitCode = process.exitValue()
            } else {
                // 超时 → 强制杀
                process.destroyForcibly()           // 先温和 destroy
                process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)  // 再等一会
                if (process.isAlive) {
                    // 极端情况再强制（Windows 上 destroyForcibly 更接近 kill -9）
                    // 但 destroyForcibly 已经是 forceful 了，这里只是再确认
                }
                stderr.append("执行超时，已强制终止 (可能有残留子进程)")
                exitCode = -2
            }

            // 确保读取线程结束
            stdoutThread.join(1000)
            stderrThread.join(1000)

        } catch (e: Exception) {
            stderr.append("执行异常：${e.message}\n")
            process?.destroyForcibly()
        } finally {
            process?.let {
                if (it.isAlive) {
                    it.destroyForcibly()
                }
            }
        }

        return ProcessOutput(stdout.toString().trim(), stderr.toString().trim(), exitCode)
    }

    data class ProcessOutput(val stdout: String, val stderr: String, val exitCode: Int)
}