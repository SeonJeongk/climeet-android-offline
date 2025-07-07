package com.climus.climeet.presentation.ui

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DataExtensionsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun validBitmap_toMultipart_preservesFieldNamesAndJpegBody() {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "multipart-test-").toFile()
        val fileContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = directory
        }
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        try {
            for (field in listOf("file", "image")) {
                val expectedColor = if (field == "file") Color.BLACK else Color.WHITE
                bitmap.eraseColor(expectedColor)
                val part = if (field ==
                    "file"
                ) {
                    bitmap.toMultiPart(fileContext)
                } else {
                    bitmap.toMultiPartImage(fileContext)
                }
                assertNotNull(part)
                val multipart = checkNotNull(part)
                assertEquals(
                    "form-data; name=\"$field\"; filename=\"image.jpg\"",
                    multipart.headers?.get("Content-Disposition"),
                )
                assertEquals("image/jpg", multipart.body.contentType().toString())
                val buffer = Buffer()
                multipart.body.writeTo(buffer)
                val bytes = buffer.readByteArray()
                assertTrue(bytes.size > 2)
                assertEquals(0xff, bytes[0].toInt() and 0xff)
                assertEquals(0xd8, bytes[1].toInt() and 0xff)
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                assertNotNull(decoded)
                val image = checkNotNull(decoded)
                try {
                    assertEquals(bitmap.width, image.width)
                    assertEquals(bitmap.height, image.height)
                    assertTrue(kotlin.math.abs(Color.red(image.getPixel(0, 0)) - Color.red(expectedColor)) <= 2)
                } finally {
                    image.recycle()
                }
                // 앞선 호출의 파일을 다음 호출이 재사용해도 통과하는 것을 막습니다.
                assertTrue(File(directory, "image.jpg").delete())
            }
        } finally {
            bitmap.recycle()
            File(directory, "image.jpg").delete()
            directory.delete()
        }
    }

    @Test
    fun unwritablePath_toMultipart_returnsNull() {
        val path = File.createTempFile("multipart-test-", ".tmp", context.cacheDir)
        val fileContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = path
        }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            assertNull(bitmap.toMultiPart(fileContext))
            assertNull(bitmap.toMultiPartImage(fileContext))
        } finally {
            bitmap.recycle()
            path.delete()
        }
    }

    @Test
    fun missingVideo_toVideoThumbnail_returnsNull() {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "thumbnail-test-").toFile()
        try {
            assertNull(Uri.fromFile(File(directory, "missing.mp4")).toVideoThumbnail(context))
        } finally {
            directory.delete()
        }
    }
}
