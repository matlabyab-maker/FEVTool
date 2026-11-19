package com.fevtool.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

class MainActivity : Activity() {

    private val toolbarLabels = listOf(
        "Import Video", "Export Video", "Delete", "Color And Light",
        "Tracks", "Cut Tool", "Insert"
    )

    private val importedFiles = linkedMapOf<String, ImportedFile>()
    private lateinit var importedFilesList: LinearLayout
    private lateinit var importedFilesSummary: TextView
    private val importRequestCode = 4102
    private val saveAsRequestCode = 4103
    private val selectedFiles = linkedSetOf<String>()
    private var gridColumns = 5
    private var storyboardInterval = "00:05"
    // The storyboard always retains its original 180 permanent frame slots.
    // Extra slots are temporary and exist only while a storyboard-generation task is active.
    private val permanentStoryboardFrameCount = 180
    private var temporaryStoryboardFrameCount = 0
    private var storyboardGenerationInProgress = false
    private var pendingSaveJson: String? = null

    private data class ImportedFile(
        val uri: Uri, val name: String, val mimeType: String, val tag: String = ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 20, 25)
        window.navigationBarColor = Color.rgb(18, 20, 25)
        window.decorView.systemUiVisibility = 0

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(25, 28, 34))
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        val toolbarScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            isFillViewport = false
            overScrollMode = HorizontalScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(Color.rgb(18, 20, 25))
            layoutParams = LinearLayout.LayoutParams(-1, dp(46))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        toolbarLabels.forEach { label ->
            val button = TextView(this).apply {
                text = label
                textSize = if (label.none { it == ' ' }) 20f else 18f
                maxLines = 2
                includeFontPadding = false
                setTextColor(Color.WHITE)
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    setAutoSizeTextTypeUniformWithConfiguration(
                        10, if (label.none { it == ' ' }) 20 else 18, 1,
                        android.util.TypedValue.COMPLEX_UNIT_SP
                    )
                }
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                // The horizontal gap is reduced to roughly one quarter of the former spacing.
                setPadding(dp(1), dp(2), dp(1), dp(2))
                minWidth = dp(104)
                setBackgroundResource(R.drawable.toolbar_button)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { marginEnd = dp(1) }
                setOnClickListener {
                    when (label) {
                        "Import Video" -> openMultiFilePicker()
                        "Export Video" -> showExportTools()
                        "Insert" -> showInsertTools()
                        "Tracks" -> showTrackTools()
                        "Color And Light" -> showColorAndLightTools()
                    }
                }
            }
            toolbar.addView(button)
        }

        toolbarScroll.addView(toolbar, ViewGroup.LayoutParams(-2, -1))
        page.addView(toolbarScroll)

        val workspace = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 29, 57))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        // Compact preview/player area: no outer margins or decorative blank padding.
        val preview = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(17, 34, 67))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 0.43f)
        }
        val title = TextView(this).apply {
            text = "Player"
            textSize = 54f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Color.rgb(250, 250, 247))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        preview.addView(title, android.widget.FrameLayout.LayoutParams(-1, -1))

        // Thin outlined cells around the preview reproduce the compact tiled frame in the reference.
        val topTiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(7) {
            val tile = android.view.View(this).apply { background = tileBorderDrawable() }
            topTiles.addView(tile, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                if (it < 6) marginEnd = dp(1)
            })
        }
        preview.addView(topTiles, android.widget.FrameLayout.LayoutParams(-1, dp(40), Gravity.TOP))
        fun addSideTiles(gravity: Int) {
            val side = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                repeat(4) { i ->
                    addView(android.view.View(this@MainActivity).apply { background = tileBorderDrawable() },
                        LinearLayout.LayoutParams(-1, 0, 1f).apply { if (i < 3) bottomMargin = dp(1) })
                }
            }
            preview.addView(side, android.widget.FrameLayout.LayoutParams(dp(58), -1, gravity).apply {
                topMargin = dp(41)
                bottomMargin = dp(18)
                if (gravity == Gravity.START) marginStart = dp(1) else marginEnd = dp(1)
            })
        }
        addSideTiles(Gravity.START)
        addSideTiles(Gravity.END)
        importedFilesSummary = TextView(this).apply {
            text = "0 clips"
            textSize = 11f
            setTextColor(Color.rgb(214, 222, 237))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(5), dp(2))
        }
        preview.addView(importedFilesSummary, android.widget.FrameLayout.LayoutParams(-1, dp(18), Gravity.BOTTOM))
        workspace.addView(preview)

        // One tightly packed control strip, matching the reference layout.
        val controlScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.rgb(10, 19, 42))
            layoutParams = LinearLayout.LayoutParams(-1, dp(44))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(2), dp(3), dp(2))
        }
        fun controlButton(label: String, action: () -> Unit) {
            val b = TextView(this).apply {
                text = label
                textSize = 14f
                maxLines = 1
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(24, 27, 34))
                setPadding(dp(7), dp(5), dp(7), dp(5))
                setBackgroundResource(R.drawable.toolbar_button)
                isClickable = true
                setOnClickListener { action() }
            }
            controls.addView(b, LinearLayout.LayoutParams(-2, -1).apply { marginEnd = dp(3) })
        }
        controlButton("UP") { selectedFiles.clear(); refreshImportedFilesList() }
        controlButton("ALL VIEW") { refreshImportedFilesList() }
        controls.addView(TextView(this).apply {
            text = "Columns"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(3), 0, dp(2), 0)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(-2, -1))
        val columnsSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                (1..15).map { it.toString() }
            )
            setSelection(gridColumns - 1)
            setBackgroundColor(Color.rgb(255, 211, 36))
            setPadding(dp(2), 0, dp(2), 0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    gridColumns = position + 1
                    refreshImportedFilesList()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        controls.addView(columnsSpinner, LinearLayout.LayoutParams(dp(62), -1).apply {
            marginEnd = dp(5)
        })
        controls.addView(TextView(this).apply {
            text = "Interval"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, LinearLayout.LayoutParams(-2, -1).apply { marginEnd = dp(2) })
        val intervalInput = EditText(this).apply {
            setText(storyboardInterval)
            textSize = 13f
            singleLine = true
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(3), 0, dp(3), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(23, 39, 68))
                setStroke(dp(1), Color.rgb(170, 151, 73))
            }
            hint = "mm:ss"
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    storyboardInterval = s?.toString().orEmpty()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        controls.addView(intervalInput, LinearLayout.LayoutParams(dp(62), -1).apply {
            marginEnd = dp(5)
        })
        controlButton("Select All") {
            selectedFiles.clear()
            selectedFiles.addAll(importedFiles.keys)
            refreshImportedFilesList()
        }
        controlButton("Un Select") { selectedFiles.clear(); refreshImportedFilesList() }
        controlButton("□ Frame") { }
        controlScroll.addView(controls, ViewGroup.LayoutParams(-2, -1))
        workspace.addView(controlScroll)

        val importedScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(Color.rgb(16, 31, 59))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 0.57f)
        }
        importedFilesList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(1), dp(2), dp(1))
        }
        importedScroll.addView(importedFilesList, ViewGroup.LayoutParams(-1, -2))
        workspace.addView(importedScroll)
        page.addView(workspace)
        setContentView(page)
        refreshImportedFilesList()
    }


    private fun openMultiFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // */* lets the user mix video, audio, image, subtitle and other file formats.
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, importRequestCode)
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Cannot open file picker")
                .setMessage(e.localizedMessage ?: "No compatible file picker is available.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    @Deprecated("Handled for compatibility with the current project without adding dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == saveAsRequestCode) {
            if (resultCode == RESULT_OK && data?.data != null) {
                try {
                    contentResolver.openOutputStream(data.data!!)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(pendingSaveJson ?: "{}") }
                    }
                    AlertDialog.Builder(this).setMessage("Project file saved successfully.")
                        .setPositiveButton("OK", null).show()
                } catch (e: Exception) {
                    AlertDialog.Builder(this).setTitle("Save failed")
                        .setMessage(e.localizedMessage ?: "Unable to write project file.")
                        .setPositiveButton("OK", null).show()
                }
            }
            pendingSaveJson = null
            return
        }
        if (requestCode != importRequestCode || resultCode != RESULT_OK || data == null) return

        val selected = linkedSetOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(selected::add)
        }
        data.data?.let(selected::add)

        selected.forEach { uri ->
            try {
                val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Some document providers grant a temporary read permission only.
            } catch (_: IllegalArgumentException) {
                // The provider may not support persistable permissions.
            }
            val name = queryDisplayName(uri) ?: "Imported file"
            val mime = contentResolver.getType(uri) ?: "Unknown format"
            importedFiles[uri.toString()] = ImportedFile(uri, name, mime)
        }
        refreshImportedFilesList()
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    /**
     * Reserve enough storyboard cells for the requested number of frames.
     * The first 180 cells are permanent; any cells beyond 180 are temporary.
     * Call this when the video/storyboard engine determines its required frame count.
     */
    private fun beginStoryboardFrameGeneration(requiredFrameCount: Int) {
        storyboardGenerationInProgress = true
        temporaryStoryboardFrameCount =
            (requiredFrameCount.coerceAtLeast(permanentStoryboardFrameCount) - permanentStoryboardFrameCount)
        refreshImportedFilesList()
    }

    /**
     * Call on successful completion, cancellation, or failure of storyboard generation.
     * This removes all overflow cells and restores exactly the original 180-slot storyboard.
     */
    private fun finishStoryboardFrameGeneration() {
        storyboardGenerationInProgress = false
        temporaryStoryboardFrameCount = 0
        refreshImportedFilesList()
    }

    private fun refreshImportedFilesList() {
        importedFilesSummary.text = "${importedFiles.size} clips"
        importedFilesList.removeAllViews()
        // Keep 180 permanent storyboard slots at every column setting.
        // Temporary overflow slots are appended only during storyboard generation.
        val storyboardSlots = permanentStoryboardFrameCount +
            if (storyboardGenerationInProgress) temporaryStoryboardFrameCount else 0
        val count = maxOf(storyboardSlots, importedFiles.size)
        val rows = (count + gridColumns - 1) / gridColumns
        for (rowIndex in 0 until rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (column in 0 until gridColumns) {
                val index = rowIndex * gridColumns + column
                if (index >= count) break
                val file = importedFiles.values.elementAtOrNull(index)
                val key = file?.uri?.toString()
                val selected = key != null && key in selectedFiles
                val cell = android.widget.FrameLayout(this).apply {
                    setBackground(android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (selected) Color.rgb(47, 72, 111) else Color.rgb(19, 39, 72))
                        setStroke(dp(1), Color.rgb(69, 96, 137))
                    })
                    isClickable = true
                    if (key != null) setOnClickListener {
                        if (!selectedFiles.add(key)) selectedFiles.remove(key)
                        refreshImportedFilesList()
                    }
                }
                val play = TextView(this).apply {
                    text = if (file == null) "" else "▶"
                    textSize = 29f
                    setTextColor(Color.rgb(133, 166, 211))
                    gravity = Gravity.CENTER
                    if (file != null) typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                cell.addView(play, android.widget.FrameLayout.LayoutParams(-1, -1))
                val number = TextView(this).apply {
                    text = if (file == null) "${index + 1}" else "${index + 1}  ${file.name}"
                    textSize = if (file == null) 10f else 9f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(Color.WHITE)
                    gravity = Gravity.BOTTOM or Gravity.START
                    setPadding(dp(2), 0, dp(2), dp(2))
                }
                cell.addView(number, android.widget.FrameLayout.LayoutParams(-1, dp(19), Gravity.BOTTOM))
                row.addView(cell, LinearLayout.LayoutParams(0, dp(86), 1f).apply {
                    if (column < gridColumns - 1) marginEnd = dp(1)
                    bottomMargin = dp(1)
                })
            }
            importedFilesList.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun showExportTools() {
        showToolMenu("Export Video", listOf("Tag", "Rename", "Save", "Save As", "Split Selected")) { tool ->
            when (tool) {
                "Tag" -> showTagSettings()
                "Rename" -> showRenameSettings()
                "Save" -> saveProjectLocally()
                "Save As" -> showSaveAsSettings()
                "Split Selected" -> showSplitSettings()
            }
        }
    }

    private fun selectedEntries(): List<Pair<String, ImportedFile>> =
        importedFiles.entries.filter { it.key in selectedFiles }.map { it.key to it.value }

    private fun requireSelection(action: String): List<Pair<String, ImportedFile>>? {
        val entries = selectedEntries()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle(action)
                .setMessage("Select one or more imported files first. Tap a file in the list to select it.")
                .setPositiveButton("OK", null).show()
            return null
        }
        return entries
    }

    private fun showTagSettings() {
        val entries = requireSelection("Tag") ?: return
        val input = EditText(this).apply {
            hint = "Enter tag (e.g. Intro, Music, Scene 1)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        AlertDialog.Builder(this).setTitle("Tag Settings")
            .setMessage("Apply this tag to ${entries.size} selected file(s).")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val tag = input.text.toString().trim()
                entries.forEach { (key, file) -> importedFiles[key] = file.copy(tag = tag) }
                refreshImportedFilesList()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showRenameSettings() {
        val entries = requireSelection("Rename") ?: return
        val input = EditText(this).apply {
            hint = if (entries.size == 1) "New file name" else "Base name for selected files"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setText(entries.first().second.name.substringBeforeLast('.', entries.first().second.name))
        }
        AlertDialog.Builder(this).setTitle("Rename Settings")
            .setMessage(if (entries.size == 1) "Rename the selected item. Its original file is not modified." else "Each selected item receives this name plus an index. File extensions are preserved.")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val base = input.text.toString().trim()
                if (base.isNotBlank()) {
                    entries.forEachIndexed { index, (key, file) ->
                        val ext = file.name.substringAfterLast('.', "").let { if (it != file.name && it.isNotBlank()) ".$it" else "" }
                        val newName = if (entries.size == 1) "$base$ext" else "$base-${index + 1}$ext"
                        importedFiles[key] = file.copy(name = newName)
                    }
                    refreshImportedFilesList()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun projectJson(): String {
        val root = JSONObject()
        root.put("project", "FEVTool")
        root.put("version", 1)
        root.put("storyboardColumns", gridColumns)
        root.put("storyboardInterval", storyboardInterval)
        val files = JSONArray()
        importedFiles.values.forEach { file ->
            files.put(JSONObject().apply {
                put("uri", file.uri.toString())
                put("name", file.name)
                put("mimeType", file.mimeType)
                put("tag", file.tag)
            })
        }
        root.put("files", files)
        return root.toString(2)
    }

    private fun saveProjectLocally() {
        try {
            openFileOutput("fevtool-project.json", MODE_PRIVATE).use {
                it.write(projectJson().toByteArray(Charsets.UTF_8))
            }
            AlertDialog.Builder(this).setTitle("Save")
                .setMessage("Project information saved in the app's private storage. Source media files are not copied or rendered.")
                .setPositiveButton("OK", null).show()
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("Save failed")
                .setMessage(e.localizedMessage ?: "Unable to save project.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun showSaveAsSettings() {
        val box = settingsBox()
        val name = EditText(this).apply {
            hint = "Project file name"
            setText("FEVTool-project.json")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        box.addView(name, fullWidth())
        box.addView(TextView(this).apply {
            text = "Save As exports the project list, names, tags and document references as a JSON project file. It does not render a video yet."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, 0)
        })
        AlertDialog.Builder(this).setTitle("Save As Settings").setView(box)
            .setPositiveButton("Choose location") { _, _ ->
                val fileName = name.text.toString().trim().ifBlank { "FEVTool-project.json" }
                pendingSaveJson = projectJson()
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, if (fileName.endsWith(".json", true)) fileName else "$fileName.json")
                }
                startActivityForResult(intent, saveAsRequestCode)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showSplitSettings() {
        val entries = requireSelection("Split Selected") ?: return
        val box = settingsBox()
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Split by duration", "Split into equal parts", "Split at marked points"))
        }
        val duration = EditText(this).apply {
            hint = "Segment duration in seconds (e.g. 30)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("30")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        val count = EditText(this).apply {
            hint = "Number of equal parts (e.g. 2)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("2")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        box.addView(TextView(this).apply { text = "${entries.size} selected file(s)"; setTextColor(Color.WHITE); textSize = 15f })
        box.addView(mode, fullWidth())
        box.addView(duration, fullWidth())
        box.addView(count, fullWidth())
        AlertDialog.Builder(this).setTitle("Split Selected Settings").setView(box)
            .setPositiveButton("Apply") { _, _ ->
                val message = when (mode.selectedItemPosition) {
                    0 -> "Split plan: every ${duration.text} second(s)."
                    1 -> "Split plan: ${count.text} equal part(s) per selected file."
                    else -> "Split plan: split at timeline markers."
                }
                AlertDialog.Builder(this).setTitle("Split settings saved")
                    .setMessage("$message\nSelected files: ${entries.size}\nActual media splitting requires the video-processing engine, which is not connected yet.")
                    .setPositiveButton("OK", null).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showColorAndLightTools() {
        showToolMenu("Color And Light", listOf("Color , A", "Light")) { tool ->
            when (tool) {
                "Color , A" -> showColorSettings()
                "Light" -> showLightSettings()
            }
        }
    }

    // Color controls are kept together in their own dedicated settings panel.
    private fun showColorSettings() {
        showMultiSliderSettings(
            "Color , A Settings",
            listOf(
                Triple("Saturation", 0, 200) to 100,
                Triple("Hue", -180, 180) to 0,
                Triple("Temperature", -100, 100) to 0,
                Triple("Tint", -100, 100) to 0
            )
        )
    }

    // Light controls are independent from color controls.
    private fun showLightSettings() {
        showMultiSliderSettings(
            "Light Settings",
            listOf(
                Triple("Brightness", -100, 100) to 0,
                Triple("Exposure", -100, 100) to 0,
                Triple("Contrast", 0, 200) to 100,
                Triple("Highlights", -100, 100) to 0,
                Triple("Shadows", -100, 100) to 0
            )
        )
    }

    private fun showMultiSliderSettings(
        title: String,
        settings: List<Pair<Triple<String, Int, Int>, Int>>
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        settings.forEach { (definition, initial) ->
            val (label, min, max) = definition
            val value = TextView(this).apply {
                text = "$label: $initial"
                textSize = 15f
                setTextColor(Color.WHITE)
                includeFontPadding = false
            }
            val seek = SeekBar(this).apply {
                this.max = max - min
                progress = initial - min
                contentDescription = label
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "$label: ${progress + min}"
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
            content.addView(value, LinearLayout.LayoutParams(-1, dp(25)))
            content.addView(seek, LinearLayout.LayoutParams(-1, dp(38)))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Apply", null)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInsertTools() {
        showToolMenu("Insert", listOf("Blur", "Sharpen", "Noise")) { tool ->
            when (tool) {
                "Blur" -> showSliderSettings("Blur Settings", "Blur radius", 1, 30, 8)
                "Sharpen" -> showSliderSettings("Sharpen Settings", "Sharpen strength", 0, 100, 35)
                "Noise" -> showSliderSettings("Noise Settings", "Noise intensity", 0, 100, 15)
            }
        }
    }

    private fun showTrackTools() {
        showToolMenu("Tracks", listOf("Text", "Voice", "Subtitle")) { tool ->
            when (tool) {
                "Text" -> showTextSettings()
                "Voice" -> showVoiceSettings()
                "Subtitle" -> showSubtitleSettings()
            }
        }
    }

    private fun showToolMenu(title: String, tools: List<String>, onSelected: (String) -> Unit) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        tools.forEach { name ->
            val item = TextView(this).apply {
                text = name
                textSize = 19f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundResource(R.drawable.toolbar_button)
                isClickable = true
                setOnClickListener {
                    menuDialog?.dismiss()
                    onSelected(name)
                }
            }
            content.addView(item, LinearLayout.LayoutParams(-1, dp(52)).apply {
                bottomMargin = dp(7)
            })
        }
        menuDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setNegativeButton("Close", null)
            .create()
        menuDialog?.show()
    }

    private var menuDialog: AlertDialog? = null

    private fun showSliderSettings(title: String, label: String, min: Int, max: Int, initial: Int) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(4))
        }
        val valueText = TextView(this).apply {
            text = "$label: $initial"
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        val slider = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueText.text = "$label: ${progress + min}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        box.addView(valueText)
        box.addView(slider)
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("Apply", null).setNegativeButton("Cancel", null).show()
    }

    private fun showTextSettings() {
        val box = settingsBox()
        val textInput = EditText(this).apply { hint = "Enter text"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
        box.addView(textInput, fullWidth())
        addSettingSlider(box, "Text size", 8, 96, 32)
        addSettingSlider(box, "Opacity", 0, 100, 100)
        addSettingSlider(box, "Outline", 0, 10, 1)
        AlertDialog.Builder(this).setTitle("Text Track Settings").setView(box)
            .setPositiveButton("Apply", null).setNegativeButton("Cancel", null).show()
    }

    private fun showVoiceSettings() {
        val box = settingsBox()
        addSettingSlider(box, "Volume", 0, 200, 100)
        addSettingSlider(box, "Fade in", 0, 10, 0)
        addSettingSlider(box, "Fade out", 0, 10, 0)
        AlertDialog.Builder(this).setTitle("Voice Track Settings").setView(box)
            .setPositiveButton("Apply", null).setNegativeButton("Cancel", null).show()
    }

    private fun showSubtitleSettings() {
        val box = settingsBox()
        val language = EditText(this).apply {
            hint = "Subtitle language / label"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        box.addView(language, fullWidth())
        addSettingSlider(box, "Subtitle size", 8, 72, 26)
        addSettingSlider(box, "Background opacity", 0, 100, 55)
        addSettingSlider(box, "Vertical position", 0, 100, 85)
        AlertDialog.Builder(this).setTitle("Subtitle Track Settings").setView(box)
            .setPositiveButton("Apply", null).setNegativeButton("Cancel", null).show()
    }

    private fun settingsBox() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(8), dp(22), dp(4))
    }

    private fun addSettingSlider(box: LinearLayout, label: String, min: Int, max: Int, initial: Int) {
        val value = TextView(this).apply {
            text = "$label: $initial"
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        val slider = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                value.text = "$label: ${progress + min}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        box.addView(value)
        box.addView(slider)
    }

    private fun fullWidth() = LinearLayout.LayoutParams(-1, dp(52))

    private fun tileBorderDrawable() = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.rgb(19, 37, 70))
        setStroke(dp(1), Color.rgb(61, 87, 128))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
