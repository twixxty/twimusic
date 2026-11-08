package app.vitune.android.utils

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import app.vitune.android.service.PlayerService

@Composable
fun PlayerService.Binder?.collectProvidedBitmapAsState(
    key: Any = Unit
): Bitmap? {
    val stateFlow = this?.bitmapState ?: return null
    return stateFlow.collectAsState().value
}
