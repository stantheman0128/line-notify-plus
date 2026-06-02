package com.stanslab.linenotify

import android.content.Context
import android.content.Intent
import android.net.Uri

fun Context.openExternalUri(uri: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
}

fun Context.shareLineNotifyPlus() {
    val playStoreUrl = getString(R.string.play_store_url_placeholder)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message, playStoreUrl))
    }
    startActivity(Intent.createChooser(sendIntent, getString(R.string.share_chooser_title)))
}
