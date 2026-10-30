package io.legado.app.ui.association

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class SharedIntentUrisTest {

    @Test
    fun viewIntentUsesDataUri() {
        val uri = Uri.parse("content://com.example/files/book.json")
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
        assertEquals(uri, intent.associationUri())
    }

    @Test
    fun sendIntentUsesExtraStream() {
        val uri = Uri.parse("content://com.example/files/source.json")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(uri, intent.associationUri())
    }

    @Test
    fun sendIntentWithoutStreamReturnsNull() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://example.com")
        assertNull(intent.associationUri())
    }

    @Test
    fun sendMultipleUsesFirstStream() {
        val first = Uri.parse("content://com.example/a.json")
        val second = Uri.parse("content://com.example/b.json")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("application/json")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        assertEquals(first, intent.associationUri())
        assertEquals(2, intent.associationUris().size)
    }
}
