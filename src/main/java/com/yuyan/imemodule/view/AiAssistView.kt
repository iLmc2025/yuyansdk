package com.yuyan.imemodule.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
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

    // 主页面的“输入条”，点击后打开二级编辑页
    private val inputBar = ImeEditText(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        isCursorVisible = false
        isFocusable = false
        isFocusableInTouchMode = false
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setHint(R.string.ai_panel_input_bar_hint)
        setOnClickListener { openEditorPanel() }
    }

    // 二级编辑页
    private val editorInput = ImeEditText(context).apply {
        gravity = Gravity.TOP
        isCursorVisible = true
        isFocusable = true
        isFocusableInTouchMode = true
        minLines = 4
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setHint(R.string.ai_panel_input_hint)
    }

    private val counter = TextView(context).apply {
        gravity = Gravity.END
    }

    private val editorDone = TextView(context).apply {
        setText(R.string.done)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener {
            inputBar.setText(editorInput.text.toString())
            closeEditorPanel()
        }
    }

    private val editorPanel = LinearLayout(context).apply {
        orientation = VERTICAL
        visibility = GONE
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(editorInput, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(counter, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(editorDone, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        })
    }

    private var aiRole: AiAssistRole = AiAssistRole.Default

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true

        addView(TextView(context).apply {
            setText(R.string.ai_assist_role)
            setPadding(dp(4), dp(6), dp(4), dp(4))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val roleRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val roleTabs = mutableListOf<TextView>()

        fun updateRoleUi() {
            roleTabs.forEach { tv ->
                val selected = tv.tag == aiRole
                tv.alpha = if (selected) 1f else 0.7f
            }
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
                setOnTouchListener { v, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) v.performClick()
                    true
                }
            }
            roleTabs.add(tab)
            roleRow.addView(tab)
        }
        updateRoleUi()

        addView(
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(roleRow)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        val actionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        fun addAction(labelRes: Int, mode: AiTextService.AssistMode) {
            actionRow.addView(TextView(context).apply {
                text = context.getString(labelRes)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener { runAction(mode) }
                setOnTouchListener { v, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) v.performClick()
                    true
                }
            })
        }
        addAction(R.string.ai_assist_mode_continue, AiTextService.AssistMode.Continue)
        addAction(R.string.ai_assist_mode_polish, AiTextService.AssistMode.Polish)
        addAction(R.string.ai_assist_mode_expand, AiTextService.AssistMode.Expand)
        addAction(R.string.ai_assist_mode_formal, AiTextService.AssistMode.Formal)
        addView(actionRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(inputBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(editorPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        editorInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                counter.text = "$length/1000"
            }
        })
        counter.text = "0/1000"
    }

    fun handleAiAssistView() {}

    fun setInitialText(text: String) {
        inputBar.setText(text)
    }

    private fun openEditorPanel() {
        editorInput.setText(inputBar.text?.toString().orEmpty())
        editorInput.setSelection(editorInput.text?.length ?: 0)
        editorPanel.visibility = View.VISIBLE
    }

    private fun closeEditorPanel() {
        editorPanel.visibility = View.GONE
    }

    private fun runAction(mode: AiTextService.AssistMode) {
        val text = inputBar.text.toString().trim()
        if (text.isBlank()) {
            context.toast(R.string.ai_panel_input_empty)
            return
        }
        onRun(text, mode, aiRole)
    }


    fun commitText(text: String) {
        val target = if (editorPanel.visibility == View.VISIBLE) editorInput else inputBar
        target.commitText(text)
    }

    fun sendKeyEvent(keyCode: Int) {
        val target = if (editorPanel.visibility == View.VISIBLE) editorInput else null
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (target != null) {
                    target.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    target.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))
                } else {
                    val old = inputBar.text?.toString().orEmpty()
                    if (old.isNotEmpty()) inputBar.setText(old.dropLast(1))
                }
            }
            KeyEvent.KEYCODE_ENTER -> {
                if (target != null) {
                    inputBar.setText(editorInput.text.toString())
                    closeEditorPanel()
                } else {
                    runAction(AiTextService.AssistMode.Continue)
                }
            }
            else -> {
                val unicodeChar: Char = KeyEvent(KeyEvent.ACTION_DOWN, keyCode).unicodeChar.toChar()
                if (unicodeChar != Character.MIN_VALUE) {
                    if (target != null) target.commitText(unicodeChar.toString())
                    else inputBar.commitText(unicodeChar.toString())
                }
            }
        }
    }

    fun updateTheme(theme: Theme) {
        val keyTextColor = ThemeManager.activeTheme.keyTextColor
        setBackgroundColor(theme.barColor)
        val inputBackground = GradientDrawable().apply {
            setColor(ThemeManager.activeTheme.keyBackgroundColor)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ThemeManager.prefs.keyRadius.getValue().toFloat()
        }
        inputBar.background = inputBackground
        editorInput.background = inputBackground.constantState?.newDrawable()
        editorPanel.background = inputBackground.constantState?.newDrawable()

        inputBar.setTextColor(keyTextColor)
        inputBar.setHintTextColor(keyTextColor)
        editorInput.setTextColor(keyTextColor)
        editorInput.setHintTextColor(keyTextColor)
        counter.setTextColor(keyTextColor)
        editorDone.setTextColor(keyTextColor)

        // 同步角色与动作文字颜色
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is TextView) child.setTextColor(keyTextColor)
        }
    }
}
