package app.vitune.android.utils

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.vitune.android.service.PlayerService

@Composable
fun PlayerService.Binder?.collectProvidedBitmapAsState(
    key: Any = Unit
): Bitmap? {
    var state by remember(this, key) { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(this, key) {
        val listener: (Bitmap?) -> Unit = {
            state = it
        }
        this@collectProvidedBitmapAsState?.addListener(listener)
        onDispose {
            this@collectProvidedBitmapAsState?.removeListener(listener)
        }
    }

    return state
}
