package com.yuyan.imemodule.prefs.behavior

import com.yuyan.imemodule.view.preference.ManagedPreference

enum class AiAssistMode {
    Continue,
    Polish,
    Expand,
    Formal;

    companion object : ManagedPreference.StringLikeCodec<AiAssistMode> {
        override fun decode(raw: String): AiAssistMode {
            return entries.firstOrNull { it.name == raw } ?: Continue
        }
    }
}
