# Changelog

All notable changes to TwiMusic since the branding update.

## [Unreleased]

### Fixed
- **Blur & Lava Lamp backgrounds on Android 11 and below**: `Modifier.blur` is a silent no-op before Android 12, so both backgrounds rendered as a plain un-blurred (or empty) image on older devices. They no longer use a live blur at all (see below), so they now look identical on every Android version.
- **Blurred inactive lyric lines on Android 11 and below**: line blur relied on `RenderEffect` (Android 12+ only). On older versions each blurred line is now rendered once into a tiny bitmap, blurred on the CPU and drawn from there, with its fade/scale animations intact. Android 12+ keeps the GPU blur path unchanged.

### Changed
- **Pre-rendered blurred backgrounds**: the artwork is blurred once per track, off the main thread, on a ≤128px copy, then cached and simply drawn. The Lava Lamp bakes its wash and rotating quarters the same way, so per frame the GPU only rotates textures - no full-screen live blur. New artwork now fades in over the old one instead of popping.
- The background blur strength no longer changes when lyrics are toggled (the darkening overlay still does), so toggling lyrics never triggers a re-blur.

### Performance
- Animating the player's dark overlay no longer recomposes the whole player on every frame (the value is now read in the draw phase).
- Lyrics frame loop: the active-line lookup used to scan every line and allocate new sets on every frame (60-120x/s). It's now a binary search into a precomputed timeline with the result cached per interval.
- Lyric line blur on Android 11 and below snaps instead of re-blurring on every animation frame.

## [1.2.7] - 2026-11-10

### Added
- **New Branding & App Icon**: Replaced default app icon, Google Play artwork, status bar/lockscreen notification icon, and Android TV banner with new modern vector logo on AMOLED black background.
- **Themed Icons (Monet)**: Added native Android 13+ monochrome adaptive icon support (`ic_launcher_monochrome`) that adapts to wallpaper accent colors.
- **Changeable App Icons**: Added setting in **Settings → Appearance → App Icon** allowing users to switch between the new logo, the classic ViTune logo, and the new **Headphones** icon (with native themed icon support) dynamically via `<activity-alias>` without app restarts.

### Changed
- **Startup Update Checks**: Removed background WorkManager update worker and periodic notifications in favor of checking for updates on app startup by default, with an in-app out-of-date dialog featuring direct download/install, release notes, and progress reporting.

### Fixed
- **Lyrics Tap-to-Seek Blur Bug**: Fixed an issue where tapping an inactive lyric line to seek left the active line blurred until the line changed, by ensuring existing blur animators are cancelled immediately on user seek and active lines immediately snap to full focus.
