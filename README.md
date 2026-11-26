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
| Notification access / listener | ✗ (only if you opt in to full music controls) |
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
| Swipe left | Also opens all apps. The panel follows your finger, and a flick opens or closes it. Start away from the screen edge, because an edge swipe is Android's Back gesture |
| Long-press an app in the side panel | Add to home / add to dock / app info / uninstall |
| Long-press an app on the home grid and **drag** | Put it in any cell, gaps allowed (like the Pixel launcher) |
| Drag an app **onto another app** | Make a folder. Drag more apps onto it to add them |
| Tap a folder | Open it; tap its name to rename (e.g. "Finance"). Long-press an app inside to remove it |
| Long-press a folder | Move it, or *Ungroup* to put its apps back on the grid |
| Long-press an **empty spot** on the home grid | Wallpaper & style (Pixel's app), Panel wallpaper photo, Home settings |
| App panel › long-press › *Add to folder…* | Put an app straight into a folder, or start a new one |
| Long-press an app without moving | Menu: remove, add to dock, app info, uninstall |
| Long-press an app in the dock | Move up/down, remove, app info, uninstall |
| ⚙️ at the bottom (or in the app panel) | Settings |

The app panel shows your **5 most recent apps** at the top (Settings › *Recent apps at top
of app panel*). It counts launches made through Panel, so no usage-access permission is needed.

### Settings

- **Top section**: *Boxes* (Folio-style tiles), *Glance* (Niagara-style large clock with the
  date, weather and next events as text), or *Big clock* (iOS lock-screen style, below).
- **Box style**: Glass, Light or Dark. Glass, labels and status-bar icons switch to dark
  text automatically on a light wallpaper (*Text on wallpaper*: Auto / Dark / White to
  override).
- **Time & date box numbers**: size, weight (Light to Black) and font. Big sizes shrink to fit.
- **Hide status bar on home screen**: Android can't hide just the clock, so this hides the
  whole bar on Panel's home screen only. Swipe down from the top to peek.
- **Dock**: right (Folio), left, or bottom. With the dock at the bottom, weather becomes a
  third box.
- **Grid**: 3 to 7 columns, icon size 70–130%. Rows scroll, so there are as many as you need.
- **Dock height**: slide the side dock anywhere from top to bottom. As it moves down, the
  weather pill above it grows (up to the size of the time and date boxes) and shows the
  conditions and place.
- **Up next calendars**: pick which calendars (e.g. personal and work, but not a shared
  family one) appear in Up next, Glance and the big clock line. The agenda still shows
  everything unless you turn on *Filter the agenda too*. *Show all-day events in Up next*
  hides things like birthdays and holidays from Up next.
- Show or hide the Up next box, icon roundness (round by default),
  app names on or off, which side the app panel slides from, next alarm on or off.
- Weather units, city (or device location), and which app the weather box opens.
- Which apps the clock and calendar open (defaults to the system ones).

### Big clock with depth effect

Like the iOS lock screen: a huge clock that the subject of your wallpaper stands in front of.

1. Settings › Big clock & wallpaper › **Choose wallpaper photo** (Android's photo picker, so
   no storage permission is needed).
2. Panel cuts out the photo's subject on the phone itself with Google's ML Kit (the model
   comes from Google Play services; nothing is uploaded).
3. Set **Top section** to **Big clock**, then adjust the clock's size, height, position,
   opacity, font and colour until the subject overlaps it the way you like.

Panel draws the photo, then the clock, then the cut-out subject. That's why the effect needs a
photo chosen inside Panel. It can't read the system wallpaper without broad storage access.
*Also use as phone wallpaper* puts the same photo on your lock screen, but the lock screen
clock itself belongs to Android and can't be changed by an app.

### Music (Spotify and others)

While music is playing, the Up next box gets an **Up next | ♫ Playing** toggle and switches to
the player by itself: album art, title, artist, previous / play-pause / next. The toggle
disappears about 10 minutes after the music stops. Glance and Big clock show a one-line player.

- **Spotify, no permissions** (default): in Spotify › Settings, turn on **Device broadcast
  status**. Album art comes from Spotify's public web link. Shuffle can't be controlled in this
  mode.
- **Full controls, any app** (optional): Settings › Music › *Full controls* asks for
  notification access. You get the app's own extra buttons (Spotify's shuffle, like and so on)
  and it works with YouTube Music, podcasts, etc. Panel never reads your notifications. Android
  may call this a "Restricted setting" for apps not from the Play Store: App info › ⋮ ›
  *Allow restricted settings*. If your bank objects, turn it off again.

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
