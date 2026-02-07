package com.pan.volta

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.border.AbstractBorder

class RoundedBorder : AbstractBorder() {
    private val cornerRadius = 16 // 圆角半径，可根据需要调整
    private val borderColor = JBColor(0x4CAF50, 0x66BB6A)  // 适配明暗主题的边框色
    override fun paintBorder(
        c: Component,
        g: Graphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        // 绘制边框
        g2.color = borderColor
        g2.draw(RoundRectangle2D.Double(x.toDouble()+3, y.toDouble()+7, (width - 6).toDouble(), (height - 14).toDouble(), cornerRadius.toDouble(), cornerRadius.toDouble()))
    }

    override fun getBorderInsets(c: Component): Insets {
        return JBUI.emptyInsets() // 内边距：上下8px，左右8px
    }

    override fun getBorderInsets(c: Component, insets: Insets): Insets {
        insets.top = 0
        insets.left = 8
        insets.bottom = 0
        insets.right =8
        return insets
    }
}