# Panel launcher

An Android home-screen app that mixes **Folio's look** (clean iOS-style boxes, rounded icon
grid, a slide-in side panel with all your apps) with **Niagara's glance features** (clock,
weather and "up next" from your calendar in one place, each opening the right app, plus a
day-by-day agenda view).

## Why this works with banking apps

Panel is a normal launcher. It uses only the standard Android home-screen APIs (`LauncherApps`).
It does **not** use any of the things banking apps block:

| Not used | |
|---|---|
| Accessibility service | ✗ |
| Draw over other apps (overlays) | ✗ |
| Notification access / listener | ✗ |
| Query all packages | ✗ |

The only permissions are:

- **Calendar (read)**: for *Up next* and the agenda. Optional.
- **Approximate location**: for local weather. Optional; you can type a city instead.
- **Internet**: weather from [open-meteo.com](https://open-meteo.com) (free, no account, no key).

## Install

1. On your phone, open the repo's **Releases**, then **Latest build**, and download
   `Panel-launcher.apk`.
2. Open the file. Android will ask you to allow installs from your browser or Files app the
   first time. Allow it, then install.
3. Press Home. Android asks which home app to use: pick **Panel**, then *Always*.
   (Or go to Settings › Apps › Default apps › Home app.)

Every push to the repo rebuilds the APK (GitHub Actions, `.github/workflows/build.yml`).
Installing a newer build updates the app in place and keeps your layout.

## Using it

| Do this | To |
|---|---|
| Tap the **clock** box | Open your clock/alarms app |
| Tap the **date** | Open your calendar app |
| Tap the **weather** box | Open your weather app (you choose which, the first time) |
| Long-press the weather box | Refresh the weather now |
| Tap **Up next** | Open the agenda: upcoming events grouped by day |
| Tap an event | Open that event in your calendar app |
| Swipe left (or tap the handle on the right edge) | Open the side panel with all apps, A–Z, plus search |
| Long-press an app in the side panel | Add to home / add to dock / app info / uninstall |
| Long-press an app on home or in the dock | Reorder, remove, app info, uninstall |
| ⚙️ in the side panel | Settings |

### Settings

- **Top section**: *Boxes* (Folio-style tiles) or *Glance* (Niagara-style large clock with the
  date, weather and next events as text).
- **Box style**: Glass, Light or Dark.
- Number of columns, icon roundness, app names on or off, side panel on the left or right,
  next alarm on or off.
- Weather units, city (or device location), and which app the weather box opens.
- Which apps the clock and calendar open (defaults to the system ones).

## Signing key for updates

CI signs each build with the same key so updates install over the previous version. By
default that key is generated on the first run and kept in the GitHub Actions cache. If the
cache is ever cleared (for example after a week with no builds), the next APK will be signed
with a new key, and you'd need to uninstall Panel before installing it.

To make the key permanent, create a keystore and add two repository secrets
(Settings › Secrets and variables › Actions):

- `PANEL_KEYSTORE_BASE64`: the output of `base64 -w0 your.keystore`
- `PANEL_KEYSTORE_PASSWORD`: its password (the key alias must be `panel`, with the same password)

## Building locally

Requires the Android SDK (API 35) and JDK 17:

```
./gradlew assembleDebug
```
