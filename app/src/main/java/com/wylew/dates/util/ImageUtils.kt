package com.wylew.dates.util

import android.content.Context
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.github.penfeizhou.animation.apng.APNGDrawable
import java.io.File

object ImageUtils {
    fun getAnimatedImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    fun createAnimatedImageRequest(context: Context, data: Any?): ImageRequest {
        val imageModel = if (data is String && data.startsWith("/")) File(data) else data
        val dataStr = data?.toString().orEmpty()
        val isAnimated = dataStr.endsWith(".gif", ignoreCase = true) || dataStr.endsWith(".apng", ignoreCase = true)

        val builder = ImageRequest.Builder(context)
            .data(imageModel)
            .crossfade(true)

        if (isAnimated) {
            builder.decoderFactory(
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoderDecoder.Factory()
                } else {
                    GifDecoder.Factory()
                }
            )
        }
        return builder.build()
    }
}

@Composable
fun FluentEmojiView(
    imageUri: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val file = if (imageUri.startsWith("/")) File(imageUri) else null

    when {
        imageUri.endsWith(".json", ignoreCase = true) -> {
            val spec = if (file != null && file.exists()) {
                LottieCompositionSpec.File(file.absolutePath)
            } else {
                LottieCompositionSpec.Url(imageUri)
            }
            val composition by rememberLottieComposition(spec)
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = modifier
            )
        }

        imageUri.endsWith(".png", ignoreCase = true) || imageUri.endsWith(".apng", ignoreCase = true) -> {
            val apngDrawable = remember(imageUri) {
                try {
                    if (file != null && file.exists()) {
                        APNGDrawable.fromFile(file.absolutePath)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (apngDrawable != null) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(apngDrawable)
                        }
                    },
                    update = { imageView ->
                        imageView.setImageDrawable(apngDrawable)
                    },
                    modifier = modifier
                )
            } else {
                val imageModel = file ?: imageUri
                AsyncImage(
                    model = ImageUtils.createAnimatedImageRequest(LocalContext.current, imageModel),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = modifier
                )
            }
        }

        else -> {
            val imageModel = file ?: imageUri
            AsyncImage(
                model = ImageUtils.createAnimatedImageRequest(LocalContext.current, imageModel),
                contentDescription = null,
                contentScale = contentScale,
                modifier = modifier
            )
        }
    }
}

@Composable
fun CustomFontEmojiView(
    emoji: String,
    fontFile: File,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 22f
) {
    val textColor = if (androidx.compose.foundation.isSystemInDarkTheme()) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY

    AndroidView(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                text = emoji
                textSize = fontSizeSp
                gravity = android.view.Gravity.CENTER
                setTextColor(textColor)
                if (fontFile.exists() && fontFile.length() > 0L) {
                    try {
                        typeface = android.graphics.Typeface.createFromFile(fontFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        },
        update = { tv ->
            tv.text = emoji
            tv.textSize = fontSizeSp
            tv.setTextColor(textColor)
            if (fontFile.exists() && fontFile.length() > 0L) {
                try {
                    tv.typeface = android.graphics.Typeface.createFromFile(fontFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        modifier = modifier
    )
}

