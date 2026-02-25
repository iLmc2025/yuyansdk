package com.yuyan.imemodule.keyboard.container

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.yuyan.imemodule.R
import com.yuyan.imemodule.adapter.ClipBoardAdapter
import com.yuyan.imemodule.ai.AiTextService
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.data.theme.ThemeManager.activeTheme
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.database.entry.Clipboard
import com.yuyan.imemodule.libs.recyclerview.SwipeMenu
import com.yuyan.imemodule.libs.recyclerview.SwipeMenuBridge
import com.yuyan.imemodule.libs.recyclerview.SwipeMenuItem
import com.yuyan.imemodule.libs.recyclerview.SwipeRecyclerView
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.prefs.behavior.ClipboardLayoutMode
import com.yuyan.imemodule.prefs.behavior.AiAssistRole
import com.yuyan.imemodule.prefs.behavior.PopupMenuMode
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.utils.toast
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.imemodule.manager.layout.CustomGridLayoutManager
import com.yuyan.imemodule.singleton.EnvironmentSingleton.Companion.instance
import splitties.dimensions.dp
import splitties.views.textResource
import kotlin.math.ceil

/**
 * 粘贴板列表键盘容器
 *
 * 使用RecyclerView实现垂直ListView列表布局。
 */
@SuppressLint("ViewConstructor")
class ClipBoardContainer(context: Context, inputView: InputView) : BaseContainer(context, inputView) {
    private val mPaint : Paint = Paint() // 测量字符串长度
    private val mRVSymbolsView: SwipeRecyclerView = SwipeRecyclerView(context)
    private var mTVLable: TextView? = null
    private var itemMode:SkbMenuMode? = null
    private var aiRole: AiAssistRole = AppPrefs.getInstance().input.aiAssistRole.getValue()

    init {
        mPaint.textSize = dp(22f)
        initView(context)
    }

    private fun initView(context: Context) {
        mTVLable = TextView(context).apply {
            textResource = R.string.clipboard_empty_ltip
            gravity = Gravity.CENTER
            setTextColor(activeTheme.keyTextColor)
            textSize = instance.candidateTextSize
        }
        mRVSymbolsView.setItemAnimator(null)
        val layoutParams2 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        mRVSymbolsView.layoutParams = layoutParams2
        this.addView(mRVSymbolsView)
    }

