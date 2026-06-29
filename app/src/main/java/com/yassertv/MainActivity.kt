package com.yassertv

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.provider.OpenableColumns
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import android.view.Surface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yassertv.data.ApiClient
import com.yassertv.data.AuthApiClient
import com.yassertv.data.Config
import com.yassertv.data.LiveCategory
import com.yassertv.data.MediaItem
import com.yassertv.player.StreamPlayerFactory
import com.yassertv.data.RemoteConfig
import com.yassertv.core.NativeCore
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.IMediaPlayer

private const val REQUEST_PICK_M3U = 9001

class MainActivity : AppCompatActivity() {
  private var ijkPlayer: IjkMediaPlayer? = null
  private var exoPlayer: ExoPlayer? = null
  private var playerLiveMode: Boolean? = null
  private var currentPlayer = "ijk" // "ijk" or "exo"
  private lateinit var prefs: SharedPreferences
  private val favoriteIds = mutableSetOf<String>()
  private var aspectRatio = 0 // 0=fit, 1=fill, 2=16:9, 3=4:3
  private var currentList = listOf<MediaItem>()
  private var currentIndex = -1

  private lateinit var playerPanel: View
  private lateinit var textureView: TextureView
  private lateinit var playerControls: View
  private lateinit var btnPrev: ImageButton
  private lateinit var btnNext: ImageButton
  private lateinit var btnPlayPause: ImageButton
  private lateinit var btnFullscreen: ImageButton
  private lateinit var btnSwitchPlayer: TextView
  private lateinit var btnSettings: View
  private lateinit var seekBar: SeekBar
  private var seekRow: View? = null
  private lateinit var tvTime: TextView
  private lateinit var tvStatus: TextView
  private lateinit var tvChannelName: TextView

  private lateinit var grid: RecyclerView
  private lateinit var adapter: MediaAdapter
  private lateinit var progressBar: ProgressBar
  private lateinit var errorText: TextView
  private lateinit var tabs: LinearLayout
  private lateinit var categoryRow: LinearLayout
  private lateinit var categoryScroll: HorizontalScrollView
  private lateinit var searchInput: EditText
  private var sidebarList: RecyclerView? = null
  private lateinit var sidebarAdapter: CategoryAdapter

  private var currentSection = "live"
  private var currentCategory = "الكل"
  private var isShowingCategories = false
  private var selectedCategoryName = "الكل"
  private var categoryItems = listOf<MediaItem>()
  private var allItems = listOf<MediaItem>()
  private var liveCategories = listOf<LiveCategory>()
  private var liveCategoriesLoaded = false
  private var vodCategories = listOf<LiveCategory>()
  private var vodCategoriesLoaded = false
  private var seriesCategories = listOf<LiveCategory>()
  private var seriesCategoriesLoaded = false
  private var dataLoaded = false
  private var cachedData = mutableMapOf<String, List<MediaItem>>()
  private val handler = Handler(Looper.getMainLooper())
  private val statusHandler = Handler(Looper.getMainLooper())
  private val heartbeatHandler = Handler(Looper.getMainLooper())
  private val HEARTBEAT_INTERVAL_MS = 3 * 60 * 1000L
  private val heartbeatRunnable: Runnable = object : Runnable {
    override fun run() {
      AuthApiClient.sendHeartbeat(this@MainActivity)
      heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
    }
  }

  private lateinit var categorySpinner: Spinner
  private var spinnerAdapter: ArrayAdapter<String>? = null
  private var categoryNames = mutableListOf("الكل")
  private var ignoreSpinnerEvent = false

