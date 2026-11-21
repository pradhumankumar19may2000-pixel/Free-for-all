package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.R
import com.example.domain.model.DynamicArtworkTheme
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtworkPaletteExtractor {

    suspend fun extractTheme(context: Context, track: Track?): DynamicArtworkTheme = withContext(Dispatchers.IO) {
        if (track == null) return@withContext DynamicArtworkTheme()

        val bitmap = loadTrackBitmap(context, track) ?: return@withContext DynamicArtworkTheme()
        val palette = Palette.from(bitmap).generate()

        val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) }
            ?: palette.lightVibrantSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFFA855F7)

        val secondary = palette.lightVibrantSwatch?.rgb?.let { Color(it) }
            ?: palette.mutedSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFF06F6D4)

        val darkBg = palette.darkVibrantSwatch?.rgb?.let { Color(it) }
            ?: palette.darkMutedSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFF1A0F33)

        DynamicArtworkTheme(
            primaryGlow = vibrant,
            secondaryGlow = secondary,
            backgroundTop = darkBg.copy(alpha = 0.92f),
            backgroundBottom = Color(0xFF05050B),
            glassTint = vibrant.copy(alpha = 0.22f),
            lyricHighlight = secondary
        )
    }

    private suspend fun loadTrackBitmap(context: Context, track: Track): Bitmap? {
        if (!track.artworkUri.isNullOrBlank()) {
            val loaded = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(Uri.parse(track.artworkUri))
                    .allowHardware(false)
                    .size(160)
                    .build()
                val result = context.imageLoader.execute(request)
                (result as? SuccessResult)?.drawable?.let { drawable ->
                    val bmp = Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(64),
                        drawable.intrinsicHeight.coerceAtLeast(64),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }.getOrNull()
            if (loaded != null) return loaded
        }

        val resId = when (track.artworkResName) {
            "img_hero_cosmos" -> R.drawable.img_hero_cosmos
            "img_album_acoustic_mist" -> R.drawable.img_album_acoustic_mist
            "img_ai_dj_avatar" -> R.drawable.img_ai_dj_avatar
            else -> R.drawable.img_album_neon_pulse
        }
        return BitmapFactory.decodeResource(context.resources, resId)
    }
}
