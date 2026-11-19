package com.fevtool.editor

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val toolbarLabels = listOf(
        "Import Video",
        "Export Video",
        "Delete",
        "Color And Light",
        "Tracks",
        "Cut Tool",
        "Insert"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(18, 20, 25)
        window.navigationBarColor = Color.rgb(18, 20, 25)
        window.decorView.systemUiVisibility = 0

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(25, 28, 34))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbarScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            isFillViewport = false
            overScrollMode = HorizontalScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(Color.rgb(18, 20, 25))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            )
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }

        toolbarLabels.forEach { label ->
            val button = TextView(this).apply {
                text = label
                textSize = 18f
                maxLines = 2
                includeFontPadding = false
                setTextColor(Color.WHITE)
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    setAutoSizeTextTypeUniformWithConfiguration(
                        10, 18, 1,
                        android.util.TypedValue.COMPLEX_UNIT_SP
                    )
                }
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(2), dp(5), dp(2))
                minWidth = dp(104)
                scaleX = 0.7f
                setBackgroundResource(R.drawable.toolbar_button)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = dpFraction(0.1f)
                }
            }
            toolbar.addView(button)
        }

        toolbarScroll.addView(
            toolbar,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        page.addView(toolbarScroll)

        val workspace = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(25, 28, 34))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val title = TextView(this).apply {
            text = "FEVTool"
            textSize = 26f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.rgb(225, 228, 235))
            gravity = Gravity.CENTER
        }
        workspace.addView(title)
        page.addView(workspace)

        setContentView(page)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun dpFraction(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
