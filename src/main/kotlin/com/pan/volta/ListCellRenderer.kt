package com.pan.volta
import com.intellij.icons.AllIcons
import com.intellij.util.ui.EmptyIcon
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

class ListCellRenderer : DefaultListCellRenderer() {

    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {

        val versionText = value?.toString().orEmpty()
        val displayText = versionText
            .replace("v", "", true)
            .replace("(default)", "")
            .trim()

        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
            preferredSize = java.awt.Dimension(0, 32) // ⭐ 固定行高
            background = if (isSelected) list.selectionBackground else list.background
        }

        val label = JLabel(displayText).apply {
            icon = if (versionText.endsWith("(default)")) {
                AllIcons.Actions.Checked
            } else {
                EmptyIcon.ICON_16
            }
            foreground = if (isSelected) list.selectionForeground else list.foreground
        }

       /* val deleteIcon = JLabel(AllIcons.Actions.DeleteTagHover).apply {
            isVisible = !versionText.endsWith("(default)")
            border = BorderFactory.createEmptyBorder(0, 8, 0, 0)
        }*/

        panel.add(label, BorderLayout.WEST)
        //panel.add(deleteIcon, BorderLayout.EAST)

        return panel
    }
}
