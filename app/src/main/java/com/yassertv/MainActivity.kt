package com.yassertv

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yassertv.data.ApiClient
import com.yassertv.data.Config
import com.yassertv.data.MediaItem

class MainActivity : AppCompatActivity() {
  private lateinit var grid: RecyclerView
  private lateinit var adapter: MediaAdapter
  private lateinit var progressBar: ProgressBar
  private lateinit var errorText: TextView
  private lateinit var tabs: LinearLayout
  private lateinit var categoryRow: LinearLayout
  private lateinit var searchInput: EditText
  private lateinit var tvServerName: TextView

  private var currentSection = "live"
  private var currentCategory = "الكل"
  private var allItems = listOf<MediaItem>()
  private var dataLoaded = false
  private var cachedData = mutableMapOf<String, List<MediaItem>>()
  private val timeoutHandler = Handler(Looper.getMainLooper())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    tvServerName = findViewById(R.id.tvServerName)
    grid = findViewById(R.id.grid)
    tabs = findViewById(R.id.tabs)
    categoryRow = findViewById(R.id.categoryRow)
    searchInput = findViewById(R.id.searchInput)
    progressBar = findViewById(R.id.progressBar)
    errorText = findViewById(R.id.errorText)

    tvServerName.text = "🖥 ${Config.activeServer.name}"
    findViewById<View>(R.id.btnSettings).setOnClickListener { showServerDialog() }

    adapter = MediaAdapter { item ->
      startActivity(Intent(this, PlayerActivity::class.java).apply {
        putExtra("name", item.name); putExtra("url", item.streamUrl()); putExtra("type", item.type)
      })
    }

    updateGridColumns()
    grid.adapter = adapter

    tabs.findViewById<View>(R.id.tabLive)?.setOnClickListener { switchSection("live") }
    tabs.findViewById<View>(R.id.tabMovies)?.setOnClickListener { switchSection("movies") }
    tabs.findViewById<View>(R.id.tabSeries)?.setOnClickListener { switchSection("series") }

    searchInput.addTextChangedListener(object : android.text.TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: android.text.Editable?) { filterItems(s?.toString() ?: "") }
    })

    switchSection("live")
  }

  private fun showServerDialog() {
    val names = Config.servers.mapIndexed { i, s -> "${i + 1}. ${s.name}" }.toTypedArray()
    val current = Config.servers.indexOfFirst { it.apiUrl == Config.activeServer.apiUrl }.coerceAtLeast(0)
    AlertDialog.Builder(this)
      .setTitle("تغيير السيرفر")
      .setSingleChoiceItems(names, current) { d, which ->
        Config.activeServer = Config.servers[which]
        tvServerName.text = "🖥 ${Config.activeServer.name}"
        cachedData.clear(); dataLoaded = false; switchSection(currentSection); d.dismiss()
      }
      .setNegativeButton("إلغاء", null).show()
  }

  private fun switchSection(section: String) {
    currentSection = section; currentCategory = "الكل"; dataLoaded = false
    progressBar.visibility = View.VISIBLE; errorText.visibility = View.GONE; grid.visibility = View.VISIBLE
    timeoutHandler.removeCallbacksAndMessages(null)
    timeoutHandler.postDelayed({
      if (!dataLoaded) { progressBar.visibility = View.GONE; errorText.visibility = View.VISIBLE; errorText.text = "تعذر الاتصال بالسيرفر\nتأكد من اتصالك بالانترنت"; grid.visibility = View.GONE }
    }, 15000)

    for (i in 0 until tabs.childCount) tabs.getChildAt(i).isSelected = false
    val tabMap = mapOf("live" to R.id.tabLive, "movies" to R.id.tabMovies, "series" to R.id.tabSeries)
    tabs.findViewById<View>(tabMap[section] ?: R.id.tabLive)?.isSelected = true
    loadCategories()
    if (cachedData.containsKey(section)) { allItems = cachedData[section]!!; dataLoaded = true; filterItems(searchInput.text.toString()); return }

    val loader: (List<MediaItem>) -> Unit = { items -> runOnUiThread { cachedData[section] = items; allItems = items; dataLoaded = true; filterItems(searchInput.text.toString()) } }
    val errorHandler: (String) -> Unit = { msg -> runOnUiThread { if (!dataLoaded) { progressBar.visibility = View.GONE; errorText.visibility = View.VISIBLE; errorText.text = "خطأ: $msg"; grid.visibility = View.GONE; dataLoaded = true } } }

    when (section) {
      "live" -> ApiClient.fetchLiveChannels(loader, errorHandler)
      "movies" -> ApiClient.fetchMovies(loader, errorHandler)
      "series" -> ApiClient.fetchSeries(loader, errorHandler)
    }
  }

  private fun loadCategories() {
    categoryRow.removeAllViews()
    val cats = mutableListOf("الكل").apply {
      when (currentSection) {
        "live" -> addAll(listOf("رياضة", "أخبار", "أطفال", "أفلام", "ترفيه", "وثائقي", "دينية", "موسيقى"))
        "movies" -> addAll(listOf("2026", "2025", "2024", "عربي", "تركي", "أجنبي", "أنمي"))
        "series" -> addAll(listOf("عربي", "تركي", "أجنبي", "دراما", "كوميديا"))
      }
    }
    cats.forEach { cat ->
      val btn = Button(this).apply {
        text = cat; setOnClickListener { currentCategory = cat; highlightCategory(it); filterItems(searchInput.text.toString()) }
        setPadding(20, 8, 20, 8); setTextColor(resources.getColorStateList(android.R.color.primary_text_dark, theme)); textSize = 12f
        background = resources.getDrawable(R.drawable.tab_bg, theme)
        setOnFocusChangeListener { v, hasFocus -> v.animate().scaleX(if (hasFocus) 1.08f else 1f).scaleY(if (hasFocus) 1.08f else 1f).setDuration(100).start() }
      }
      categoryRow.addView(btn)
    }
    (categoryRow.getChildAt(0) as? Button)?.isSelected = true
  }

  private fun highlightCategory(view: View) { for (i in 0 until categoryRow.childCount) categoryRow.getChildAt(i).isSelected = false; view.isSelected = true }

  private fun filterItems(query: String) {
    val items = if (currentCategory == "الكل") allItems else allItems.filter { it.name.contains(currentCategory, ignoreCase = true) || it.genre.contains(currentCategory, ignoreCase = true) }
    val filtered = if (query.isBlank()) items else items.filter { it.name.contains(query, ignoreCase = true) }
    adapter.submitList(filtered); progressBar.visibility = View.GONE; errorText.visibility = View.GONE; grid.visibility = View.VISIBLE
  }

  override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); updateGridColumns() }

  private fun updateGridColumns() {
    val cols = when (resources.configuration.screenWidthDp) { in 0 until 360 -> 2; in 360 until 480 -> 2; in 480 until 600 -> 3; in 600 until 840 -> 4; else -> 5 }
    grid.layoutManager = GridLayoutManager(this, cols)
  }
}
