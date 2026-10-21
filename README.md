# PUBG Mortar Auto Photo — Android v0.2 (prototype)

> **Target experience: open APK → phone camera opens → take ONE photo of the PC PUBG tactical map → show result or honest recognition failure.**
>
> This is a **source project**, not a tested APK. It has no user-facing map selector, elevation selector, width field, point picker or manual calibration. Unlike v0.1, it does not ask a player to prepare reference map files.
>
> **IMPORTANT: fully automatic marker recognition is an unvalidated color/shape heuristic, not a trained PUBG marker recognizer. This project is not yet a reliable one-photo production calculator.**

## What changed from the previous APK

- On opening the app, it automatically launches the Android camera app.
- A single picture is all the user submits; there are no import-map/import-height/configuration controls.
- OpenCV attempts to match the picture with every map bundled in the APK, and estimates screenshot magnification and camera perspective (homography).
- Colored waypoint marker and white triangle player icon detection runs automatically on the registered photo. **If ambiguous, the app shows a failure, not made-up coordinates.**
- If the map and both markers are detected, outputs horizontal distance and bearing automatically.
- If that map also includes historical 16-bit height data, shows **unverified** height difference and an **experimental** ballistic correction. Otherwise explicitly reports elevation unavailable.
- All map assets download **during APK build in GitHub Actions** and are stored inside the APK (no user setup, no network needed when using the app).

## Requirements in the GAME screenshot

1. PC PUBG tactical map must be visible in the picture (not only the 3D battlefield).
2. Player location and target waypoint should both be visible; otherwise a single static image does not contain the necessary data.
3. Map terrain needs enough recognizable features; excessively cropped/blurred/zoomed/repetitive views may fail alignment.
4. Camera screen perspective, glare, game colorblind mode, team marker colors and PUBG updates may make marker detector fail or choose a wrong marker.

## How to get an APK

1. Replace the old GitHub repository contents with **the contents of this source ZIP**. Keep `.github/workflows/build-apk.yml` and `tools/`.
2. Keep only this workflow if the older one is still running. If your default branch is not `main`, change the trigger.
3. In **GitHub → Actions → Build PUBG Auto-Photo APK → Run workflow**, run the build.
4. The workflow downloads tactical PNGs from the official developer repository and, where available, historical height images from the community source. It converts them into offline Android assets and builds `:app:assembleDebug` using JDK17 / Gradle8.9 / Android SDK35.
5. If the build succeeds, get `app-debug.apk` from the `PUBG-Mortar-AutoPhoto-APK` artifact, install it, and take a photo. The app does not ask for map files.

**Note**: GitHub Actions must have network access to GitHub asset hosts. A failed asset download means the build will fail (no fake demo maps silently shipped). Elevation download failure means that specific map remains usable for distance only, not elevation.

## Accuracy caveats

- The 8km or 4km map sizes are **nominal grid extents**, not survey-grade image georeferencing. Padding in map images and game updates can shift positions. Verify against in-game 100m grid.
- Height reference used here is `cgcostume/pubg-maps` **PUBG PC v5.7.6** where available, not recent 2026 content. See <https://github.com/cgcostume/pubg-maps/releases>. Scale `raw / 128` meters is only an **illustrative UE terrain assumption** and has **NOT** been calibrated against authoritative PUBG heights or aligned with the current API map imagery. A displayed height difference may therefore be wrong, including sign/magnitude.
- Terrain height maps don't include building rooftops and cannot tell whether you are standing upstairs.
- `MortarMath.correctedSetting` is an **unverified, nonofficial** approximation. It is not a substitute for measurements of actual game projectile trajectory.
- Marker detector is an initial OpenCV image-overlay heuristic based on a white player-arrow candidate and a colored target-marker candidate. **It is not an ML recognizer; it will often fail, and may still be wrong when it returns a result.** The app flags this prominently.
- Android APK build and physical smartphone camera image recognition **could not be tested** in the creation environment because Android SDK/Gradle and live PUBG screenshots are unavailable. Only the build-time asset conversion logic (with mock downloads), YAML parsing and Java math compilation were checked.

## Developer implementation notes

- `tools/prepare_offline_assets.py` queries the official `pubg/api-assets` GitHub directory for available map filenames and downloads map images. It chooses from low- and high-res versions, downscales to 2000px JPEG, emits `app/src/main/assets/maps/catalog.json`.
- Optionally tries to fetch historical 16-bit PNGs from cgcostume's `v5.7.6` tag if the map is supported. If absent / not uint16, no height resource is emitted.
- At runtime `MapCatalog` loads the prepackaged resources, `MapRecognizer` uses ORB + RANSAC to select a map, and `MarkerDetector` uses a warped reference map to detect visually distinct markers.
- `MainActivity` shows camera view immediately and displays results; no server calls.
- `FETCH_LEGACY_HEIGHT=0` in workflow disables retrieval of historical height data if their build download becomes slow/unavailable. Users never need to change settings.

## Data provenance and rights

- Official PUBG map assets: <https://github.com/pubg/api-assets> — **must be used in accordance with PUBG Terms of Use and player-created content rules.** The project does not redistribute map PNGs in its ZIP; each personal APK build fetches them from their source.
- Historical terrain data: <https://github.com/cgcostume/pubg-maps> — extracted maps may have redistribution restrictions; check before distributing an APK.
- This project is unofficial and not affiliated with PUBG or KRAFTON.

## Next step for real one-photo reliability

Acquire a dataset of actual PUBG screenshots / phone photos with player and target icons annotated, different map zoom settings, colorblind modes, monitor angles and map identities. Train/test an actual player/target marker detector, verify reference-map alignment and height/unit calibration, then validate against known in-game mortar distances / shots. Until then this is a functional **prototype architecture**, not a dependable calibrated shot calculator.
