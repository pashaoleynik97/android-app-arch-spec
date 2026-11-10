package {{ROOT_PACKAGE}}.images.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme

/**
 * A server image, fetched through the app's authenticated loader (Coil's singleton, see `ImageLoaderInitializer`).
 *
 * [placeholder] is drawn while loading, on failure, and when [url] is `null` — the three cases look the same on
 * purpose: an image that cannot load is not an error the user can act on. Defaults to a neutral surface.
 */
@Composable
fun {{THEME_PREFIX}}RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { DefaultPlaceholder() },
) {
    if (url == null) {
        Box(modifier) { placeholder() }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { placeholder() },
        error = { placeholder() },
    )
}

@Composable
private fun DefaultPlaceholder() {
    Box(Modifier.fillMaxSize().background({{THEME_PREFIX}}Theme.colors.surfaceVariant))
}
