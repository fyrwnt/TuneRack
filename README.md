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
- Spotify (global and per country, daily and weekly) and YouTube charts from [kworb.net](https://kworb.net)
- Home: Top Picks based on what you play, Recently Played, chart highlights

## Building

Requires Android Studio (AGP 9.1) and JDK 17+.

```bash
./gradlew :app:assembleDebug
```

Release builds are minified; sign them with your own key.

## How it works

- YouTube data and audio streams: [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)
- Playback: Media3 / ExoPlayer
- Storage: Room database in app storage; downloads via the Storage Access Framework

The app talks directly to YouTube, kworb.net and image CDNs from the phone. There is no backend,
analytics or tracking.

## License

GPL-3.0. See [LICENSE](LICENSE). TuneRack uses NewPipe Extractor, which is GPL-3.0.

TuneRack is not affiliated with YouTube, Google, Spotify, Apple or kworb.net.
