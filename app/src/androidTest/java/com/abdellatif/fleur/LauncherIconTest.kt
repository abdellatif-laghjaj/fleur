package com.abdellatif.fleur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test
    fun adaptiveFlowerIconRendersAtLauncherSizes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        if (Build.VERSION.SDK_INT >= 33) assertNotNull((icon as AdaptiveIconDrawable).monochrome)
        for (size in listOf(48, 192, 512)) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bitmap))
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            val reds = pixels.count {
                Color.alpha(it) > 100 &&
                    Color.red(it) > 120 &&
                    Color.red(it) > Color.green(it) * 1.5 &&
                    Color.red(it) > Color.blue(it) * 1.5
            }
            assertTrue("Flower should remain visible at $size pixels", reds > size * size / 10)
            if (size == 512) {
                val directory = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
                File(directory, "22-launcher-icon.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            bitmap.recycle()
        }
    }
}
