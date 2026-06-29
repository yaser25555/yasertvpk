package com.yassertv

import com.yassertv.data.MediaItem

object M3UParser {
  fun parse(content: String, source: String = "m3u"): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val lines = content.lines()
    var i = 0
    while (i < lines.size) {
      val line = lines[i].trim()
      if (line.startsWith("#EXTINF:")) {
        val attrs = parseExtInf(line)
        i++
        if (i < lines.size) {
          val url = lines[i].trim()
          if (url.isNotEmpty() && !url.startsWith("#")) {
            items.add(
              MediaItem(
                id = "${source}_${attrs["tvg-id"] ?: items.size}",
                type = "live",
                name = attrs["name"] ?: "Unknown",
                image = attrs["tvg-logo"] ?: "",
                rating = "0",
                categoryId = attrs["group-title"] ?: "General",
                genre = attrs["group-title"] ?: "",
                directUrl = url
              )
            )
          }
        }
      }
      i++
    }
    return items
  }

  private fun parseExtInf(line: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val nameMatch = Regex(",(.+)$").find(line)
    if (nameMatch != null) map["name"] = nameMatch.groupValues[1].trim()

    val attrRegex = Regex("""(\w[\w-]*)\s*=\s*"([^"]*?)"""")
    for (match in attrRegex.findAll(line)) {
      map[match.groupValues[1].lowercase()] = match.groupValues[2]
    }
    return map
  }
}
