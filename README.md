# simple-weather
A simple weather app.

## Project Overview

A minimal Android weather app (Kotlin + Jetpack Compose + Material3) whose primary value is a home screen widget displaying the current temperature. Open-Meteo is used for weather and geocoding (no API key required).

## Releases

**Always tag after the PR merges on GitHub.** GitHub signs merge commits automatically, so tagging the merged commit produces a "Verified" release without any local GPG/SSH setup.

```bash
# After merging on GitHub:
git checkout main && git pull

# Stable release
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0

# Pre-release (alpha/beta — triggers release workflow, marked as pre-release)
git tag -a v1.0.0-alpha -m "Pre-release v1.0.0-alpha"
git push origin v1.0.0-alpha
```

Pushing any `v*` tag triggers `.github/workflows/release.yml`, which builds, signs, and publishes the APK. Tags containing a hyphen (e.g. `-alpha`, `-beta`) are automatically marked as pre-releases.

## Wishlist (deferred features)

- Tapping widget can launch the user's choice of weather app
- Widget: condition icon, high/low temperatures, font picker
- v2: Room + Hilt migration, temperature trend sparkline widget (12/24hr graph)
