package com.chaidarun.chronofile

import com.google.gson.Gson
import java.io.File

class History {

  val entries = mutableListOf<Entry>()
  val gson = Gson()
  var currentActivityStartTime = getEpochSeconds()
    private set
  private val mFile = File("/storage/emulated/0/Sync/chronofile.jsonl")

  init {
    loadHistoryFromFile()
  }

  fun addEntry(activity: String) {
    entries += Entry(currentActivityStartTime, activity)
    normalizeEntries()
    currentActivityStartTime = getEpochSeconds()
    saveHistoryToDisk()
  }

  fun getFuzzyTimeSinceLastEntry(): String {
    val elapsedSeconds = getEpochSeconds() - currentActivityStartTime
    val elapsedMinutes = elapsedSeconds / 60
    val elapsedHours = elapsedMinutes / 60
    return when {
      elapsedHours > 0 -> "$elapsedHours hours"
      elapsedMinutes > 0 -> "$elapsedMinutes minutes"
      else -> "$elapsedSeconds seconds"
    }
  }

  private fun loadHistoryFromFile() {
    currentActivityStartTime = getEpochSeconds()
    if (!mFile.exists()) {
      mFile.writeText(gson.toJson(PlaceholderEntry(currentActivityStartTime)))
    }
    entries.clear()
    mFile.readLines().forEach {
      if (',' in it) {
        entries += gson.fromJson(it, Entry::class.java)
      } else if (it.trim().isNotEmpty()) {
        currentActivityStartTime = gson.fromJson(it, PlaceholderEntry::class.java).startTime
      }
    }
    normalizeEntries()
    saveHistoryToDisk()
  }

  private fun saveHistoryToDisk() {
    val lines = mutableListOf<String>()
    entries.forEach { lines += gson.toJson(it) }
    lines += gson.toJson(PlaceholderEntry(currentActivityStartTime))
    mFile.writeText(lines.joinToString("") { "$it\n" })
  }

  private fun normalizeEntries() {
    entries.sortBy { it.startTime }
    var lastSeenActivity: String? = null
    entries.removeAll {
      val shouldRemove = it.activity == lastSeenActivity
      lastSeenActivity = it.activity
      shouldRemove
    }
  }

  private fun getEpochSeconds() = System.currentTimeMillis() / 1000
}
