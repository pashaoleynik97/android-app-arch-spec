package {{ROOT_PACKAGE}}.libandroid.ui.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * Full-screen preview at the **reference device** — the smallest screen the design targets
 * (`REFERENCE_DEVICE_DP` = {{REFERENCE_DEVICE_DP}}). Previews at a large default size hide exactly the
 * overflow and wrapping the smallest device shows. Wrap the previewed content in the theme.
 */
@Preview(name = "Reference device", showBackground = true, widthDp = {{REFERENCE_WIDTH_DP}}, heightDp = {{REFERENCE_HEIGHT_DP}})
annotation class ReferenceDevicePreview

/** Component preview at the reference device's width; the height wraps the content. */
@Preview(name = "Reference width", showBackground = true, widthDp = {{REFERENCE_WIDTH_DP}})
annotation class ReferenceWidthPreview
