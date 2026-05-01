# Subtitle Blocker

Overlay to mask hard-coded subtitles on videos and social media.

A moveable, resizable, semi-transparent rectangle that floats on top of any
other app. Drag it over the part of the screen where burned-in subtitles
appear so you don't have to look at them. When you want it out of the way,
collapse it to a small side tab with a single tap.

## Requirements

- Android 8.0 (Oreo / API 26) or later.
- "Draw over other apps" permission (requested on first run).

## Usage

- **Show/hide**: open the app and tap *Show Overlay*, or add the *Sub
  Blocker* tile to your Quick Settings panel (swipe down, tap the edit
  pencil, drag the tile in) and toggle it from there.
- **Move**: drag anywhere on the overlay.
- **Resize**: drag any corner.
- **Collapse**: tap the `»` icon in the middle of the overlay to shrink it
  to a small tab on the right edge of the screen. Tap the tab to expand
  again.
- **Settings**: triple-tap the overlay, or open the app and tap *Settings*.
  Configure colour, opacity, edge feathering, handle colour, which edge
  the tab collapses to, and whether resize grows from a corner or
  symmetrically around the centre.
- **Stop**: tap *Stop Overlay* in Settings, the *Hide Overlay* button on
  the main screen, or the Quick Settings tile.

## Building

Standard Android Gradle project. From the repository root:

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Licence

GPL-3.0-only. See [LICENSE](LICENSE).
