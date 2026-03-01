package com.pan.volta

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame

class VoltaActivationListener : ApplicationActivationListener {

    override fun applicationActivated(ideFrame: IdeFrame) {
        // 当 IDE 从后台切回前台时触发
        refreshVersion()
    }
}

