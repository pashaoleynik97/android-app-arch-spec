# Images UI — remote images and avatars (component: images)

Composables in `:images` (package `{{ROOT_PACKAGE}}.images.ui`); the loader and URL rules are in the
application-architecture skill's `images.md`. Spec: `.arch-spec/spec/specs/recommended/images.md` (IMG-08).

## Pieces

| API | Use |
|---|---|
| `{{THEME_PREFIX}}RemoteImage(url, contentDescription, modifier, contentScale = Crop, placeholder = …)` | any server image; `placeholder` shows while loading, on error and for a `null` url |
| `{{THEME_PREFIX}}Avatar(imageUrl, name, modifier, size = 48.dp)` | round avatar; initials on a `primaryContainer` circle as the placeholder |
| `Initials.of(name)` | the pure initials rule (first + last word, upper-cased, code-point safe) |

Both fetch through Coil's singleton loader, which `:images` makes the authenticated one — a plain
`coil3.compose.AsyncImage(model = url)` in a feature gets the same credentials and cache.

## Rules

**IMG-08** — Loading, failure and `null` URL look the same (the placeholder); avatars fall back to initials. *Why:* a
failed image is not actionable; a stable placeholder avoids layout jumps.
<!-- origin: arch-spec IMG-08 -->

- Feed URLs from the VM's state (resolved by `FileUrlResolver` in the domain); never build URLs in a composable.
- Size images explicitly (`Modifier.size(…)` from tokens, or an aspect ratio) so the placeholder and the image occupy
  the same box.
- `contentDescription`: the person's name for an avatar; `null` for decorative images.
- Previews: use `imageUrl = null` — previews have no network; the placeholder is what they can show.

```kotlin
{{THEME_PREFIX}}Avatar(imageUrl = uiState.avatarUrl, name = uiState.userName, size = {{THEME_PREFIX}}Theme.dimens.touchTarget)
{{THEME_PREFIX}}RemoteImage(url = item.photoUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
```
