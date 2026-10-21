package coil.compose

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null
) {
    val context = LocalContext.current
    val loader = remember(context) { context.imageLoader }

    val request = remember(model, context) {
        when (model) {
            is ImageRequest -> model
            is String -> ImageRequest.Builder(context).data(model).build()
            else -> ImageRequest.Builder(context).data(model?.toString() ?: "").build()
        }
    }

    val cacheKey = request.cacheKey
    val initialBitmap = remember(cacheKey) {
        loader.getFromMemory(cacheKey)
    }

    var loadedBitmap by remember(cacheKey) { mutableStateOf(initialBitmap) }

    LaunchedEffect(cacheKey) {
        if (loadedBitmap == null && request.urlString.isNotBlank()) {
            val res = loader.execute(request)
            if (res is SuccessResult) {
                val bmp = (res.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    loadedBitmap = bmp
                }
            }
        }
    }

    val activeBitmap = loadedBitmap
    if (activeBitmap != null && !activeBitmap.isRecycled) {
        val imageBitmap = remember(activeBitmap) { activeBitmap.asImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter
        )
    } else {
        Box(modifier = modifier)
    }
}
