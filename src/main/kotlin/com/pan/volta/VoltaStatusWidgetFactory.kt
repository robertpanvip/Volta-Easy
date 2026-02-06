package com.pan.volta

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import java.awt.Desktop
import java.awt.Font
import java.awt.Toolkit
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
        //return VoltaStatusWidget(project).also { currentWidget = it }
        return VoltaStatusWidget(project).also { widget ->
            currentWidget = widget
            SwingUtilities.invokeLater {
                widget.updateLabelText()
            }
        }
    }

    override fun isAvailable(@NotNull project: Project): Boolean = true
    override fun disposeWidget(@NotNull widget: StatusBarWidget) { currentWidget = null }
    override fun canBeEnabledOn(@NotNull statusBar: StatusBar): Boolean = true
    override fun isEnabledByDefault(): Boolean = true
    override fun isConfigurable(): Boolean = false

    class VoltaStatusWidget(private val project: Project) : CustomStatusBarWidget {
        private val service = VoltaService(project)

        private val label: JBLabel = JBLabel(" Node: Loading... ").apply {
            toolTipText = VoltaBundle.message("node.switch.click")
            font = Font("Segoe UI", Font.PLAIN, 12)
            border = javax.swing.BorderFactory.createEmptyBorder(0, 4, 0, 4)

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount != 1) return
                    SwingUtilities.invokeLater {
                        if (!service.isVoltaInstalled()) {
                            showVoltaInstallPrompt()
                        } else {
                            VoltaVersionPopup(project, service).show(label)
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
                    JOptionPane.showMessageDialog(null, VoltaBundle.message("node.pre.install.browserfail",{url}))
                }
            } else {
                JOptionPane.showMessageDialog(null,  VoltaBundle.message("node.pre.install.office"))
            }
        }

        fun updateLabelText() {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater { updateLabelText() }
                return
            }

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

            label.text = " Node: $versionText "

            label.toolTipText = VoltaBundle.message("node.version.popover",{versionText})
        }

        override fun ID(): @NotNull String = WIDGET_ID
        override fun getComponent(): JComponent? = label
        override fun install(@NotNull statusBar: StatusBar) {}
        override fun dispose() {}
    }
}

private var currentWidget: VoltaStatusWidgetFactory.VoltaStatusWidget? = null

fun refreshVersion() {
    currentWidget?.updateLabelText()
    SwingUtilities.invokeLater {
        currentWidget?.updateLabelText()
    }
}