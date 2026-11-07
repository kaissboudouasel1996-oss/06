# 06

Android app that lists the upcoming matches of the **Premier League, Ligue 1, La Liga, Serie A and Bundesliga** and, for each match, every TV channel that broadcasts it, grouped by country.

- Pick a league tab, then tap a match to load its channels.
- Times are shown in your phone's time zone.
- The app icon is a football.

Data comes from [TheSportsDB](https://www.thesportsdb.com) (`eventsnextleague` for matches, `lookuptv` for channels).

## Get the app

GitHub builds the APK for you, no Android Studio needed.

1. Push this project to your repository (any push that touches `android/` starts the build), or open the **Actions** tab and run **Build Android APK** by hand.
2. When the run is green, download `app-debug.apk` from the **Releases** page (tag `latest`) or from the run's *Artifacts*.
3. Install it on your phone (allow "install unknown apps" for your browser).

## API key

The app works out of the box with TheSportsDB's free key, but that key is heavily limited: it returns only a few matches and channels per request. For the full list of matches and channels, get a premium key from TheSportsDB and paste it in the app: gear icon, **TheSportsDB API key**, Save.

The key is stored only on your phone, never in this repository.

## Folders

- `android/`: Kotlin + Jetpack Compose app (`Api.kt` talks to TheSportsDB, `MainActivity.kt` is the UI)
- `.github/workflows/`: APK build