  private val m3uPlaylists = mutableListOf<M3uPlaylist>()
  private val m3uChannels = mutableMapOf<String, List<MediaItem>>()
  private val m3uTabViews = mutableMapOf<Int, View>()
  private var previewMode = false
  private val m3uPickerLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) importM3uFromUri(uri)
  }
  
  private var isFullscreen = false
  private var reconnectCount = 0
  private val hlsFallbackItems = mutableSetOf<String>()
  private var currentPlayingLive = false
  private var wakeLock: PowerManager.WakeLock? = null
  private var lastPlayedItem: MediaItem? = null
  private val playTimeoutRunnable = Runnable { handlePlaybackStall() }
  private val MAX_RECONNECT = 10
  private val progressHandler = Handler(Looper.getMainLooper())
  private val progressRunnable = object : Runnable {
    override fun run() {
      try {
        val isPlaying = if (currentPlayer == "ijk") ijkPlayer?.isPlaying == true else exoPlayer?.isPlaying == true
        if (!isPlaying || isSeeking || currentPlayingLive) { progressHandler.postDelayed(this, 1000); return }
        val dur = if (currentPlayer == "ijk") ijkPlayer?.duration ?: 0L else exoPlayer?.duration ?: 0L
        val cur = if (currentPlayer == "ijk") ijkPlayer?.currentPosition ?: 0L else exoPlayer?.currentPosition ?: 0L
        if (dur > 0) {
          seekBar.progress = ((cur * 1000) / dur).toInt()
        }
        tvTime.text = "${cur / 60000}:${(cur % 60000) / 1000}"
      } catch (_: Exception) {}
      progressHandler.postDelayed(this, 1000)
    }
  }

  private var backPressedOnce = false
  private val resetBackPressRunnable = Runnable { backPressedOnce = false }
  private val hideControlsRunnable = Runnable { hidePlayerControls() }
  private val CONTROLS_AUTO_HIDE_MS = 3000L
  private val EXIT_CONFIRM_MS = 2500L
  private var mobileControlsVisible = true
  private lateinit var tvSectionTitle: TextView
  private var tvGridDecorationAdded = false
  private var pendingRestoreSearch: String? = null
  private var pendingRestoreItemId: String? = null
  private var pendingRestoreItemType: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prefs = getSharedPreferences("yassertv", Context.MODE_PRIVATE)
    currentPlayer = prefs.getString("player", "ijk") ?: "ijk"
    aspectRatio = prefs.getInt("aspect_ratio", 0)
    favoriteIds.addAll(prefs.getStringSet("fav", emptySet()) ?: emptySet())
    savedInstanceState?.let { restoreInstanceState(it) }
    setContentView(R.layout.activity_main)

    playerPanel = findViewById(R.id.playerPanel)
    textureView = findViewById(R.id.textureView)
    playerControls = findViewById(R.id.playerControls)
    btnPrev = findViewById(R.id.btnPrev)
    btnNext = findViewById(R.id.btnNext)
    btnPlayPause = findViewById(R.id.btnPlayPause)
    btnFullscreen = findViewById(R.id.btnFullscreen)
    seekBar = findViewById(R.id.seekBar)
    seekRow = findViewById(R.id.seekRow)
    tvTime = findViewById(R.id.tvTime)
    tvStatus = findViewById(R.id.tvStatus)
    tvChannelName = findViewById(R.id.tvChannelName)

    grid = findViewById(R.id.grid)
    tabs = findViewById(R.id.tabs)
    categoryRow = findViewById(R.id.categoryRow)
    categoryScroll = findViewById(R.id.categoryScroll)
    categorySpinner = findViewById(R.id.categorySpinner)
    searchInput = findViewById(R.id.searchInput)
    progressBar = findViewById(R.id.progressBar)
    errorText = findViewById(R.id.errorText)
    tvSectionTitle = findViewById(R.id.tvSectionTitle)
    sidebarList = findViewById(R.id.sidebarList)
    // btnImportM3u (sidebar): short press → focus grid, long press → import M3U
    findViewById<View>(R.id.btnImportM3u)?.let { btn ->
      btn.setOnClickListener {
        grid.post {
          grid.isFocusable = true
          grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
          if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
        }
      }
      btn.setOnLongClickListener {
        openM3uPicker()
        true
      }
      btn.setOnFocusChangeListener { v, hasFocus ->
        v.animate().scaleX(if (hasFocus) 1.1f else 1f).scaleY(if (hasFocus) 1.1f else 1f).setDuration(120).start()
      }
    }
    findViewById<View>(R.id.btnImportM3uTop)?.setOnClickListener { openM3uPicker() }

    sidebarAdapter = CategoryAdapter { catName ->
      if (!isShowingCategories && catName == selectedCategoryName) {
        if (isTvDevice() && lastPlayedItem != null) toggleFullscreen()
        return@CategoryAdapter
      }
      onCategorySelected(catName)
    }
    sidebarList?.adapter = sidebarAdapter
    sidebarList?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

    adapter = MediaAdapter(
      onItemClick = { item ->
      when (item.type) {
        "category" -> onCategorySelected(item.name)
        "live" -> {
          if (isTvDevice() && item.id == lastPlayedItem?.id && isCurrentPlayerPlaying()) {
            toggleFullscreen()
            return@MediaAdapter
          }
          playItem(item)
          if (isTvDevice()) {
            showPlayerControlsBriefly()
          } else {
            showContentPanel(false)
            if (!isFullscreen) toggleFullscreen()
          }
        }
        "movie" -> {
          if (isTvDevice() && item.id == lastPlayedItem?.id && isCurrentPlayerPlaying()) {
            toggleFullscreen()
            return@MediaAdapter
          }
          playItem(item)
          if (isTvDevice()) {
            showPlayerControlsBriefly()
          } else {
            showContentPanel(false)
            if (!isFullscreen) toggleFullscreen()
          }
        }
        "series" -> showEpisodesDialog(item)
        "episode" -> {
          playItem(item)
          if (isTvDevice()) {
            showPlayerControlsBriefly()
          } else {
            showContentPanel(false)
            if (!isFullscreen) toggleFullscreen()
          }
        }
        else -> playItem(item)
      }
    },
      tvMode = isTvDevice(),
      onFavClick = { toggleFavorite(it) },
      isFav = { it.id in favoriteIds }
    )

    updateGridColumns()
    grid.adapter = adapter

    tabs.findViewById<View>(R.id.tabLive)?.setOnClickListener { switchSection("live") }
    tabs.findViewById<View>(R.id.tabMovies)?.setOnClickListener { switchSection("movies") }
    tabs.findViewById<View>(R.id.tabSeries)?.setOnClickListener { switchSection("series") }
    tabs.findViewById<View>(R.id.tabFavorites)?.setOnClickListener { switchSection("favorites") }
    tabs.findViewById<View>(R.id.tabM3u)?.setOnClickListener { switchSection("m3u") }

    searchInput.addTextChangedListener(object : android.text.TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: android.text.Editable?) { filterItems(s?.toString() ?: "") }
    })

    spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
    spinnerAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    categorySpinner.adapter = spinnerAdapter
    categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (ignoreSpinnerEvent) return
        currentCategory = categoryNames[position]
        filterItems(searchInput.text.toString())
      }
      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    btnPrev.setOnClickListener { switchChannel(false) }
    btnNext.setOnClickListener { switchChannel(true) }
    btnPlayPause.setOnClickListener { togglePlayPause() }
    btnFullscreen.setOnClickListener { toggleFullscreen() }
    btnSwitchPlayer = findViewById(R.id.btnSwitchPlayer)
    btnSwitchPlayer.text = currentPlayer.uppercase()
    btnSwitchPlayer.setOnClickListener { switchPlayer() }
    btnSettings = findViewById(R.id.btnSettings)
    btnSettings.setOnClickListener { showSettingsDialog() }

    val playerClickListener = View.OnClickListener {
      if (isOverlayLayout()) {
        val panel = findViewById<View>(R.id.contentPanel) ?: return@OnClickListener
        showContentPanel(panel.visibility != View.VISIBLE)
      } else {
        toggleMobileControls()
      }
    }
    textureView.setOnClickListener(playerClickListener)
    playerPanel.setOnClickListener(playerClickListener)

    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {}
      override fun onStartTrackingTouch(sb: SeekBar) { isSeeking = true }
      override fun onStopTrackingTouch(sb: SeekBar) {
        isSeeking = false
        val dur = if (currentPlayer == "ijk") ijkPlayer?.duration ?: 0L else exoPlayer?.duration ?: 0L
        if (dur > 0) {
          val pos = (sb.progress.toLong() * dur) / 1000
          if (currentPlayer == "ijk") ijkPlayer?.seekTo(pos) else exoPlayer?.seekTo(pos)
        }
      }
    })

    setupPlayer()
    setupDeviceUi()
    applyFullscreenState()

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() = handleBackPress()
    })

    if (isTvDevice()) showContentPanel(true)
    loadM3uPlaylist()
    switchSection(currentSection)
  }

  private fun openM3uPicker() {
    try {
      m3uPickerLauncher.launch(arrayOf("*/*"))
    } catch (e: Exception) {
      android.util.Log.e("YasserTV", "M3U picker error: ${e.message}")
    }
  }

  private fun importM3uFromUri(uri: Uri) {
    try {
      contentResolver.openInputStream(uri)?.use { input ->
        val content = input.bufferedReader().readText()
        if (!content.startsWith("#EXTM3U")) {
          Toast.makeText(this, "الملف ليس قائمة M3U صالحة", Toast.LENGTH_LONG).show()
          return
        }
        val channels = M3UParser.parse(content)
        if (channels.isEmpty()) {
          Toast.makeText(this, "لا توجد قنوات في الملف", Toast.LENGTH_LONG).show()
          return
        }
        // Save to internal storage with unique name
        val dir = java.io.File(filesDir, "iptv_playlists")
        dir.mkdirs()
        val idx = dir.listFiles()?.filter { it.name.endsWith(".m3u") }?.size ?: 0
        val fileName = "playlist_$idx.m3u"
        java.io.File(dir, fileName).writeText(content)

        val listName = "مباشر${m3uPlaylists.size + 2}"
        val key = "m3u_${m3uPlaylists.size}"
        m3uChannels[key] = channels
        m3uPlaylists.add(M3uPlaylist(listName, fileName))

        // Add dynamic tab
        val tab = TextView(this).apply {
          id = View.generateViewId()
          text = listName
          setTextColor(resources.getColorStateList(R.color.tab_text_selector, theme))
          setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.tab_text_size))
          gravity = android.view.Gravity.CENTER
          maxLines = 1
          ellipsize = android.text.TextUtils.TruncateAt.END
          setPaddingRelative(4, 0, 4, 0)
          setBackgroundResource(R.drawable.tab_bg)
          isFocusable = true
          isClickable = true
          typeface = android.graphics.Typeface.DEFAULT_BOLD
          layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
          setOnClickListener { switchM3uPlaylist(m3uPlaylists.size - 1) }
          setOnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.05f else 1f).scaleY(if (hasFocus) 1.05f else 1f).setDuration(120).start()
          }
        }
        tabs.addView(tab)
        m3uTabViews[m3uPlaylists.size - 1] = tab

        Toast.makeText(this, "تم استيراد ${channels.size} قناة كـ $listName", Toast.LENGTH_LONG).show()
        if (currentSection == "m3u") {
          switchM3uPlaylist(m3uPlaylists.size - 1)
        }
      }
    } catch (e: Exception) {
      android.util.Log.e("YasserTV", "M3U import error: ${e.message}")
      Toast.makeText(this, "فشل استيراد الملف: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  private fun loadM3uPlaylist() {
    try {
      m3uPlaylists.clear()
      m3uChannels.clear()
      // Load default from assets
      try {
        val content = assets.open("iptv_playlist.m3u").bufferedReader().readText()
        if (content.isNotBlank()) {
          val channels = M3UParser.parse(content).mapIndexed { i, ch -> ch.copy(num = i + 1) }
          val key = "m3u_0"
          m3uChannels[key] = channels
          m3uPlaylists.add(M3uPlaylist("مباشر2", "iptv_playlist.m3u", isDefault = true))
          android.util.Log.i("YasserTV", "Default M3U: ${channels.size} channels")
        }
      } catch (_: Exception) {}

      // Load imported playlists
      val dir = java.io.File(filesDir, "iptv_playlists")
      if (dir.exists()) {
        dir.listFiles()?.filter { it.name.endsWith(".m3u") }?.sortedBy { it.name }?.forEachIndexed { idx, file ->
          val content = file.readText()
          if (content.isNotBlank()) {
            val channels = M3UParser.parse(content)
            val key = "m3u_${idx + 1}"
            m3uChannels[key] = channels
            val name = file.nameWithoutExtension.replace("_", " ").ifBlank { "قائمة ${idx + 1}" }
            m3uPlaylists.add(M3uPlaylist(name, file.name))
            android.util.Log.i("YasserTV", "Imported M3U '$name': ${channels.size} channels")
          }
        }
      }
      rebuildM3uTabs()
    } catch (e: Exception) {
      android.util.Log.e("YasserTV", "M3U load error: ${e.message}")
    }
  }

  private fun rebuildM3uTabs() {
    if (m3uPlaylists.isEmpty()) return
    // Remove old dynamic M3U tabs (keep the first one which is in the layout)
    m3uTabViews.values.forEach { (it.parent as? ViewGroup)?.removeView(it) }
    m3uTabViews.clear()
    // Add dynamic tabs for additional playlists (index > 0)
    for (i in 1 until m3uPlaylists.size) {
      val playlist = m3uPlaylists[i]
      val tab = TextView(this).apply {
        id = View.generateViewId()
        text = playlist.name
        setTextColor(resources.getColorStateList(R.color.tab_text_selector, theme))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.tab_text_size))
        gravity = android.view.Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPaddingRelative(4, 0, 4, 0)
        setBackgroundResource(R.drawable.tab_bg)
        isFocusable = true
        isClickable = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        setOnClickListener { switchM3uPlaylist(i) }
        setOnFocusChangeListener { v, hasFocus ->
          v.animate().scaleX(if (hasFocus) 1.05f else 1f).scaleY(if (hasFocus) 1.05f else 1f).setDuration(120).start()
        }
      }
      tabs.addView(tab)
      m3uTabViews[i] = tab
    }
  }

  private fun switchM3uPlaylist(index: Int) {
    if (index < 0 || index >= m3uPlaylists.size) return
    val key = "m3u_$index"
    val channels = m3uChannels[key] ?: return
    allItems = channels
    currentList = sortArabicFirst(channels)
    adapter.submitList(currentList)
    progressBar.visibility = View.GONE
    errorText.visibility = View.GONE
    grid.visibility = View.VISIBLE
    dataLoaded = true
    // Update tab selection
    for ((i, tab) in m3uTabViews) {
      tab.isSelected = i == index
    }
    tabs.findViewById<View>(R.id.tabM3u)?.isSelected = index == 0
    if (isTvDevice()) {
      tvSectionTitle.text = m3uPlaylists[index].name
      setFocusTrap(true)
      grid.post { if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus() }
    }
  }

  private fun restoreInstanceState(state: Bundle) {
    isFullscreen = state.getBoolean("isFullscreen", false)
    currentSection = state.getString("section") ?: currentSection
    currentCategory = state.getString("category") ?: currentCategory
    pendingRestoreSearch = state.getString("search")
    pendingRestoreItemId = state.getString("lastId")
    pendingRestoreItemType = state.getString("lastType")
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean("isFullscreen", isFullscreen)
    outState.putString("section", currentSection)
    outState.putString("category", currentCategory)
    outState.putString("search", searchInput.text?.toString() ?: "")
    lastPlayedItem?.let {
      outState.putString("lastId", it.id)
      outState.putString("lastType", it.type)
    }
  }

  private fun applyFullscreenState() {
    if (isFullscreen) {
      btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit)
      hideSystemUI()
      showContentPanel(false)
    }
    adjustPlayerHeight()
  }

  private fun setupDeviceUi() {
    categoryRow.visibility = View.GONE
    categoryScroll.visibility = View.GONE
    categorySpinner.visibility = View.GONE
    if (isTvDevice()) {
      tvSectionTitle.visibility = View.VISIBLE
      setupTvGridDecoration()
      setupTvNavigation()
      updateSectionTitle()
    } else {
      tvSectionTitle.visibility = View.GONE
      setupMobileUi()
    }
  }

  private fun setupTvGridDecoration() {
    if (tvGridDecorationAdded) return
    val spacing = resources.getDimensionPixelSize(R.dimen.grid_spacing)
    grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
      override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(spacing / 2, spacing / 2, spacing / 2, spacing / 2)
      }
    })
    tvGridDecorationAdded = true
  }

  private fun updateSectionTitle() {
    if (!isTvDevice()) return
    tvSectionTitle.text = when (currentSection) {
      "live" -> getString(R.string.live)
      "movies" -> getString(R.string.movies)
      "series" -> getString(R.string.series)
      "favorites" -> getString(R.string.favorites)
      "m3u" -> getString(R.string.m3u_tab)
      else -> getString(R.string.app_name)
    }
  }

  private fun isTvDevice(): Boolean {
    return (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
      Configuration.UI_MODE_TYPE_TELEVISION
  }

  private fun isOverlayLayout(): Boolean = true

  private fun isPortrait(): Boolean =
    resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  private fun setupMobileUi() {
    searchInput.showSoftInputOnFocus = true
    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

    val cp = findViewById<View>(R.id.contentPanel)
    cp.setOnApplyWindowInsetsListener { v, insets ->
      try {
        v.setPadding(
          insets.getSystemWindowInsetLeft().coerceAtLeast(0) + 8,
          insets.getSystemWindowInsetTop().coerceAtLeast(0) + 50,
          insets.getSystemWindowInsetRight().coerceAtLeast(0) + 8,
          8
        )
      } catch (_: Exception) {}
      insets
    }
    categoryRow.visibility = View.GONE
    categoryScroll.visibility = View.GONE
    categorySpinner.visibility = View.GONE

    val spacing = resources.getDimensionPixelSize(R.dimen.grid_spacing)
    if (grid.itemDecorationCount == 0) {
      grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
          val half = spacing / 2
          outRect.set(half, half, half, half)
        }
      })
    }

    val swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(e: MotionEvent): Boolean = true

      override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
      ): Boolean {
        if (e1 == null || !isWatching()) return false
        if (!isFullscreen && !isOverlayLayout() && !isLandscape()) return false
        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y
        if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) &&
          kotlin.math.abs(diffX) > 80 && kotlin.math.abs(velocityX) > 200
        ) {
          switchChannel(diffX < 0)
          return true
        }
        return false
      }
    })

    val touchListener = View.OnTouchListener { _, event ->
      swipeDetector.onTouchEvent(event)
      false
    }
    textureView.setOnTouchListener(touchListener)
    playerPanel.setOnTouchListener(touchListener)
  }

  private fun setupTvNavigation() {
    searchInput.showSoftInputOnFocus = false

    grid.isFocusable = false
    grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    grid.layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(
      this, R.anim.grid_layout_animation
    )

    listOf(btnPrev, btnNext, btnPlayPause, btnFullscreen, btnSwitchPlayer, btnSettings).forEach { btn ->
      btn.setOnFocusChangeListener { v, hasFocus ->
        if (hasFocus) showPlayerControls()
        v.animate()
          .scaleX(if (hasFocus) 1.12f else 1f)
          .scaleY(if (hasFocus) 1.12f else 1f)
          .alpha(if (hasFocus) 1f else 0.85f)
          .setDuration(120)
          .start()
      }
    }

    val playerKeyListener = View.OnKeyListener { _, keyCode, event ->
      if (event.action == KeyEvent.ACTION_DOWN) {
        when (keyCode) {
          KeyEvent.KEYCODE_DPAD_UP -> {
            if (isFullscreen) {
              showContentPanel(true)
              isFullscreen = false
              btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
              grid.post {
                grid.isFocusable = true
                grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
              }
              true
            } else false
          }
          KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (!isFullscreen) {
              playerControls.visibility = View.VISIBLE
              playerControls.requestFocus()
              true
            } else false
          }
          else -> handlePlayerKey(keyCode)
        }
      } else false
    }
    textureView.setOnKeyListener(playerKeyListener)
    playerPanel.setOnKeyListener(playerKeyListener)

    playerControls.setOnKeyListener { _, keyCode, event ->
      if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
        if (!isFullscreen) {
          grid.post {
            grid.isFocusable = true
            grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
          }
          true
        } else false
      } else false
    }
  }

  private var pendingItem: MediaItem? = null

  private fun showEpisodesDialog(series: MediaItem) {
    val dialog = AlertDialog.Builder(this)
      .setTitle(series.name)
      .setView(R.layout.dialog_loading)
      .setNegativeButton("إلغاء", null)
      .show()

    ApiClient.fetchSeriesEpisodes(series.id, { episodes, _ ->
      runOnUiThread {
        dialog.dismiss()
        if (episodes.isEmpty()) {
          Toast.makeText(this, "لا توجد حلقات لهذا المسلسل", Toast.LENGTH_SHORT).show()
          return@runOnUiThread
        }
        showEpisodeList(series.name, episodes)
      }
    }, { msg ->
      runOnUiThread {
        dialog.dismiss()
        Toast.makeText(this, "خطأ: $msg", Toast.LENGTH_SHORT).show()
      }
    })
  }

  private fun showEpisodeList(seriesName: String, episodes: List<MediaItem>) {
    val adapter = MediaAdapter(
      onItemClick = { ep ->
      if (isOverlayLayout()) {
        showContentPanel(false)
      }
      playItem(ep)
    },
      tvMode = isTvDevice()
    )
    adapter.submitList(episodes)

    val listView = RecyclerView(this)
    listView.layoutManager = LinearLayoutManager(this)
    listView.adapter = adapter
    listView.setPadding(24, 8, 24, 8)

    AlertDialog.Builder(this)
      .setTitle(seriesName)
      .setView(listView)
      .setPositiveButton("إغلاق", null)
      .show()
  }

  private fun setupPlayer() {
    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        if (currentPlayer == "ijk") ijkPlayer?.setSurface(Surface(surface))
        else exoPlayer?.setVideoTextureView(textureView)
        pendingItem?.let { playItem(it); pendingItem = null }
      }
      override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
      override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
        if (currentPlayer == "ijk") ijkPlayer?.setSurface(null)
        return true
      }
      override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
    }
  }

  private fun releasePlayer() {
    stopProgressUpdates()
    releasePlaybackWakeLock()
    ijkPlayer?.apply {
      try { stop() } catch (_: Exception) {}
      try { release() } catch (_: Exception) {}
    }
    ijkPlayer = null
    exoPlayer?.apply {
      try { stop() } catch (_: Exception) {}
      try { release() } catch (_: Exception) {}
    }
    exoPlayer = null
    playerLiveMode = null
  }

  private fun isCurrentPlayerPlaying(): Boolean {
    return if (currentPlayer == "ijk") ijkPlayer?.isPlaying == true else exoPlayer?.isPlaying == true
  }

  private fun switchPlayer() {
    val new = if (currentPlayer == "ijk") "exo" else "ijk"
    currentPlayer = new
    prefs.edit().putString("player", new).apply()
    btnSwitchPlayer.text = new.uppercase()
    releasePlayer()
    lastPlayedItem?.let { playItem(it) }
  }

  private fun selectedBufferMs(forLive: Boolean): Int {
    val defaultMs = if (forLive) 90_000 else 30_000
    val maxMs = if (forLive) 180_000 else 60_000
    return prefs.getInt("buffer_ms", defaultMs).coerceIn(10_000, maxMs)
  }

  private fun liveBufferTimeoutMs(): Long {
    return (selectedBufferMs(true).toLong() + 30_000L).coerceIn(60_000L, 180_000L)
  }

  private fun ensurePlayer(forLive: Boolean) {
    if (currentPlayer == "ijk") {
      if (ijkPlayer != null) return
      ijkPlayer = IjkMediaPlayer().apply {
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", forLive.compareTo(false).toLong())
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", if (forLive) 1L else 0L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 512L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect-at-eof", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 30_000_000L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rw_timeout", 60_000_000L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", Config.USER_AGENT)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", "Accept: */*\r\nConnection: keep-alive\r\n")
        setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", 500_000L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1_024_000L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1L)
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max_ts_probe", 100L)
        setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
        setVolume(1.0f, 1.0f)

        setOnPreparedListener { mp ->
          mp.start()
          runOnUiThread {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            handler.removeCallbacks(playTimeoutRunnable)
            reconnectCount = 0
            acquirePlaybackWakeLock()
          }
        }

        setOnErrorListener { mp, what, extra ->
          runOnUiThread {
            tvStatus.apply {
              text = "خطأ - إعادة المحاولة..."
              setTextColor(android.graphics.Color.parseColor("#ff5a76"))
              visibility = View.VISIBLE
            }
            handler.removeCallbacks(playTimeoutRunnable)
            handler.postDelayed(playTimeoutRunnable, 1000L)
          }
          true
        }

        setOnInfoListener { mp, what, extra ->
          when (what) {
            IMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
              runOnUiThread {
                tvStatus.apply {
                  text = "تحميل..."
                  setTextColor(android.graphics.Color.parseColor("#ffb000"))
                  visibility = View.VISIBLE
                }
                handler.removeCallbacks(playTimeoutRunnable)
                handler.postDelayed(playTimeoutRunnable, liveBufferTimeoutMs())
              }
            }
            IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
              runOnUiThread { tvStatus.visibility = View.GONE }
            }
            IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
              runOnUiThread {
                textureView.visibility = View.VISIBLE
                tvStatus.visibility = View.GONE
                handler.removeCallbacks(playTimeoutRunnable)
              }
            }
            IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START -> {
              runOnUiThread { textureView.visibility = View.VISIBLE }
            }
          }
          true
        }

        setOnCompletionListener {
          runOnUiThread {
            if (currentPlayingLive) {
              handler.removeCallbacks(playTimeoutRunnable)
              handler.postDelayed(playTimeoutRunnable, 1000L)
            } else {
              playNext()
            }
          }
        }
      }
    } else {
      if (exoPlayer != null && playerLiveMode == forLive) return
      releasePlayer()
      exoPlayer = StreamPlayerFactory.create(this, forLive, selectedBufferMs(forLive)).apply {
        setVideoTextureView(textureView)
        addListener(object : androidx.media3.common.Player.Listener {
          override fun onPlaybackStateChanged(state: Int) {
            when (state) {
              androidx.media3.common.Player.STATE_READY -> {
                textureView.visibility = View.VISIBLE
                tvStatus.visibility = View.GONE
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                handler.removeCallbacks(playTimeoutRunnable)
                reconnectCount = 0
                acquirePlaybackWakeLock()
              }
              androidx.media3.common.Player.STATE_BUFFERING -> {
                tvStatus.apply {
                  text = "تحميل..."
                  setTextColor(android.graphics.Color.parseColor("#ffb000"))
                  visibility = View.VISIBLE
                }
                handler.removeCallbacks(playTimeoutRunnable)
                handler.postDelayed(playTimeoutRunnable, liveBufferTimeoutMs())
              }
              androidx.media3.common.Player.STATE_ENDED -> {
                if (currentPlayingLive) {
                  handler.removeCallbacks(playTimeoutRunnable)
                  handler.postDelayed(playTimeoutRunnable, 1000L)
                } else {
                  playNext()
                }
              }
            }
          }

          override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            tvStatus.apply {
              text = "خطأ - إعادة المحاولة..."
              setTextColor(android.graphics.Color.parseColor("#ff5a76"))
              visibility = View.VISIBLE
            }
            handler.removeCallbacks(playTimeoutRunnable)
            handler.postDelayed(playTimeoutRunnable, 1000L)
          }

          override fun onRenderedFirstFrame() {
            textureView.visibility = View.VISIBLE
            tvStatus.visibility = View.GONE
            handler.removeCallbacks(playTimeoutRunnable)
          }
        })
      }
      playerLiveMode = forLive
    }
  }

  private fun resolveStreamUrl(item: MediaItem): String {
    if (item.directUrl.isNotEmpty()) return item.directUrl
    if (item.type == "live") {
      if (item.id in hlsFallbackItems) {
        val m3u8 = item.streamUrlM3u8()
        if (m3u8.isNotEmpty()) return m3u8
      }
      val ts = item.streamUrl()
      if (ts.isNotEmpty()) return ts
      return item.streamUrlM3u8()
    }
    return item.streamUrl()
  }

  private fun acquirePlaybackWakeLock() {
    if (wakeLock?.isHeld == true) return
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YasserTV:Playback").apply {
      acquire(4 * 60 * 60 * 1000L)
    }
  }

  private fun releasePlaybackWakeLock() {
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
  }

  private fun startProgressUpdates() {
    progressHandler.removeCallbacks(progressRunnable)
    progressHandler.post(progressRunnable)
  }

  private fun stopProgressUpdates() {
    progressHandler.removeCallbacks(progressRunnable)
  }

  private var isSeeking = false

  private fun playItem(item: MediaItem) {
    val isNewItem = lastPlayedItem?.id != item.id || lastPlayedItem?.type != item.type
    if (isNewItem) {
      lastPlayedItem = item
      reconnectCount = 0
      NativeCore.resetBuffer()
    }
    handler.removeCallbacks(playTimeoutRunnable)

    tvChannelName.text = item.name
    try { findViewById<TextView>(R.id.tvNowPlaying)?.text = item.name } catch (_: Exception) {}
    currentIndex = currentList.indexOf(item)

    val useHls = item.id in hlsFallbackItems
    val url = if (useHls && item.type == "live") item.streamUrlM3u8() else resolveStreamUrl(item)
    if (url.isEmpty()) return

    val isLive = item.type == "live"
    currentPlayingLive = isLive
    setSeekVisible(!isLive)

    textureView.visibility = View.INVISIBLE
    try {
      ensurePlayer(isLive)
      if (currentPlayer == "ijk") {
        ijkPlayer?.apply {
          stop()
          reset()
          textureView.surfaceTexture?.let { st ->
            setSurface(Surface(st))
          }
          setDataSource(url)
          prepareAsync()
        }
      } else {
        exoPlayer?.apply {
          stop()
          clearMediaItems()
          setMediaItem(ExoMediaItem.fromUri(Uri.parse(url)))
          playWhenReady = true
          prepare()
        }
      }
      if (isLive) stopProgressUpdates() else startProgressUpdates()
    } catch (e: Exception) {
      android.util.Log.e("YasserTV", "playItem error: ${e.message}", e)
      tvStatus.apply {
        text = "تعذر التشغيل"
        setTextColor(android.graphics.Color.parseColor("#ff5a76"))
        visibility = View.VISIBLE
      }
      handler.removeCallbacks(playTimeoutRunnable)
      handler.postDelayed(playTimeoutRunnable, 3000)
    }
  }

  private fun playPrevious(): Boolean {
    if (currentIndex > 0) {
      playItem(currentList[currentIndex - 1])
      return true
    }
    return false
  }

  private fun playNext(): Boolean {
    if (currentIndex < currentList.size - 1) {
      playItem(currentList[currentIndex + 1])
      return true
    }
    return false
  }

  private fun setSeekVisible(visible: Boolean) {
    val visibility = if (visible) View.VISIBLE else View.GONE
    seekRow?.visibility = visibility
    if (seekRow == null) {
      seekBar.visibility = visibility
      tvTime.visibility = visibility
    }
  }

  private fun switchChannel(forward: Boolean) {
    val switched = if (forward) playNext() else playPrevious()
    if (switched) {
      val item = currentList.getOrNull(currentIndex)
      if (item != null) {
        val msg = if (forward) getString(R.string.channel_next, item.name)
        else getString(R.string.channel_prev, item.name)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
      }
      showPlayerControlsBriefly()
    } else {
      val msg = if (forward) getString(R.string.no_channel_next) else getString(R.string.no_channel_prev)
      Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
  }

  private fun togglePlayPause() {
    if (currentPlayer == "ijk") {
      val p = ijkPlayer ?: return
      if (p.isPlaying) {
        p.pause()
        handler.removeCallbacks(playTimeoutRunnable)
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
      } else {
        p.start()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
      }
    } else {
      val p = exoPlayer ?: return
      if (p.isPlaying) {
        p.pause()
        handler.removeCallbacks(playTimeoutRunnable)
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
      } else {
        p.play()
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
      }
    }
  }

  private fun switchSection(section: String) {
    currentSection = section
    currentCategory = "الكل"
    isShowingCategories = false
    selectedCategoryName = "الكل"
    dataLoaded = false
    
    // M3U import button only visible in M3U tab
    findViewById<View>(R.id.btnImportM3u)?.visibility = if (section == "m3u") View.VISIBLE else View.GONE
    
    updateSectionTitle()
    updateGridColumns()
    progressBar.visibility = View.VISIBLE
    errorText.visibility = View.GONE
    grid.visibility = View.VISIBLE
    if (isTvDevice()) {
      sidebarAdapter.submitList(emptyList())
      sidebarList?.visibility = View.VISIBLE
      grid.post { if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus() }
    }

    handler.removeCallbacksAndMessages(null)
    handler.postDelayed({
      if (!dataLoaded) {
        progressBar.visibility = View.GONE
        errorText.visibility = View.VISIBLE
        errorText.text = "تعذر الاتصال بالسيرفر\nتأكد من اتصالك بالانترنت"
        grid.visibility = View.GONE
      }
    }, 15000)

    val staticTabIds = listOf(R.id.tabLive, R.id.tabMovies, R.id.tabSeries, R.id.tabFavorites, R.id.tabM3u)
    for (i in 0 until tabs.childCount) {
      val tab = tabs.getChildAt(i)
      if (tab.id in staticTabIds || tab.id in m3uTabViews.values.map { it.id }) {
        tab.isSelected = false
        tab.setBackgroundColor(android.graphics.Color.TRANSPARENT)
      }
    }
    val tabMap = mapOf(
      "live" to R.id.tabLive,
      "movies" to R.id.tabMovies,
      "series" to R.id.tabSeries,
      "favorites" to R.id.tabFavorites,
      "m3u" to R.id.tabM3u
    )
    val tabColors = mapOf(
      "live" to R.color.tab_live_bg,
      "movies" to R.color.tab_movies_bg,
      "series" to R.color.tab_series_bg,
      "favorites" to R.color.tab_favorites_bg,
      "m3u" to R.color.tab_m3u_bg
    )
    val selectedTab = tabs.findViewById<View>(tabMap[section] ?: R.id.tabLive) ?: return
    selectedTab.isSelected = true
    selectedTab.setBackgroundColor(getColor(tabColors[section] ?: R.color.tab_live_bg))

    if (section == "favorites") {
      loadFavorites()
      return
    }

    if (section == "m3u") {
      loadM3uSection()
      return
    }

    loadCategories(section)

    if (cachedData.containsKey(section)) {
      allItems = cachedData[section]!!
      dataLoaded = true
      return
    }

    val loader: (List<MediaItem>) -> Unit = { items ->
      runOnUiThread {
        cachedData[section] = items
        allItems = items
        dataLoaded = true
        if (!isShowingCategories) {
          populateSidebarChannels()
        } else {
          progressBar.visibility = View.GONE
        }
      }
    }

    val errorHandler: (String) -> Unit = { msg ->
      runOnUiThread {
        if (!dataLoaded) {
          progressBar.visibility = View.GONE
          errorText.visibility = View.VISIBLE
          errorText.text = "خطأ: $msg"
          grid.visibility = View.GONE
          dataLoaded = true
        }
      }
    }

    when (section) {
      "live" -> ApiClient.fetchLiveChannels(loader, errorHandler)
      "movies" -> ApiClient.fetchMovies(loader, errorHandler)
      "series" -> ApiClient.fetchSeries(loader, errorHandler)
      "favorites" -> Unit
    }
  }

  private fun loadM3uSection() {
    if (m3uPlaylists.isEmpty()) {
      progressBar.visibility = View.GONE
      errorText.visibility = View.VISIBLE
      errorText.text = "لا توجد قنوات M3U\nاستورد ملف M3U من زر الاستيراد"
      grid.visibility = View.GONE
      dataLoaded = true
      return
    }
    switchM3uPlaylist(0)
  }

  private fun loadFavorites() {
    dataLoaded = true
    val favItems = allItems.filter { it.id in favoriteIds }
    currentList = sortArabicFirst(favItems)
    adapter.submitList(currentList)
    progressBar.visibility = View.GONE
    errorText.visibility = if (favItems.isEmpty()) {
      errorText.text = "لا توجد قنوات مفضلة\nاضغط ☆ على القناة لإضافتها"
      View.VISIBLE
    } else View.GONE
    grid.visibility = View.VISIBLE
  }

  private fun toggleFavorite(item: MediaItem) {
    if (item.id in favoriteIds) favoriteIds.remove(item.id) else favoriteIds.add(item.id)
    prefs.edit().putStringSet("fav", favoriteIds).apply()
    adapter.notifyDataSetChanged()
    if (currentSection == "favorites") loadFavorites()
  }

  private fun loadCategories(section: String) {
    when (section) {
      "favorites" -> {}
      "live" -> loadApiCategories("live", liveCategoriesLoaded, { liveCategories }, { liveCategories = it; liveCategoriesLoaded = true }, listOf("رياضة", "أخبار", "أطفال", "أفلام", "ترفيه", "وثائقي", "دينية", "موسيقى"))
      "movies" -> loadApiCategories("vod", vodCategoriesLoaded, { vodCategories }, { vodCategories = it; vodCategoriesLoaded = true }, listOf("2026", "2025", "2024", "عربي", "تركي", "أجنبي", "أنمي", "عائلي"))
      "series" -> loadApiCategories("series", seriesCategoriesLoaded, { seriesCategories }, { seriesCategories = it; seriesCategoriesLoaded = true }, listOf("عربي", "تركي", "أجنبي", "دراما", "كوميديا"))
    }
  }

  private fun hasArabicText(text: String): Boolean =
    text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' }

  private fun loadApiCategories(section: String, isLoaded: Boolean, getCats: () -> List<LiveCategory>, setCats: (List<LiveCategory>) -> Unit, fallback: List<String>) {
    if (isLoaded) {
      buildCategories(listOf("الكل") + getCats().map { it.categoryName })
      return
    }
    categoryRow.removeAllViews()
    val fetcher: ((List<LiveCategory>) -> Unit, ((String) -> Unit)?) -> Unit = when (section) {
      "live" -> { cb, err -> ApiClient.fetchLiveCategories(cb, err) }
      "vod" -> { cb, err -> ApiClient.fetchVodCategories(cb, err) }
      "series" -> { cb, err -> ApiClient.fetchSeriesCategories(cb, err) }
      else -> return
    }
    fetcher({ cats ->
      setCats(cats)
      runOnUiThread {
        buildCategories(listOf("الكل") + cats.map { it.categoryName })
      }
    }, { _ ->
      runOnUiThread {
        buildCategories(listOf("الكل") + fallback)
      }
    })
  }

  private fun buildCategories(cats: List<String>) {
    searchInput.setText("")
    val catsWithCount = if (allItems.isNotEmpty()) {
      cats.map { name ->
        val count = when (currentSection) {
          "live" -> {
            if (name == "الكل") allItems.size
            else allItems.count { it.categoryId == liveCategories.find { c -> c.categoryName == name }?.categoryId }
          }
          else -> if (name == "الكل") allItems.size else 0
        }
        SidebarCategory(name, count)
      }
    } else {
      cats.map { SidebarCategory(it, 0) }
    }
    sidebarAdapter.submitList(catsWithCount)
    if (isTvDevice()) {
      sidebarAdapter.setSelected(0)
      sidebarList?.visibility = View.VISIBLE
      populateSidebarChannels()
      grid.post {
        if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
      }
    } else {
      categoryItems = cats.mapIndexed { index, name ->
        MediaItem(id = "cat_$index", type = "category", name = name, image = "", rating = "0", categoryId = "")
      }
      showCategoryList()
    }
  }

  private fun populateSidebarChannels() {
    isShowingCategories = false
    selectedCategoryName = "الكل"
    if (allItems.isNotEmpty()) {
      currentList = sortArabicFirst(allItems)
      adapter.submitList(currentList)
      progressBar.visibility = View.GONE
      errorText.visibility = View.GONE
      grid.visibility = View.VISIBLE
    }
  }

  private fun showCategoryList() {
    isShowingCategories = true
    searchInput.setText("")
    if (isTvDevice()) {
      updateSectionTitle()
      setFocusTrap(false)
      return
    }
    selectedCategoryName = "الكل"
    currentList = categoryItems
    adapter.submitList(categoryItems)
    progressBar.visibility = View.GONE
    errorText.visibility = View.GONE
    grid.visibility = View.VISIBLE
  }

  private fun onCategorySelected(categoryName: String) {
    if (currentSection == "favorites") return
    if (!dataLoaded) {
      Toast.makeText(this, "جاري تحميل البيانات...", Toast.LENGTH_SHORT).show()
      return
    }
    if (categoryName == selectedCategoryName && !isShowingCategories) return
    selectedCategoryName = categoryName
    isShowingCategories = false
    val items = if (categoryName == "الكل") allItems
    else allItems.filter { matchesCategory(it, categoryName) }
    currentList = sortArabicFirst(items)
    adapter.submitList(currentList)
    progressBar.visibility = View.GONE
    errorText.visibility = View.GONE
    grid.visibility = View.VISIBLE
    if (isTvDevice()) {
      val sectionName = when (currentSection) {
        "live" -> getString(R.string.live)
        "movies" -> getString(R.string.movies)
        "series" -> getString(R.string.series)
        else -> getString(R.string.app_name)
      }
      tvSectionTitle.text = "$sectionName ← $categoryName"
      setFocusTrap(true)
      grid.post {
        if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
      }
    }
  }

  private fun highlightCategory(view: View) {
    for (i in 0 until categoryRow.childCount) {
      categoryRow.getChildAt(i).isSelected = false
    }
    view.isSelected = true
  }

  private fun sortArabicFirst(items: List<MediaItem>): List<MediaItem> {
    return items.sortedBy { item ->
      val c = item.name.firstOrNull() ?: ' '
      when {
        c in '\u0600'..'\u06FF' -> 0
        c == 'ع' -> 1
        item.name.startsWith("AR", ignoreCase = true) -> 2
        item.name.startsWith("ARB", ignoreCase = true) -> 2
        else -> 3
      }
    }
  }

  private fun filterItems(query: String) {
    if (isShowingCategories) return
    val items = if (currentSection == "favorites") {
      val arabic = allItems.filter { hasArabicText(it.name) }
      if (query.isBlank()) sortArabicFirst(arabic)
      else arabic.filter { it.name.contains(query, ignoreCase = true) }
    } else {
      val byCat = if (selectedCategoryName == "الكل") allItems
      else allItems.filter { matchesCategory(it, selectedCategoryName) }
      if (query.isBlank()) sortArabicFirst(byCat)
      else byCat.filter { it.name.contains(query, ignoreCase = true) }
    }

    currentList = items
    adapter.submitList(items)
    progressBar.visibility = View.GONE
    errorText.visibility = View.GONE
    grid.visibility = View.VISIBLE

    pendingRestoreSearch?.let { query ->
      if (searchInput.text.toString() != query) searchInput.setText(query)
      pendingRestoreSearch = null
    }

    pendingRestoreItemId?.let { id ->
      val type = pendingRestoreItemType ?: ""
      currentList.find { it.id == id && it.type == type }?.let { item ->
        pendingRestoreItemId = null
        pendingRestoreItemType = null
        playItem(item)
      }
    }
  }

  private val liveCategoryPatterns = mapOf(
    "رياضة" to listOf("sport", "bein", "ssc", "ufc", "wwe", "nba", "football", "match", "كأس", "رياض"),
    "أخبار" to listOf("news", "العربية", "الجزيرة", "cnn", "bbc", "france 24", "sky news", "alarabiya", "aljazeera", "اخبار"),
    "أطفال" to listOf("kids", "cartoon", "mbc 3", "spacetoon", "disney", "nick", "براعم", "طفال"),
    "أفلام" to listOf("movie", "cinema", "film", "mbc2", "action", "افلام"),
    "ترفيه" to listOf("mbc", "rotana", "osn", "show", "mix", "one", "comedy", "مسلسلات"),
    "وثائقي" to listOf("documentary", "nat geo", "discovery", "history", "animal", "وثائقي"),
    "دينية" to listOf("quran", "makkah", "madina", "islam", "قرآن", "مكة", "السنة", "دينية"),
    "موسيقى" to listOf("music", "mazika", "melody", "اغاني")
  )

  private val movieCategoryPatterns = mapOf(
    "عربي" to listOf("arab", "عربي"),
    "تركي" to listOf("turk", "تركي"),
    "أجنبي" to listOf("english", "usa", "uk"),
    "أنمي" to listOf("anime", "انمي"),
    "عائلي" to listOf("family", "عائلي")
  )

  private fun matchesCategory(item: MediaItem, category: String): Boolean {
    val catList = when {
      currentSection == "live" && liveCategoriesLoaded -> liveCategories
      currentSection == "movies" && vodCategoriesLoaded -> vodCategories
      currentSection == "series" && seriesCategoriesLoaded -> seriesCategories
      else -> null
    }
    if (catList != null) {
      val catId = catList.find { it.categoryName == category }?.categoryId ?: return false
      return item.categoryId == catId
    }
    val patterns = when (currentSection) {
      "live" -> liveCategoryPatterns
      "movies" -> movieCategoryPatterns
      else -> emptyMap()
    }
    val keywords = patterns[category]
    if (keywords != null) {
      val text = "${item.name} ${item.genre}"
      return keywords.any { text.contains(it, ignoreCase = true) }
    }
    return item.name.contains(category, ignoreCase = true) || item.genre.contains(category, ignoreCase = true)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    updateGridColumns()
    applyFullscreenState()
  }

  private fun updateGridColumns() {
    grid.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
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

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (!isTvDevice()) {
      if (event.action == KeyEvent.ACTION_DOWN && isWatching() && isFullscreen) {
        when (event.keyCode) {
          KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
            switchChannel(false)
            return true
          }
          KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
            switchChannel(true)
            return true
          }
          KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
            togglePlayPause()
            showPlayerControlsBriefly()
            return true
          }
        }
      }
      return super.dispatchKeyEvent(event)
    }

    if (event.action == KeyEvent.ACTION_DOWN) {
      var handled = true
      when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
          if (isPlayerFocused() && shouldHandleChannelKeys()) {
            showPlayerControlsBriefly()
            switchChannel(false)
          } else if (!isPlayerFocused()) {
            val focused = currentFocus
            if (focused != null && focused.parent is RecyclerView && (focused.parent as RecyclerView).id == R.id.sidebarList) {
              grid.post {
                grid.isFocusable = true
                if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
              }
            } else handled = false
          } else handled = false
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
          if (isPlayerFocused() && shouldHandleChannelKeys()) {
            showPlayerControlsBriefly()
            switchChannel(true)
          } else if (!isPlayerFocused()) {
            val focused = currentFocus
            if (focused != null && focused.parent is RecyclerView && (focused.parent as RecyclerView).id == R.id.grid) {
              sidebarList?.requestFocus()
            } else handled = false
          } else handled = false
        }
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
          if (isWatching()) {
            showPlayerControlsBriefly()
            switchChannel(false)
          } else handled = false
        }
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> {
          if (isWatching()) {
            showPlayerControlsBriefly()
            switchChannel(true)
          } else handled = false
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
          handled = false
          if (event.isLongPress && (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) {
            val focus = currentFocus
            if (focus != null) {
              val pos = grid.getChildAdapterPosition(focus)
              if (pos != RecyclerView.NO_POSITION && pos < adapter.itemCount) {
                val item = adapter.getItem(pos)
                toggleFavorite(item)
                Toast.makeText(this, if (item.id in favoriteIds) "★ أضيف للمفضلة" else "☆ أزيل من المفضلة", Toast.LENGTH_SHORT).show()
                handled = true
              }
            }
          }
          if (!handled && isWatching() && shouldHandlePlayerKeys()) {
            if (event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || isFullscreen || !isContentPanelVisible()) {
              togglePlayPause()
              showPlayerControlsBriefly()
              handled = true
            }
          }
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
          if (isWatching()) {
            if (isFullscreen) {
              showContentPanel(true)
              isFullscreen = false
              btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
            }
            val tabId = when (currentSection) {
              "movies" -> R.id.tabMovies
              "series" -> R.id.tabSeries
              "favorites" -> R.id.tabFavorites
              "m3u" -> R.id.tabM3u
              else -> R.id.tabLive
            }
            tabs.findViewById<View>(tabId)?.requestFocus()
          } else handled = false
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
          if (isWatching()) {
            val focused = currentFocus
            val isOnTab = focused != null && tabs.indexOfChild(focused) >= 0
            if (isOnTab || isFullscreen) {
              if (isFullscreen) {
                showContentPanel(true)
                isFullscreen = false
                btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
              }
              grid.post {
                grid.isFocusable = true
                if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
              }
            } else if (!isPlayerFocused()) {
              showPlayerControlsBriefly()
              btnPlayPause.requestFocus()
            } else handled = false
          } else handled = false
        }
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
          if (isWatching()) {
            if (!isContentPanelVisible() || isFullscreen) {
              showContentPanel(true)
              isFullscreen = false
              btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
            }
            val tabId = when (currentSection) {
              "movies" -> R.id.tabMovies
              "series" -> R.id.tabSeries
              "favorites" -> R.id.tabFavorites
              "m3u" -> R.id.tabM3u
              else -> R.id.tabLive
            }
            tabs.findViewById<View>(tabId)?.requestFocus()
          } else handled = false
        }
        else -> handled = false
      }
      if (handled) return true
    }
    return super.dispatchKeyEvent(event)
  }

  private fun handlePlayerKey(keyCode: Int): Boolean {
    return when (keyCode) {
      KeyEvent.KEYCODE_DPAD_LEFT -> {
        if (shouldHandleChannelKeys()) {
          switchChannel(false)
          true
        } else false
      }
      KeyEvent.KEYCODE_DPAD_RIGHT -> {
        if (shouldHandleChannelKeys()) {
          switchChannel(true)
          true
        } else false
      }
      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
        if (shouldHandlePlayerKeys()) {
          togglePlayPause()
          showPlayerControlsBriefly()
          true
        } else false
      }
      KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
        val focus = currentFocus
        if (focus != null) {
          val pos = grid.getChildAdapterPosition(focus)
          if (pos != RecyclerView.NO_POSITION && pos < adapter.itemCount) {
            val item = adapter.getItem(pos)
            toggleFavorite(item)
            showPlayerControlsBriefly()
            true
          } else false
        } else false
      }
      KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
        showPlayerControlsBriefly()
        btnPlayPause.requestFocus()
        true
      }
      else -> false
    }
  }

  private fun handleBackPress() {
    if (isTvDevice()) {
      if (isFullscreen) {
        showContentPanel(true)
        isFullscreen = false
        btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
        grid.post {
          grid.isFocusable = true
          grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
          if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
        }
        return
      }
      if (isContentPanelVisible()) {
        if (isShowingCategories) {
          // Back from categories to grid
          populateSidebarChannels()
          return
        }
        if (lastPlayedItem != null) {
          toggleFullscreen()
          return
        }
      }
      if (backPressedOnce) {
        handler.removeCallbacks(resetBackPressRunnable)
        finish()
      } else {
        backPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_again_exit), Toast.LENGTH_LONG).show()
        handler.postDelayed(resetBackPressRunnable, EXIT_CONFIRM_MS)
      }
      return
    }
    val panel = findViewById<View>(R.id.contentPanel)
    if (panel != null && panel.visibility == View.VISIBLE) {
      if (!isShowingCategories) {
        showCategoryList()
        return
      }
      showContentPanel(false)
      return
    }
    when {
      isFullscreen -> {
        toggleFullscreen()
      }
      backPressedOnce -> {
        handler.removeCallbacks(resetBackPressRunnable)
        finish()
      }
      else -> {
        backPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_again_exit), Toast.LENGTH_LONG).show()
        handler.postDelayed(resetBackPressRunnable, EXIT_CONFIRM_MS)
      }
    }
  }

  private fun toggleContentPanelWithRemote() {
    if (isTvDevice()) return
    val panel = findViewById<View>(R.id.contentPanel) ?: return
    val show = panel.visibility != View.VISIBLE
    showContentPanel(show)
    if (show) {
      Toast.makeText(this, getString(R.string.show_channels), Toast.LENGTH_SHORT).show()
      grid.post {
        if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
      }
    }
  }

  private fun isWatching(): Boolean {
    val hasPlayer = if (currentPlayer == "ijk") ijkPlayer != null else exoPlayer != null
    return hasPlayer && currentList.isNotEmpty()
  }

  private fun isLandscape(): Boolean =
    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

  private fun isContentPanelVisible(): Boolean {
    val panel = findViewById<View>(R.id.contentPanel) ?: return true
    return panel.visibility == View.VISIBLE
  }

  private fun isBrowsingContent(): Boolean {
    if (!isContentPanelVisible()) return false
    val focused = currentFocus ?: return false
    val panel = findViewById<View>(R.id.contentPanel) ?: return false
    return isDescendantOf(focused, panel)
  }

  private fun isDescendantOf(view: View, ancestor: View): Boolean {
    var parent = view.parent
    while (parent is View) {
      if (parent == ancestor) return true
      parent = parent.parent
    }
    return false
  }

  private fun isPlayerFocused(): Boolean {
    val focused = currentFocus ?: return false
    return focused == textureView || focused == playerPanel || focused == playerControls ||
      focused == btnPrev || focused == btnNext || focused == btnPlayPause || focused == btnFullscreen ||
      focused == btnSwitchPlayer || focused == btnSettings
  }

  private fun setFocusTrap(enabled: Boolean) {
    val f = !enabled
    searchInput.isFocusable = f
    searchInput.isFocusableInTouchMode = f
    for (i in 0 until tabs.childCount) {
      tabs.getChildAt(i).isFocusable = f
    }
    sidebarList?.isFocusable = f
    
    // When focus trap is enabled, hide player controls on TV
    if (enabled && isTvDevice()) {
      hidePlayerControls()
    }
  }

  private fun shouldHandleChannelKeys(): Boolean {
    if (!isWatching()) return false
    if (isFullscreen) return true
    if (!isContentPanelVisible()) return true
    return false
  }

  private fun isControlButtonFocused(): Boolean {
    val focused = currentFocus
    return focused == btnPrev || focused == btnNext || focused == btnPlayPause || focused == btnFullscreen
  }

  private fun shouldHandlePlayerKeys(): Boolean {
    if (!isWatching()) return false
    if (isFullscreen || !isContentPanelVisible()) return true
    return isPlayerFocused()
  }

  private fun showPlayerControls() {
    playerControls.visibility = View.VISIBLE
    playerControls.alpha = 1f
    mobileControlsVisible = true
  }

  private var autoHideTv = false

  private fun hidePlayerControls() {
    val isPlaying = if (currentPlayer == "ijk") ijkPlayer?.isPlaying == true else exoPlayer?.isPlaying == true
    if (!isTvDevice() && !isPlaying && !isFullscreen) return
    if (isTvDevice() && !isFullscreen && !autoHideTv) return
    autoHideTv = false
    mobileControlsVisible = false
    playerControls.animate().alpha(0f).setDuration(300).withEndAction {
      if (!mobileControlsVisible) playerControls.visibility = View.GONE
    }.start()
  }

  private fun toggleMobileControls() {
    val hasPlayer = if (currentPlayer == "ijk") ijkPlayer != null else exoPlayer != null
    if (!hasPlayer) return
    if (mobileControlsVisible && playerControls.visibility == View.VISIBLE) {
      hidePlayerControls()
    } else {
      showPlayerControlsBriefly()
    }
  }

  private fun showPlayerControlsBriefly() {
    showPlayerControls()
    if (isTvDevice()) autoHideTv = true
    handler.removeCallbacks(hideControlsRunnable)
    handler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS)
  }

  private fun handlePlaybackStall() {
    val item = lastPlayedItem ?: return

    if (item.type == "live" && item.directUrl.isEmpty() && item.id !in hlsFallbackItems) {
      hlsFallbackItems.add(item.id)
      android.util.Log.i("YasserTV", "TS failed, falling back to HLS for: ${item.name}")
      statusHandler.post {
        tvStatus.apply {
          text = "جاري التحميل..."
          setTextColor(android.graphics.Color.parseColor("#ffb000"))
          visibility = View.VISIBLE
        }
      }
      statusHandler.postDelayed({ playItem(item) }, 2000)
      return
    }

    if (reconnectCount < MAX_RECONNECT) {
      reconnectCount++
      val delayMs = NativeCore.getReconnectDelay(reconnectCount).toLong()
      statusHandler.post {
        tvStatus.apply {
          text = "إعادة الاتصال (محاولة $reconnectCount)..."
          setTextColor(android.graphics.Color.parseColor("#ffb000"))
          visibility = View.VISIBLE
        }
      }
      statusHandler.postDelayed({ playItem(item) }, delayMs)
    } else {
      reconnectCount = 0
      statusHandler.post {
        tvStatus.apply {
          text = "فشل الاتصال - الانتقال للقناة التالية..."
          setTextColor(android.graphics.Color.parseColor("#ff5a76"))
          visibility = View.VISIBLE
        }
      }
      statusHandler.postDelayed({ playNext() }, 1500)
    }
  }

  private fun showContentPanel(show: Boolean) {
    val panel = findViewById<View>(R.id.contentPanel) ?: return
    if (isTvDevice()) {
      if (show) {
        panel.visibility = View.VISIBLE
        panel.alpha = 1f
        playerControls.visibility = View.GONE
      } else {
        panel.visibility = View.GONE
        playerControls.visibility = View.VISIBLE
      }
      return
    }
    if (show) {
      panel.animate().cancel()
      if (panel.visibility != View.VISIBLE) {
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.animate().alpha(1f).setDuration(300).start()
        adjustPlayerHeight()
      } else {
        panel.alpha = 1f
      }
    } else {
      if (panel.visibility == View.VISIBLE) {
        panel.animate().alpha(0f).setDuration(300).withEndAction {
          panel.visibility = View.GONE
          adjustPlayerHeight()
        }.start()
      }
    }
  }

  private fun toggleFullscreen() {
    isFullscreen = !isFullscreen

    if (isFullscreen) {
      if (isTvDevice() && isPortrait()) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      }
      showContentPanel(false)
      btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit)
      hideSystemUI()
      showPlayerControls()
      playerControls.post { playerControls.requestFocus() }
    } else {
      if (isTvDevice() && isLandscape()) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
      showContentPanel(true)
      btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
      showSystemUI()
      grid.post {
        grid.isFocusable = true
        grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (grid.childCount > 0) grid.getChildAt(0)?.requestFocus()
      }
    }
    adjustPlayerHeight()
  }

  private fun hideSystemUI() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(false)
      window.insetsController?.let { controller ->
        controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else {
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = (
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          or View.SYSTEM_UI_FLAG_FULLSCREEN
      )
    }
  }

  private fun showSystemUI() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(true)
      window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
    } else {
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = (
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
      )
    }
  }

  private fun adjustPlayerHeight() {
    playerPanel.requestLayout()
  }

  private fun applyAspectRatio() {
    val surfaceTexture = textureView.surfaceTexture ?: return
    val viewWidth = textureView.width
    val viewHeight = textureView.height
    if (viewWidth == 0 || viewHeight == 0) return
    val videoWidth = if (currentPlayer == "ijk") ijkPlayer?.videoWidth ?: 0 else 0
    val videoHeight = if (currentPlayer == "ijk") ijkPlayer?.videoHeight ?: 0 else 0
    if (videoWidth <= 0 || videoHeight <= 0) return

    val matrix = android.graphics.Matrix()
    when (aspectRatio) {
      1 -> { // fill - stretch to fill
        val sx = viewWidth.toFloat() / videoWidth
        val sy = viewHeight.toFloat() / videoHeight
        matrix.setScale(sx, sy, viewWidth / 2f, viewHeight / 2f)
      }
      2 -> { // 16:9
        val targetW = viewWidth
        val targetH = (viewWidth * 9f / 16f).toInt()
        val sy = if (targetH > 0) viewHeight.toFloat() / targetH else 1f
        val sx = viewWidth.toFloat() / targetW
        matrix.setScale(sx * 0.9f, sy * 0.9f, viewWidth / 2f, viewHeight / 2f)
      }
      3 -> { // 4:3
        val targetW = viewWidth
        val targetH = (viewWidth * 3f / 4f).toInt()
        val sy = if (targetH > 0) viewHeight.toFloat() / targetH else 1f
        val sx = viewWidth.toFloat() / targetW
        matrix.setScale(sx * 0.9f, sy * 0.9f, viewWidth / 2f, viewHeight / 2f)
      }
      else -> { // fit - fit within bounds
        val sx = viewWidth.toFloat() / videoWidth
        val sy = viewHeight.toFloat() / videoHeight
        val min = minOf(sx, sy)
        matrix.setScale(min, min, viewWidth / 2f, viewHeight / 2f)
      }
    }
    textureView.setTransform(matrix)
  }

  private fun showSettingsDialog() {
    val ratioNames = arrayOf("ملء الشاشة", "تمديد", "16:9", "4:3")
    val bufferNames = arrayOf("صغير (10ث)", "وسط (30ث)", "كبير (60ث)", "كبير جداً (90ث)", "ضخم (120ث)")
    val bufferValues = intArrayOf(10000, 30000, 60000, 90000, 120000)
    val currentRatio = aspectRatio
    val currentBufIdx = bufferValues.indexOf(
      prefs.getInt("buffer_ms", if (currentPlayingLive) 90000 else 30000)
    ).coerceAtLeast(0)

    val items = arrayOf("نسبة الشاشة: ${ratioNames[currentRatio]}", "حجم البافر: ${bufferNames[currentBufIdx]}")

    AlertDialog.Builder(this)
      .setTitle("إعدادات المشغل")
      .setItems(items) { _, which ->
        when (which) {
          0 -> {
            val choice = android.app.AlertDialog.Builder(this)
              .setTitle("نسبة الشاشة")
              .setSingleChoiceItems(ratioNames, currentRatio) { d, i ->
                aspectRatio = i
                prefs.edit().putInt("aspect_ratio", i).apply()
                applyAspectRatio()
                d.dismiss()
              }
              .show()
          }
          1 -> {
            android.app.AlertDialog.Builder(this)
              .setTitle("حجم البافر (زمن التحميل)")
              .setSingleChoiceItems(bufferNames, currentBufIdx) { d, i ->
                val ms = bufferValues[i]
                prefs.edit().putInt("buffer_ms", ms).apply()
                android.widget.Toast.makeText(this, "سيطبق على القناة التالية", android.widget.Toast.LENGTH_SHORT).show()
                d.dismiss()
              }
              .show()
          }
        }
      }
      .setNegativeButton("إغلاق", null)
      .show()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus && isFullscreen) {
      hideSystemUI()
      showPlayerControlsBriefly()
    }
  }

  override fun onStop() {
    super.onStop()
    stopHeartbeat()
    releasePlaybackWakeLock()
    if (currentPlayer == "ijk") ijkPlayer?.pause() else exoPlayer?.pause()
    handler.removeCallbacks(playTimeoutRunnable)
  }

  override fun onStart() {
    super.onStart()
    if (currentPlayer == "ijk") {
      if (ijkPlayer?.isPlaying == false) ijkPlayer?.start()
    } else {
      if (exoPlayer?.isPlaying == false) exoPlayer?.play()
    }
  }

  override fun onResume() {
    super.onResume()
    startHeartbeat()
  }

  override fun onPause() {
    super.onPause()
    stopHeartbeat()
    releasePlaybackWakeLock()
  }

  private fun startHeartbeat() {
    heartbeatHandler.removeCallbacks(heartbeatRunnable)
    AuthApiClient.sendHeartbeat(this)
    heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
  }

  private fun stopHeartbeat() {
    heartbeatHandler.removeCallbacks(heartbeatRunnable)
  }

  override fun onDestroy() {
    stopHeartbeat()
    stopProgressUpdates()
    releasePlaybackWakeLock()
    handler.removeCallbacks(resetBackPressRunnable)
    handler.removeCallbacks(hideControlsRunnable)
    handler.removeCallbacks(playTimeoutRunnable)
    statusHandler.removeCallbacksAndMessages(null)
    releasePlayer()
    StreamPlayerFactory.releaseCache()
    super.onDestroy()
  }
}
