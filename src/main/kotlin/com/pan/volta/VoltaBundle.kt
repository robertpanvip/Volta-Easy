package com.pan.volta

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

class VoltaBundle : DynamicBundle("messages.VoltaBundle") {
    companion object {
        private val INSTANCE = VoltaBundle()
        fun message(@PropertyKey(resourceBundle = "messages.VoltaBundle") key: String, vararg params: Any) = INSTANCE.getMessage(key, *params)
    }
}