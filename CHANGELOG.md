# Changelog

All notable changes to TwiMusic since the branding update.

## [1.2.7] - 2026-11-10

### Added
- **New Branding & App Icon**: Replaced default app icon, Google Play artwork, status bar/lockscreen notification icon, and Android TV banner with new modern vector logo on AMOLED black background.
- **Themed Icons (Monet)**: Added native Android 13+ monochrome adaptive icon support (`ic_launcher_monochrome`) that adapts to wallpaper accent colors.
- **Changeable App Icons**: Added setting in **Settings → Appearance → App Icon** allowing users to switch between the new logo, the classic ViTune logo, and the new **Headphones** icon (with native themed icon support) dynamically via `<activity-alias>` without app restarts.

### Fixed
- **Lyrics Tap-to-Seek Blur Bug**: Fixed an issue where tapping an inactive lyric line to seek left the active line blurred until the line changed, by ensuring existing blur animators are cancelled immediately on user seek and active lines immediately snap to full focus.
