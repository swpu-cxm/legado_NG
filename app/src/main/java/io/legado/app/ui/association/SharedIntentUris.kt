package io.legado.app.ui.association

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Resolves a content [Uri] from VIEW / SEND / SEND_MULTIPLE intents (e.g. system share sheet).
 */
internal fun Intent.associationUri(): Uri? {
    return associationUris().firstOrNull()
}

internal fun Intent.associationUris(): List<Uri> {
    when (action) {
        Intent.ACTION_SEND -> {
            streamUri()?.let { return listOf(it) }
            data?.let { return listOf(it) }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val streams = streamUris()
            if (streams.isNotEmpty()) {
                return streams
            }
        }
        Intent.ACTION_VIEW -> {
            data?.let { return listOf(it) }
        }
    }
    return emptyList()
}

private fun Intent.streamUri(): Uri? {
    clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.let { return it }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
}

private fun Intent.streamUris(): List<Uri> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let {
            if (it.isNotEmpty()) return it
        }
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let {
            if (it.isNotEmpty()) return it
        }
    }
    return clipData?.let { clip ->
        buildList {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { add(it) }
            }
        }
    }.orEmpty()
}
