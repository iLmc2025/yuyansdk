package com.yuyan.imemodule.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.yuyan.imemodule.R
import com.yuyan.imemodule.ai.AiTextService
import com.yuyan.imemodule.data.theme.Theme
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.prefs.behavior.AiAssistRole
import com.yuyan.imemodule.utils.toast
import com.yuyan.imemodule.view.widget.ImeEditText
import splitties.dimensions.dp

class AiAssistView(
    context: Context,
    private val onRun: (String, AiTextService.AssistMode, AiAssistRole) -> Unit,
) : LinearLayout(context) {

    private val input = ImeEditText(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        isCursorVisible = true
        isFocusable = true
        isFocusableInTouchMode = true
        minLines = 2
        setPadding(dp(10))
        setHint(R.string.ai_panel_input_hint)
    }

    private var aiRole: AiAssistRole = AiAssistRole.Default

    init {
        orientation = VERTICAL

        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(TextView(context).apply {
            setText(R.string.ai_assist_role)
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val roleRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val roleTabs = mutableListOf<TextView>()

        fun updateRoleUi() {
            roleTabs.forEach { tv -> tv.alpha = if (tv.tag == aiRole) 1f else 0.7f }
        }

        AiAssistRole.entries.forEach { role ->
            val titleRes = when (role) {
                AiAssistRole.Default -> R.string.ai_assist_role_default
                AiAssistRole.HighEq -> R.string.ai_assist_role_high_eq
                AiAssistRole.LoveGuru -> R.string.ai_assist_role_love_guru
                AiAssistRole.Workplace -> R.string.ai_assist_role_workplace
                AiAssistRole.SocialMedia -> R.string.ai_assist_role_social_media
            }
            val tab = TextView(context).apply {
                text = context.getString(titleRes)
                tag = role
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener {
                    aiRole = role
                    updateRoleUi()
                }
            }
            roleTabs.add(tab)
            roleRow.addView(tab)
        }
        updateRoleUi()

        addView(HorizontalScrollView(context).apply { addView(roleRow) }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val actionRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        fun addAction(labelRes: Int, mode: AiTextService.AssistMode) {
            actionRow.addView(TextView(context).apply {
                text = context.getString(labelRes)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener { runAction(mode) }
            })
        }
        addAction(R.string.ai_assist_mode_continue, AiTextService.AssistMode.Continue)
        addAction(R.string.ai_assist_mode_polish, AiTextService.AssistMode.Polish)
        addAction(R.string.ai_assist_mode_expand, AiTextService.AssistMode.Expand)
        addAction(R.string.ai_assist_mode_formal, AiTextService.AssistMode.Formal)
        addView(actionRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun handleAiAssistView() {
        input.requestFocus()
    }

    fun setInitialText(text: String) {
        input.setText(text)
        input.setSelection(text.length)
    }

    private fun runAction(mode: AiTextService.AssistMode) {
        val text = input.text.toString().trim()
        if (text.isBlank()) {
            context.toast(R.string.ai_panel_input_empty)
            return
        }
        onRun(text, mode, aiRole)
    }

    fun sendKeyEvent(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                input.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                input.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            KeyEvent.KEYCODE_ENTER -> runAction(AiTextService.AssistMode.Continue)
            else -> {
                val unicodeChar: Char = KeyEvent(KeyEvent.ACTION_DOWN, keyCode).unicodeChar.toChar()
                if (unicodeChar != Character.MIN_VALUE) input.commitText(unicodeChar.toString())
            }
        }
    }

    fun updateTheme(theme: Theme) {
        val keyTextColor = ThemeManager.activeTheme.keyTextColor
        setBackgroundColor(theme.barColor)
        input.background = GradientDrawable().apply {
            setColor(ThemeManager.activeTheme.keyBackgroundColor)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ThemeManager.prefs.keyRadius.getValue().toFloat()
        }
        input.setTextColor(keyTextColor)
        input.setHintTextColor(keyTextColor)
    }
}
