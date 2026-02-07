package com.pan.volta

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.vcsUtil.showAbove
import java.awt.Component
import javax.swing.*

class VoltaVersionPopup(
    private val project: Project,
    private val service: VoltaService
) {
    private fun installNewVersion() {
        val input = JOptionPane.showInputDialog(
            null,
            VoltaBundle.message("node.install.new.message"),
            VoltaBundle.message("node.install.new.title"),
            JOptionPane.QUESTION_MESSAGE
        ) ?: return

        val version = input.trim()
        if (version.isBlank()) {
            JOptionPane.showMessageDialog(
                null,
                VoltaBundle.message("node.install.dialog.message"),
                VoltaBundle.message("node.install.dialog.tip"),
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        runWithProgress(
            VoltaBundle.message("node.install.progress.title", version),
            run = {
                service.installVersion(version)
            },
            onOk = { result ->
                showResultDialog(result, VoltaBundle.message("node.install.result.title"))
                refreshStatusBarVersion()
            }
        )
    }

    private fun showResultDialog(message: String, title: String) {
        val type =
            if (message.contains(VoltaBundle.message("node.install.result.info")) || message.contains("Done")) JOptionPane.INFORMATION_MESSAGE
            else JOptionPane.ERROR_MESSAGE
        JOptionPane.showMessageDialog(null, message, title, type)
    }

    fun runWithProgress(
        title: String,
        run: () -> String,            // 后台执行逻辑，返回结果字符串
        onOk: (result: String) -> Unit        // 完成后的回调，接收 run 的返回值
    ) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, title, true) {
                override fun run(indicator: ProgressIndicator) {
                    // 指示器可以显示正在运行
                    indicator.isIndeterminate = true
                    // 实际切换
                    val result = run()
                    // UI 更新必须在 EDT
                    SwingUtilities.invokeLater {
                        onOk(result)
                    }
                }
            }
        )
    }

    private fun refreshStatusBarVersion() = refreshVersion()

    fun show(component: Component) {
        val items = service.getInstalledVersions()
        val installButton = LinkLabel<Any>(
            "➕ ${VoltaBundle.message("node.install.button")}",
            null
        ) { _, _ ->
            installNewVersion()
        }.apply {
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        }

        // 1. 提取推荐版本相关变量，提升可读性
        val recommendedVersion = service.getProjectRecommendedVersion()
        val recommendedVersionText = recommendedVersion?.let {
            VoltaBundle.message("node.project.recommendation", it)
        }

// 2. 构建弹窗列表 - 简洁且高性能
        val list = buildList {
            // 添加推荐版本（非空时）
            recommendedVersionText?.let { add(it) }
            // 批量添加其他版本项（替代forEach，性能更好）
            addAll(items)
        }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(list)
            .setTitle("")
            .setRenderer(ListCellRenderer())
            .setSettingButton(installButton)
            .setItemChosenCallback { selected ->
                runWithProgress(
                    VoltaBundle.message("node.switch.title", selected),
                    run = {
                        // 简化条件判断，消除currentProject临时变量
                        if (recommendedVersionText != null && selected == recommendedVersionText) {
                            service.installVersion("v${recommendedVersion}")
                        } else {
                            service.switchVersion(selected)
                        }
                    },
                    onOk = {
                        refreshStatusBarVersion()
                    }
                )
            }
            .createPopup()

        popup.showAbove(component)
    }
}