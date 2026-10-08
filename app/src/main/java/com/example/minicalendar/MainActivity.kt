package com.example.minicalendar

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class MainActivity : Activity() {

    private data class Event(val id: String, val date: LocalDate, val title: String)

    private val dark = Color.parseColor("#4A4A4F")
    private val textDark = Color.parseColor("#333333")
    private val textWeekend = Color.parseColor("#8E8E93")
    private val accent = Color.parseColor("#3478F6")
    private val inactive = Color.parseColor("#8E8E93")
    private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    private var month: YearMonth = YearMonth.now()
    private var selected: LocalDate = LocalDate.now()
    private var events = mutableListOf<Event>()

    private lateinit var homePanel: View
    private lateinit var eventPanel: View
    private lateinit var settingsPanel: View
    private lateinit var monthTitle: TextView
    private lateinit var todayNumber: TextView
    private lateinit var grid: LinearLayout
    private lateinit var infoText: TextView
    private lateinit var weekText: TextView
    private lateinit var selectedEvents: LinearLayout
    private lateinit var eventList: LinearLayout
    private var currentTab = 0
    private var lastRenderedDay: LocalDate = LocalDate.now()

    private lateinit var swipeDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        homePanel = findViewById(R.id.homePanel)
        eventPanel = findViewById(R.id.eventPanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        monthTitle = findViewById(R.id.monthTitle)
        todayNumber = findViewById(R.id.todayNumber)
        grid = findViewById(R.id.grid)
        infoText = findViewById(R.id.infoText)
        weekText = findViewById(R.id.weekText)
        selectedEvents = findViewById(R.id.selectedEvents)
        eventList = findViewById(R.id.eventList)

        loadEvents()

        findViewById<View>(R.id.btnToday).setOnClickListener { goToToday() }
        findViewById<View>(R.id.btnAdd).setOnClickListener { showAddDialog() }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.tabHome).setOnClickListener { setTab(0) }
        findViewById<View>(R.id.tabEvent).setOnClickListener { setTab(1) }
        findViewById<View>(R.id.tabSettings).setOnClickListener { setTab(2) }

        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null || currentTab != 0) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > dp(80) && abs(dx) > abs(dy) * 1.5f && abs(velocityX) > 400) {
                    changeMonth(if (dx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })

        setTab(0)
        renderAll()
    }

    override fun onResume() {
        super.onResume()
        // If the date rolled over while the app was in the background, refresh "today".
        val now = LocalDate.now()
        if (now != lastRenderedDay) {
            month = YearMonth.from(now)
            selected = now
        }
        renderAll()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::swipeDetector.isInitialized) swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------- navigation ----------

    private fun setTab(index: Int) {
        currentTab = index
        homePanel.visibility = if (index == 0) View.VISIBLE else View.GONE
        eventPanel.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsPanel.visibility = if (index == 2) View.VISIBLE else View.GONE

        val icons = listOf(R.id.iconHome, R.id.iconEvent, R.id.iconSettings)
        val labels = listOf(R.id.labelHome, R.id.labelEvent, R.id.labelSettings)
        for (i in 0..2) {
            val color = if (i == index) accent else inactive
            findViewById<ImageView>(icons[i]).setColorFilter(color)
            findViewById<TextView>(labels[i]).setTextColor(color)
        }
        if (index == 1) renderEventList()
    }

    private fun goToToday() {
        val now = LocalDate.now()
        month = YearMonth.from(now)
        selected = now
        setTab(0)
        renderAll()
    }

    private fun changeMonth(delta: Int) {
        month = month.plusMonths(delta.toLong())
        val now = LocalDate.now()
        selected = if (month == YearMonth.from(now)) now else month.atDay(1)
        renderAll()
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Go to today")
        popup.menu.add(0, 2, 1, "Previous month")
        popup.menu.add(0, 3, 2, "Next month")
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> goToToday()
                2 -> changeMonth(-1)
                3 -> changeMonth(1)
            }
            true
        }
        popup.show()
    }

    // ---------- rendering ----------

    private fun renderAll() {
        val today = LocalDate.now()
        lastRenderedDay = today
        todayNumber.text = today.dayOfMonth.toString()
        renderMonth(today)
        renderInfo(today)
        if (currentTab == 1) renderEventList()
    }

    private fun renderMonth(today: LocalDate) {
        monthTitle.text = "${month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${month.year}"
        grid.removeAllViews()

        val offset = month.atDay(1).dayOfWeek.value % 7 // Sunday = 0
        val length = month.lengthOfMonth()
        val rows = (offset + length + 6) / 7
        val eventDates = events.map { it.date }.toSet()

        for (r in 0 until rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(62))
            }
            for (c in 0 until 7) {
                val day = r * 7 + c - offset + 1
                val cell = FrameLayout(this)
                if (day in 1..length) {
                    val date = month.atDay(day)
                    val tv = TextView(this).apply {
                        text = day.toString()
                        textSize = 18f
                        gravity = Gravity.CENTER
                        when {
                            date == selected -> {
                                setBackgroundResource(R.drawable.bg_selected)
                                setTextColor(Color.WHITE)
                            }
                            date == today -> {
                                setBackgroundResource(R.drawable.bg_today)
                                setTextColor(textDark)
                            }
                            else -> setTextColor(if (c == 0 || c == 6) textWeekend else textDark)
                        }
                    }
                    cell.addView(tv, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
                    if (date in eventDates) {
                        val dot = View(this).apply { setBackgroundResource(R.drawable.bg_dot) }
                        cell.addView(
                            dot,
                            FrameLayout.LayoutParams(dp(5), dp(5), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                                .apply { bottomMargin = dp(3) }
                        )
                    }
                    cell.setOnClickListener {
                        selected = date
                        renderAll()
                    }
                }
                row.addView(cell, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            }
            grid.addView(row)
        }
    }

    private fun renderInfo(today: LocalDate) {
        val d = ChronoUnit.DAYS.between(today, selected)
        infoText.text = when {
            d == 0L -> "Today"
            d > 0 -> "In $d day(s)"
            else -> "${-d} day(s) ago"
        }
        weekText.text = "Week ${selected.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

        selectedEvents.removeAllViews()
        events.filter { it.date == selected }.forEach { e ->
            selectedEvents.addView(TextView(this).apply {
                text = "•  ${e.title}"
                textSize = 16f
                setTextColor(textDark)
                setPadding(0, dp(6), 0, dp(6))
                setOnClickListener { confirmDelete(e) }
            })
        }
    }

    private fun renderEventList() {
        eventList.removeAllViews()
        eventList.addView(TextView(this).apply {
            text = "Events"
            textSize = 28f
            setTextColor(Color.parseColor("#1C1C1E"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })
        if (events.isEmpty()) {
            eventList.addView(TextView(this).apply {
                text = "No events yet. Tap + on the Home tab to add one."
                textSize = 16f
                setTextColor(textWeekend)
            })
            return
        }
        events.sortedWith(compareBy({ it.date }, { it.title })).forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { confirmDelete(e) }
            }
            row.addView(TextView(this).apply {
                text = e.title
                textSize = 17f
                setTextColor(textDark)
            })
            row.addView(TextView(this).apply {
                text = e.date.format(dateFmt)
                textSize = 14f
                setTextColor(textWeekend)
            })
            eventList.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    // ---------- events ----------

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = "Event title"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle("New event · ${selected.format(dateFmt)}")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    events.add(Event(UUID.randomUUID().toString(), selected, title))
                    saveEvents()
                    renderAll()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(e: Event) {
        AlertDialog.Builder(this)
            .setTitle("Delete event?")
            .setMessage("${e.title}\n${e.date.format(dateFmt)}")
            .setPositiveButton("Delete") { _, _ ->
                events.removeAll { it.id == e.id }
                saveEvents()
                renderAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadEvents() {
        val set = getSharedPreferences("calendar", MODE_PRIVATE)
            .getStringSet("events", emptySet()) ?: emptySet()
        events = set.mapNotNull { s ->
            val parts = s.split("|", limit = 3)
            if (parts.size < 3) return@mapNotNull null
            try {
                Event(parts[1], LocalDate.parse(parts[0]), parts[2])
            } catch (ex: Exception) {
                null
            }
        }.toMutableList()
    }

    private fun saveEvents() {
        val set = events.map { "${it.date}|${it.id}|${it.title}" }.toSet()
        getSharedPreferences("calendar", MODE_PRIVATE).edit().putStringSet("events", set).apply()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
