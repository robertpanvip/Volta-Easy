package com.pan.volta

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.*
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import java.awt.Desktop
import java.awt.Font
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.SwingUtilities


class VoltaStatusWidgetFactory : StatusBarWidgetFactory {
    companion object {
        const val WIDGET_ID = "VoltaEasyStatusWidget"
    }

    override fun getId(): @NotNull String = WIDGET_ID

    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String =
        VoltaBundle.message("node.title")

    override fun createWidget(@NotNull project: Project): @NotNull StatusBarWidget {
        this.watchFocus();
        //return VoltaStatusWidget(project).also { currentWidget = it }
        return VoltaStatusWidget(project).also { widget ->
            //currentWidget = widget
            registerWidget(project, widget)
            SwingUtilities.invokeLater {
                widget.updateLabelText()
            }
        }
    }

    fun watchFocus() {
        if (!focusListenerRegistered) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("activeWindow") {
                    SwingUtilities.invokeLater { refreshVersionForActiveWindow() }
                }
            focusListenerRegistered = true
        }
    }

    override fun isAvailable(@NotNull project: Project): Boolean = true
    override fun disposeWidget(@NotNull widget: StatusBarWidget) {
        //currentWidget = null
        if (widget is VoltaStatusWidget) {
            unregisterWidget(widget.project)
        }
    }

    override fun canBeEnabledOn(@NotNull statusBar: StatusBar): Boolean = true
    override fun isEnabledByDefault(): Boolean = true
    override fun isConfigurable(): Boolean = false

    class VoltaStatusWidget(val project: Project) : CustomStatusBarWidget {
        private val service = VoltaService(project)
        private val versionPopup = VoltaVersionPopup(project, service)
        private val nodeIcon = IconLoader.getIcon(
            "/icons/node.svg",
            javaClass
        )

        private val label: JBLabel = JBLabel(" Node: Loading... ", nodeIcon, JBLabel.LEFT).apply {
            toolTipText = VoltaBundle.message("node.switch.click")
            font = Font("Segoe UI", Font.PLAIN, 12)
            // 正确设置文字颜色（适配IDEA明暗主题的绿色）
            foreground = JBColor(0x4CAF50, 0x66BB6A)
            border = RoundedBorder()
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount != 1) return
                    SwingUtilities.invokeLater {
                        if (!service.isVoltaInstalled()) {
                            showVoltaInstallPrompt()
                        } else {
                            versionPopup.show(label)
                        }
                    }
                }
            })
        }

        private var hasShownPrompt = false  // 简单标志，避免连续点击反复弹

        private fun showVoltaInstallPrompt() {
            if (hasShownPrompt) {
                JOptionPane.showMessageDialog(
                    null,
                    VoltaBundle.message("node.pre.install.office"),
                    VoltaBundle.message("node.pre.install.title"),
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            val choice = JOptionPane.showConfirmDialog(
                null,
                VoltaBundle.message("node.pre.install.message").trimIndent(),
                VoltaBundle.message("node.pre.install.needed"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE
            )

            hasShownPrompt = true

            if (choice == JOptionPane.YES_OPTION) {
                val url = "https://volta.sh/"
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(URI(url))
                    } else {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
                        JOptionPane.showMessageDialog(null, VoltaBundle.message("node.pre.install.copyUrl"))
                    }
                } catch (_: Exception) {
                    JOptionPane.showMessageDialog(null, VoltaBundle.message("node.pre.install.browserfail", url))
                }
            } else {
                JOptionPane.showMessageDialog(null, VoltaBundle.message("node.pre.install.office"))
            }
        }

        fun updateLabelText() {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater { updateLabelText() }
                return
            }
            // 避免在IDE索引期间执行耗时操作
            if (DumbService.isDumb(project)) {
                DumbService.getInstance(project).runWhenSmart { updateLabelText() }
                return
            }
            runWithProgress(
                project,
                VoltaBundle.message("node.switch.title", "--"),
                run = {
                    val versionText = try {
                        if (service.isVoltaInstalled()) {
                            val ver = service.getCurrentNodeVersion()
                            if (ver.isBlank() || ver == "Unknown") VoltaBundle.message("node.version.unknown")
                            else ver
                        } else {
                            VoltaBundle.message("node.version.notinstalled")
                        }
                    } catch (e: Exception) {
                        "错误: ${e.message?.take(20) ?: "未知异常"}"
                    }
                    versionText
                },
                onOk = { result ->
                    label.text = " ${result.replace("v", "", true)} "

                    label.toolTipText = VoltaBundle.message("node.version.popover", result)
                })

        }

        override fun ID(): @NotNull String = WIDGET_ID
        override fun getComponent(): JComponent? = label
        override fun install(@NotNull statusBar: StatusBar) {}
        override fun dispose() {}
    }
}

private val widgetMap = mutableMapOf<Project, VoltaStatusWidgetFactory.VoltaStatusWidget>()

fun registerWidget(project: Project, widget: VoltaStatusWidgetFactory.VoltaStatusWidget) {
    widgetMap[project] = widget
}

fun unregisterWidget(project: Project) {
    widgetMap.remove(project)
}

private fun findActiveProject(): Project? {
    val windowManager = WindowManager.getInstance()

    // 遍历所有打开的 Project
    for (project in ProjectManager.getInstance().openProjects) {
        val frame = windowManager.getFrame(project) as? IdeFrame ?: continue

        // IdeFrame 内部持有的 Window 是否是当前系统前台/激活窗口
        val window = frame.component?.topLevelAncestor as? Window ?: continue

        if (window.isActive) {
            return project
        }
    }

    // 兜底：如果没找到（极少发生），返回 null 或任意一个
    return null
}

fun refreshVersionForActiveWindow() {
    SwingUtilities.invokeLater {
        val active = findActiveProject()
        widgetMap[active]?.updateLabelText()
    }
}

private var focusListenerRegistered = false