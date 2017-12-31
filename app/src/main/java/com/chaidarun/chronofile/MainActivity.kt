package com.chaidarun.chronofile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.toast
import java.util.*


class MainActivity : BaseActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Set up UI
    setContentView(R.layout.activity_main)
    setSupportActionBar(toolbar)
    title = "Timeline"

    // Ensure required permissions are granted
    if (APP_PERMISSIONS.all {
      ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }) {
      init()
    } else {
      ActivityCompat.requestPermissions(this, APP_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
    R.id.action_refresh -> {
      hydrateStoreFromFiles()
      toast("Reloaded history and config from disk")
      true
    }
    R.id.action_stats -> {
      startActivity(Intent(this, PieActivity::class.java))
      true
    }
    else -> super.onOptionsItemSelected(item)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    when (requestCode) {
      PERMISSION_REQUEST_CODE -> {
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
          init()
        } else {
          toast("Permission denied :(")
        }
      }
    }
  }

  private fun hydrateStoreFromFiles() {
    Store.dispatch(Action.SetConfig(Config.fromFile()))
    Store.dispatch(Action.SetHistory(History.fromFile()))
  }

  private fun init() {
    hydrateStoreFromFiles()

    // Hook up list view
    historyList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
    historyList.adapter = HistoryListAdapter(this)

    // Set up listeners
    disposables = listOf(
      RxView.clicks(addEntry)
        .subscribe {
          History.addEntry(addEntryActivity.text.toString(), addEntryNote.text.toString())
          addEntryActivity.text.clear()
          addEntryNote.text.clear()
          currentFocus?.clearFocus()
        },
      RxTextView.afterTextChangeEvents(addEntryActivity)
        .subscribe { addEntry.isEnabled = !addEntryActivity.text.toString().isBlank() },
      Store.state.subscribe { state ->
        addEntry.text = TIME_FORMAT.format(
          Date(state.history!!.currentActivityStartTime * 1000))
      }
    )
  }

  companion object {
    val APP_PERMISSIONS = arrayOf(
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.READ_EXTERNAL_STORAGE,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    val PERMISSION_REQUEST_CODE = 1
  }
}
