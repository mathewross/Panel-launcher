# Panel launcher

An Android home-screen app that mixes **Folio's look** (frosted square boxes for the time
and date, a round-icon grid, and a tall pill-shaped dock down the right edge) with
**Niagara's glance features** (weather and "up next" from your calendar in one place, each
opening the right app, plus a day-by-day agenda view).

```
┌──────────────────────────────┬──────┐
│ ┌────────────┐ ┌────────────┐│ ┌──┐ │
│ │LOCAL TIME  │ │TUESDAY     ││ │☁ │ │  weather pill
│ │ 12:08      │ │ 15         ││ │12°│ │
│ │PM · Tue    │ │September   ││ └──┘ │
│ └────────────┘ └────────────┘│ ┌──┐ │
│ ┌───────────────────────────┐│ │◯ │ │
│ │UP NEXT        Agenda ›    ││ │◯ │ │  dock (up to 6)
│ │▍Team call · in 25 min     ││ │◯ │ │
│ └───────────────────────────┘│ │◯ │ │
│  ◯    ◯    ◯    ◯            │ └──┘ │
│  ◯    ◯    ◯    ◯   your apps│      │
│          ⚙  (🔍 Search)  ☰           │
└─────────────────────────────────────┘
```

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
| Tap the **Local time** box | Open your clock/alarms app |
| Tap the **date** box | Open your calendar app |
| Tap the **weather** pill (top of the dock) | Open your weather app (Pixel Weather if installed) |
| Long-press the weather pill | Refresh the weather now |
| Tap **Up next** | Open the agenda: upcoming events grouped by day |
| Tap **Search** at the bottom | Open all apps with the keyboard up; press Enter to launch the top match |
| Tap **☰** at the bottom | Open all apps, A–Z |
| Tap an event | Open that event in your calendar app |
| Swipe left | Also opens all apps. Start the swipe away from the screen edge, because an edge swipe is Android's Back gesture |
| Long-press an app in the side panel | Add to home / add to dock / app info / uninstall |
| Long-press an app on home or in the dock | Reorder, remove, app info, uninstall |
| ⚙️ at the bottom (or in the app panel) | Settings |

### Settings

- **Top section**: *Boxes* (Folio-style tiles) or *Glance* (Niagara-style large clock with the
  date, weather and next events as text).
- **Box style**: Glass, Light or Dark.
- **Dock**: right (Folio), left, or bottom. With the dock at the bottom, weather becomes a
  third box.
- Show or hide the Up next box, number of columns, icon roundness (round by default),
  app names on or off, which side the app panel slides from, next alarm on or off.
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
