package com.yassertv

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
import com.yassertv.data.MediaItem

class MainActivity : AppCompatActivity() {
  private lateinit var grid: RecyclerView
  private lateinit var adapter: MediaAdapter
  private lateinit var progressBar: ProgressBar
  private lateinit var errorText: TextView
  private lateinit var tabs: LinearLayout
  private lateinit var categoryRow: LinearLayout
  private lateinit var searchInput: EditText

  private var currentSection = "live"
  private var currentCategory = "الكل"
  private var allItems = listOf<MediaItem>()
  private var dataLoaded = false
  private var cachedData = mutableMapOf<String, List<MediaItem>>()
  private val timeoutHandler = Handler(Looper.getMainLooper())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    grid = findViewById(R.id.grid)
    tabs = findViewById(R.id.tabs)
    categoryRow = findViewById(R.id.categoryRow)
    searchInput = findViewById(R.id.searchInput)
    progressBar = findViewById(R.id.progressBar)
    errorText = findViewById(R.id.errorText)

    adapter = MediaAdapter { item ->
      val intent = Intent(this, PlayerActivity::class.java)
      intent.putExtra("name", item.name)
      intent.putExtra("url", item.streamUrl())
      intent.putExtra("type", item.type)
      startActivity(intent)
    }

    updateGridColumns()
    grid.adapter = adapter

    tabs.findViewById<View>(R.id.tabLive)?.setOnClickListener { switchSection("live") }
    tabs.findViewById<View>(R.id.tabWorldCup)?.setOnClickListener { switchSection("worldcup") }
    tabs.findViewById<View>(R.id.tabMovies)?.setOnClickListener { switchSection("movies") }
    tabs.findViewById<View>(R.id.tabSeries)?.setOnClickListener { switchSection("series") }

    searchInput.addTextChangedListener(object : android.text.TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: android.text.Editable?) {
        filterItems(s?.toString() ?: "")
      }
    })

    switchSection("live")
  }

  private fun switchSection(section: String) {
    currentSection = section
    currentCategory = "الكل"
    dataLoaded = false
    progressBar.visibility = View.VISIBLE
    errorText.visibility = View.GONE
    grid.visibility = View.VISIBLE

    // Timeout بعد 15 ثانية
    timeoutHandler.removeCallbacksAndMessages(null)
    timeoutHandler.postDelayed({
      if (!dataLoaded) {
        progressBar.visibility = View.GONE
        errorText.visibility = View.VISIBLE
        errorText.text = "تعذر الاتصال بالسيرفر\nتأكد من اتصالك بالانترنت"
        grid.visibility = View.GONE
      }
    }, 15000)

    // Highlight active tab
    for (i in 0 until tabs.childCount) {
      tabs.getChildAt(i).isSelected = false
    }
    val tabMap = mapOf("live" to R.id.tabLive, "worldcup" to R.id.tabWorldCup, "movies" to R.id.tabMovies, "series" to R.id.tabSeries)
    tabs.findViewById<View>(tabMap[section] ?: R.id.tabLive)?.isSelected = true

    loadCategories(section)

    if (cachedData.containsKey(section)) {
      allItems = cachedData[section]!!
      dataLoaded = true
      filterItems(searchInput.text.toString())
      return
    }

    val loader: (List<MediaItem>) -> Unit = { items ->
      runOnUiThread {
        cachedData[section] = items
        allItems = items
        dataLoaded = true
        filterItems(searchInput.text.toString())
      }
    }

    when (section) {
      "live" -> ApiClient.fetchLiveChannels(loader)
      "worldcup" -> ApiClient.fetchWorldCupChannels(loader)
      "movies" -> ApiClient.fetchMovies(loader)
      "series" -> ApiClient.fetchSeries(loader)
    }
  }

  private fun loadCategories(section: String) {
    categoryRow.removeAllViews()
    val cats = mutableListOf("الكل")
    when (section) {
      "live" -> cats.addAll(listOf("رياضة", "أخبار", "أطفال", "أفلام", "ترفيه", "وثائقي", "دينية", "موسيقى"))
      "worldcup" -> cats.addAll(listOf("8K", "4K", "FHD", "HD", "SD"))
      "movies" -> cats.addAll(listOf("2026", "2025", "2024", "عربي", "تركي", "أجنبي"))
      "series" -> cats.addAll(listOf("عربي", "تركي", "أجنبي", "دراما", "كوميديا"))
    }

    cats.forEach { cat ->
      val btn = Button(this).apply {
        text = cat
        setOnClickListener {
          currentCategory = cat
          highlightCategory(it)
          filterItems(searchInput.text.toString())
        }
        setPadding(24, 12, 24, 12)
      }
      categoryRow.addView(btn)
    }
    (categoryRow.getChildAt(0) as? Button)?.isSelected = true
  }

  private fun highlightCategory(view: View) {
    for (i in 0 until categoryRow.childCount) {
      categoryRow.getChildAt(i).isSelected = false
    }
    view.isSelected = true
  }

  private fun filterItems(query: String) {
    val items = if (currentCategory == "الكل") allItems
    else allItems.filter { matchesCategory(it, currentCategory) }

    val filtered = if (query.isBlank()) items
    else items.filter { it.name.contains(query, ignoreCase = true) }

    adapter.submitList(filtered)
    progressBar.visibility = View.GONE
    errorText.visibility = View.GONE
    grid.visibility = View.VISIBLE
  }

  private fun matchesCategory(item: MediaItem, category: String): Boolean {
    if (currentSection == "worldcup") {
      val q = extractQuality(item.name)
      return q == category
    }
    return item.name.contains(category, ignoreCase = true) ||
           item.genre.contains(category, ignoreCase = true)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    updateGridColumns()
  }

  private fun updateGridColumns() {
    val widthDp = resources.configuration.screenWidthDp
    val cols = when {
      widthDp < 360 -> 2
      widthDp < 480 -> 2
      widthDp < 600 -> 3
      widthDp < 840 -> 4
      else -> 5
    }
    grid.layoutManager = GridLayoutManager(this, cols)
  }

  private fun extractQuality(name: String): String {
    val afterMax = name.split(Regex("MAX\\s*\\d+"), 2).getOrElse(1) { name }
    return when {
      afterMax.contains("8K", ignoreCase = true) -> "8K"
      afterMax.contains("4K", ignoreCase = true) -> "4K"
      afterMax.contains("FHD", ignoreCase = true) -> "FHD"
      afterMax.contains("HD", ignoreCase = true) -> "HD"
      afterMax.contains("SD", ignoreCase = true) -> "SD"
      else -> ""
    }
  }
}
