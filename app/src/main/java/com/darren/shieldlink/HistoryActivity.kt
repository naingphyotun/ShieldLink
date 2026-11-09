package com.darren.shieldlink

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.surface, theme))
        }
        val toolbar = Toolbar(this).apply {
            title = "History"
            setBackgroundColor(resources.getColor(R.color.purple_primary, theme))
            setTitleTextColor(resources.getColor(R.color.white, theme))
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        root.addView(toolbar)

        val scroll = ScrollView(this)
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val prefs = getSharedPreferences("shieldlink_history", MODE_PRIVATE)
        val entries = prefs.getStringSet("link_history", emptySet()) ?: emptySet()

        if (entries.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No links checked yet."
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                setPadding(0, 40, 0, 0)
            })
        } else {
            val sorted = entries.sortedByDescending { it.substringBefore("|").toLongOrNull() ?: 0L }
            for (entry in sorted) {
                val parts = entry.split("|", limit = 3)
                if (parts.size < 3) continue
                val (timestampStr, verdict, url) = parts
                val timestamp = timestampStr.toLongOrNull() ?: continue

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 24, 24, 24)
                }
                row.addView(TextView(this).apply {
                    text = "⚠ Blocked"
                    setTextColor(resources.getColor(R.color.status_warning, theme))
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                row.addView(TextView(this).apply {
                    text = url
                    textSize = 13f
                    setTextColor(resources.getColor(R.color.text_primary, theme))
                })
                row.addView(TextView(this).apply {
                    text = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
                    textSize = 11f
                    setTextColor(resources.getColor(R.color.text_tertiary, theme))
                })
                listContainer.addView(row)

                val divider = android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 2
                    )
                    setBackgroundColor(resources.getColor(R.color.divider, theme))
                }
                listContainer.addView(divider)
            }
        }

        scroll.addView(listContainer)
        root.addView(scroll)
        setContentView(root)
        root.fitsSystemWindows = true
    }
}