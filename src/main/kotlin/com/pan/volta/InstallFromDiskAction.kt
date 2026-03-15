package com.pan.volta

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import javax.swing.JOptionPane

class InstallFromDiskAction(private val project: Project, private val service: VoltaService) :
    AnAction("Install Node From Disk") {

    override fun actionPerformed(e: AnActionEvent) {

        val descriptor = FileChooserDescriptor(
            true, false, false, false, false, false
        )

        val file = FileChooser.chooseFile(descriptor, project, null) ?: return
        runWithProgress(project, "install ...", {
            try {
                service.installNodeFromZip(file.path)
                "Success"
            } catch (e: Exception) {
                e.message
            }.toString()
        }, { message ->
            JOptionPane.showMessageDialog(null, message)
        })

        refreshVersionForActiveWindow();
    }
}