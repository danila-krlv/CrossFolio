package com.crossfolio.android.ui.portfolio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.crossfolio.common.core.network.NetworkResult
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
internal fun AssetLogo(
    url: String?,
    loadImage: (String, (NetworkResult<ByteArray>) -> Unit) -> Unit,
) {
    val image by produceState<ImageBitmap?>(null, url) {
        value = null
        val address = url ?: return@produceState
        val bytes = suspendCancellableCoroutine<ByteArray?> { continuation ->
            loadImage(address) { result ->
                if (continuation.isActive) continuation.resume(result.value)
            }
        } ?: return@produceState
        value = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text("◉", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