    /**
     * 显示候选词界面 , 点击候选词时执行
     */
    fun showClipBoardView(item: SkbMenuMode) {
        CustomConstant.lockClipBoardEnable = false
        itemMode = item

        if(itemMode == SkbMenuMode.AiAssist) {
            showAiAssistPanel()
            return
        }

        val aiPanelParent = mAiAssistPanel?.parent
        if (aiPanelParent != null) {
            (aiPanelParent as ViewGroup).removeView(mAiAssistPanel)
        }

        mRVSymbolsView.visibility = VISIBLE
        mRVSymbolsView.setHasFixedSize(true)
        val copyContents : MutableList<Clipboard> = when(itemMode) {
            SkbMenuMode.ClipBoard -> DataBaseKT.instance.clipboardDao().getAll().toMutableList()
            SkbMenuMode.Phrases -> DataBaseKT.instance.phraseDao().getAll().map { line -> Clipboard(line.content) }.toMutableList()
            else -> mutableListOf()
        }
        val manager =  when (AppPrefs.getInstance().clipboard.clipboardLayoutCompact.getValue()){
            ClipboardLayoutMode.ListView ->  LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            ClipboardLayoutMode.GridView -> CustomGridLayoutManager(context, 2)
            ClipboardLayoutMode.FlexboxView -> {
                calculateColumn(copyContents)
                CustomGridLayoutManager(context, 6).apply {
                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(i: Int) = mHashMapSymbols[i] ?: 1
                    }
                }
            }
        }
        mRVSymbolsView.setLayoutManager(manager)
        val viewParent = mTVLable?.parent
        if (viewParent != null) {
            (viewParent as ViewGroup).removeView(mTVLable)
        }
        if(copyContents.isEmpty()){
            this.addView(mTVLable, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        val adapter = ClipBoardAdapter(context, copyContents)
        mRVSymbolsView.setAdapter(null)
        mRVSymbolsView.setOnItemClickListener{ _: View?, position: Int ->
            inputView.responseLongKeyEvent(Pair(PopupMenuMode.Text, copyContents[position].content))
            if(!CustomConstant.lockClipBoardEnable)KeyboardManager.instance.switchKeyboard()
        }
        mRVSymbolsView.setSwipeMenuCreator{ _: SwipeMenu, rightMenu: SwipeMenu, position: Int ->
            val topItem = SwipeMenuItem(mContext).apply {
                setImage(if(itemMode == SkbMenuMode.ClipBoard) {
                    if(copyContents[position].isKeep == 1)R.drawable.ic_baseline_untop_circle_32 else R.drawable.ic_baseline_top_circle_32 }
                else R.drawable.ic_menu_edit)
                image.setTint(activeTheme.keyTextColor)
            }
            rightMenu.addMenuItem(topItem)
            val deleteItem = SwipeMenuItem(mContext).apply {
                setImage(R.drawable.ic_menu_delete)
                image.setTint(activeTheme.keyTextColor)
            }
            rightMenu.addMenuItem(deleteItem)
        }
        mRVSymbolsView.setOnItemMenuClickListener { menuBridge: SwipeMenuBridge, position: Int ->
            menuBridge.closeMenu()
            if(itemMode == SkbMenuMode.ClipBoard){
                if(menuBridge.position == 0) {
                    val data: Clipboard = copyContents[position]
                    data.isKeep = 1 - data.isKeep
                    DataBaseKT.instance.clipboardDao().update(data)
                    showClipBoardView(SkbMenuMode.ClipBoard)
                } else if(menuBridge.position == 1){
                    val data: Clipboard = copyContents.removeAt(position)
                    DataBaseKT.instance.clipboardDao().deleteByContent(data.content)
                    mRVSymbolsView.adapter?.notifyItemRemoved(position)
                }
            } else {
                val content = copyContents[position].content
                if(menuBridge.position == 0) {
                    inputView.onSettingsMenuClick(SkbMenuMode.AddPhrases, DataBaseKT.instance.phraseDao().queryByContent(content))
                } else if(menuBridge.position == 1){
                    DataBaseKT.instance.phraseDao().deleteByContent(content)
                    showClipBoardView(SkbMenuMode.Phrases)
                }
            }
        }
        mRVSymbolsView.setAdapter(adapter)
    }

    private val mHashMapSymbols = HashMap<Int, Int>() //候选词索引列数对应表
    private fun calculateColumn(data : MutableList<Clipboard>) {
        mHashMapSymbols.clear()
        val itemWidth = instance.skbWidth/6 - dp(10)
        var mCurrentColumn = 0
        val contents = data.map { it.content }
        contents.forEachIndexed { position, candidate ->
            var count = getSymbolsCount(candidate, itemWidth)
            var nextCount = 0
            if (contents.size > position + 1) {
                val nextCandidate = contents[position + 1]
                nextCount = getSymbolsCount(nextCandidate, itemWidth)
            }
            mCurrentColumn = if (mCurrentColumn + count + nextCount > 6) {
                count = 6 - mCurrentColumn
                0
            } else  (mCurrentColumn + count) % 6
            mHashMapSymbols[position] = count
        }
    }

    private fun getSymbolsCount(data: String, itemWidth:Int): Int {
        return if (!TextUtils.isEmpty(data)) ceil(mPaint.measureText(data).div(itemWidth)).toInt() else 0
    }
    fun getMenuMode():SkbMenuMode? {
       return itemMode
    }


    private var mAiAssistPanel: LinearLayout? = null

    private fun showAiAssistPanel() {
        mRVSymbolsView.visibility = GONE
        val panelParent = mAiAssistPanel?.parent
        if (panelParent != null) {
            (panelParent as ViewGroup).removeView(mAiAssistPanel)
        }

        val input = EditText(context).apply {
            hint = context.getString(R.string.ai_panel_input_hint)
            setText(getLatestClipboardText().orEmpty())
            setTextColor(activeTheme.keyTextColor)
            setHintTextColor(activeTheme.keyTextColor)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(activeTheme.keyBackgroundColor)
        }

        val roleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val roleTabs = mutableListOf<TextView>()
        fun updateRoleUi() {
            roleTabs.forEach { tab ->
                val selected = tab.tag == aiRole
                tab.alpha = if (selected) 1f else 0.7f
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
                setTextColor(activeTheme.keyTextColor)
                setOnClickListener {
                    aiRole = role
                    updateRoleUi()
                }
            }
            roleTabs.add(tab)
            roleContainer.addView(tab)
        }
        updateRoleUi()

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun addAction(labelRes: Int, mode: AiTextService.AssistMode) {
            actionRow.addView(TextView(context).apply {
                text = context.getString(labelRes)
                setTextColor(activeTheme.keyTextColor)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener {
                    val text = input.text?.toString()?.trim().orEmpty()
                    if (text.isBlank()) {
                        context.toast(R.string.ai_panel_input_empty)
                    } else {
                        inputView.triggerAiAssist(text, mode, aiRole)
                    }
                }
            })
        }

        addAction(R.string.ai_assist_mode_continue, AiTextService.AssistMode.Continue)
        addAction(R.string.ai_assist_mode_polish, AiTextService.AssistMode.Polish)
        addAction(R.string.ai_assist_mode_expand, AiTextService.AssistMode.Expand)
        addAction(R.string.ai_assist_mode_formal, AiTextService.AssistMode.Formal)

        mAiAssistPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = context.getString(R.string.ai_assist_role)
                setTextColor(activeTheme.keyTextColor)
                setPadding(dp(4), dp(8), dp(4), dp(4))
            })
            addView(HorizontalScrollView(context).apply { addView(roleContainer) }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(actionRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(mAiAssistPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun getLatestClipboardText(): String? {
        return DataBaseKT.instance.clipboardDao().getAll().firstOrNull()?.content?.takeIf { it.isNotBlank() }
    }
}
