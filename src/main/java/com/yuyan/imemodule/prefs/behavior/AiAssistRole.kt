package com.yuyan.imemodule.prefs.behavior

import com.yuyan.imemodule.view.preference.ManagedPreference

enum class AiAssistRole(val systemPrompt: String) {
    Default("你是专业中文写作助手，输出自然、准确、简洁。"),
    HighEq("你擅长高情商沟通：表达体贴、留有余地、尊重对方感受。"),
    LoveGuru("你擅长恋爱沟通：语气真诚、有分寸、不油腻，避免冒犯。"),
    Workplace("你擅长职场沟通：目标明确、礼貌专业、可执行。"),
    SocialMedia("你擅长社交媒体文案：简短有记忆点，适度口语化。");

    companion object : ManagedPreference.StringLikeCodec<AiAssistRole> {
        override fun decode(raw: String): AiAssistRole {
            return entries.firstOrNull { it.name == raw } ?: Default
        }
    }
}
