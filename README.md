# TuneRack

An Android music player for YouTube Music with an Apple Music–style interface. No account, no
server: your library lives on your phone.

## Features

- Search YouTube Music and stream songs in the background, with media notification controls
- Queue, Spotify-style balanced shuffle, repeat, Play Next / Play Last; the queue survives restarts
- Local library: likes, playlists, artists, listening history
- Unlimited downloads as tagged M4A files (title, artist, cover art) in a folder you choose,
  playable offline and by other music players
- Library export / import (JSON) and download-folder re-scan, for moving to a new phone
- Spotify and YouTube charts from [kworb.net](https://kworb.net): global and per country, daily and weekly
- Home: Top Picks from YouTube Music radio for what you play or like, Recently Played, chart highlights
- Audio quality settings for Wi-Fi, mobile data and downloads
- Spotlight tutorial on first launch, with a ? button on every screen to replay it
- Feedback form that opens a prefilled GitHub issue
- Optional, read-only Google sign-in that imports your YouTube likes and playlists (see below)

## Building

Requires Android Studio (AGP 9.1) and JDK 17+.

```bash
./gradlew :app:assembleDebug
```

Release builds are minified; sign them with your own key.

## Google sign-in

Sign-in uses the official YouTube Data API with the read-only `youtube.readonly` scope. It is
optional: everything else works as a guest. The Google Cloud app is in Testing mode, so only
Google accounts on its test-user list can sign in, and sign-ins expire after 7 days. Builds signed
with a different key need their own OAuth client (package `com.gitgud.tunerack` + that key's SHA-1).

## How it works

- YouTube data and audio streams: [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)
- Playback: Media3 / ExoPlayer
- Storage: Room database in app storage; downloads via the Storage Access Framework

The app talks directly to YouTube, kworb.net and image CDNs from the phone (and to Google, only if
you sign in). There is no backend, analytics or tracking, and Android backup is disabled, so the
library never leaves the phone unless you export it.

## License

GPL-3.0. See [LICENSE](LICENSE). TuneRack uses NewPipe Extractor, which is GPL-3.0.

TuneRack is not affiliated with YouTube, Google, Spotify, Apple or kworb.net.
