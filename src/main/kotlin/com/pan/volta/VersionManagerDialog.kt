package com.pan.volta

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import javax.swing.*
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JComponent

class ManageNodeVersionsAction(
    private val project: Project,
    private val service: VoltaService,
    val installNewVersion: () -> Unit
) : AnAction("Manage Installed Versions…") {

    override fun actionPerformed(e: AnActionEvent) {
        // 打开 Dialog
        NodeVersionManagerDialog(project, service, installNewVersion).show()
    }
}


class NodeVersionManagerDialog(
    private val project: Project,
    private val service: VoltaService,
    private val installNewVersion: () -> Unit
) : DialogWrapper(project) {

    private val listModel = DefaultListModel<String>()
    private val list = JBList(listModel)

    init {
        title = "Manage Node Versions"
        // 初始化 list 数据
        service.getInstalledVersions().forEach { listModel.addElement(it) }

        init() // 必须调用 DialogWrapper 的 init
    }

    override fun createCenterPanel(): JComponent {
        // 用 ToolbarDecorator 给 JBList 添加按钮
        val decorator = ToolbarDecorator.createDecorator(list)
            .setAddAction {
                // 这里可以打开 InstallFromDiskAction
                installNewVersion();
            }
            .setRemoveAction {
                val selected = list.selectedValue ?: return@setRemoveAction
                if (selected.endsWith("(default)")) {
                    JOptionPane.showMessageDialog(null, "default can not delete")
                    return@setRemoveAction
                }
                // 删除选中版本
                val confirmed = JOptionPane.showConfirmDialog(
                    null,
                    "Delete Node $selected?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION
                )
                if (confirmed == JOptionPane.YES_OPTION) {
                    runWithProgress(
                        project, "uninstall Node $selected ...",
                        run = {
                            try {
                                service.uninstallVersion(selected)
                                listModel.removeElement(selected)
                            } catch (e: Exception) {
                                JOptionPane.showMessageDialog(null, e.message)
                            }
                            ""
                        },
                        onOk = { result ->
                            refreshVersionForActiveWindow()
                        },
                    )
                }
            }
            .setEditAction(null) // 不需要编辑按钮
            .disableUpDownActions() // 禁止移动
        val panel = decorator.createPanel()
        panel.preferredSize = Dimension(250, 400) // ✅ 设置宽高
        return panel
    }
}