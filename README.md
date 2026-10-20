# TorBox RoDub Finder TV — test v4

Native Nuvio Android TV provider: your TorBox Library plus public torrent discovery, followed by exact-file and cached-media verification. No external resolver hosting or running PC is required. Existing RoDubbed, AIOStreams and catalog/TMDB lists are unchanged.

## Install/update

Add this repository in Settings → Content & Discovery → Plugins:

https://raw.githubusercontent.com/MrDany14/Nuvio-TorBox-Library-TV/main/repo.json

For an existing installation, refresh this repository. The provider should show **TorBox RoDub Finder [TV Test], Version 4**. Its internal ID and encrypted storage namespace are unchanged, preserving the saved key. Enable plugin providers globally and enable this provider.

First setup: select **Test**, enter your TorBox API key privately in the on-device dialog, then **Save and test**. No TMDB key is required. Never put the TorBox key in chat, GitHub, installer URLs or screenshots.

Nuvio's built-in Test always requests **The Matrix**, not the title last opened. The diagnostic dialog also shows the **last normal title lookup** and its candidate/cache/link counts, if available. Zero Matrix streams can be correct. Test specific titles on their normal stream screens: start with Minions & Monsters or Peppa Pig S4E15; then press Test to inspect that last-title summary.

## How it works

1. Checks up to the first 3,000 existing Library items and searches Torrentio/Knaben public metadata in parallel. Neither index receives your TorBox key. URL-only entries, including error videos, are ignored. Comet's public instance disallows debrid-free discovery, so it is not used.
2. Filters exact normalized title, movie year and Romanian filename/trusted-group candidates. Deduplicates hashes and checks at most 50 directly against TorBox with list_files=true. The exact requested file must be present in cached metadata.
3. Registers matching cached torrents to obtain real account file IDs. **These entries may appear in your TorBox Library.** Every registration sends the server-enforced add_only_if_cached=true. Failed registrations never fall back to uncached downloads. At most five registration attempts per lookup.
4. Rechecks the resulting torrent hash, finished/present flags and exact file. Uses fresh file IDs, not an indexer's guessed index. Requests the media URL and checks a small byte range. Offers up to three verified links.

“Minions & Monsters” matches “Minions and Monsters.” Funhouse never matches Clubhouse. Samples, trailers, subtitles, wrong episodes, sequels and wrong movie years are rejected. Ambiguous names/different episode orderings may be missed rather than guessed.

## Romanian audio and limits

Filename dubbing declarations are labelled **RO Dub (filename)**. The trusted release-group fallback is retained as **Possible RO Dub**. Generic groups also release English content. Audio tracks are not inspected, so these labels cannot guarantee Romanian audio.

Availability changes; some error responses are themselves valid video files. A byte-range check cannot guarantee playback. An authorized private read-only diagnostic obtained media URLs and verified container headers for three existing matching Minions & Monsters files, using the production resolver on a desktop JVM. It added no torrents and played no media. Authenticated cached-only acquisition and TV playback still need device verification.

Zero results may mean no eligible Romanian candidate, no cached copy, or no exact episode file. Coverage will differ from AIOStreams' larger provider set.

## Credentials

TorBox credentials are encrypted with Android Keystore AES-GCM in Nuvio app storage. API requests go to TorBox; the authenticated API can issue credential-bearing media links on its CDN delivery domains. Those links are used for the requested playback, never sent to discovery indexes or printed by this plugin's diagnostics. Nuvio's separate built-in TorBox login is not automatically reused. Other code installed in the same Nuvio app shares app permissions; this is not a separate app sandbox. Revoked/rejected keys reopen setup; that dialog includes Forget key.

## Verification/build

26 matcher assertions, 17 fake-gateway assertions and 12 link-contract assertions cover exact titles/episodes, stale/missing cache, actual file IDs, failed media, no uncached fallback and the real multipart cached-only flag. Native Android SDK metadata and live public discovery tested Minions & Monsters, Peppa S4E15 and Funhouse S1E1 without credentials/account operations. Counts/limits: validation.json.

Build with a JDK, official Android android.jar and D8/R8:

```powershell
./build.ps1 -AndroidJar 'C:/Android/platforms/android-34/android.jar' -D8Jar 'C:/Android/r8.jar' -JdkBin 'C:/Program Files/Android/Android Studio/jbr/bin'
```

Stubs are compile-only and excluded from DEX. Desktop tests need an org.json jar. Metadata uses Nuvio's TmdbProvider SDK; this repository embeds no TMDB API credential.

References: [TorBox API](https://api-docs.torbox.app/), [TorBox OpenAPI](https://api.torbox.app/openapi.json), [Torrentio](https://torrentio.strem.fun/manifest.json), [Knaben API](https://knaben.org/api/v1/).


Version 4 corrects URL validation that wrongly rejected TorBox-issued CDN links containing an access credential or using a domain outside torbox.app. It also aligns the download-link bearer header with Nuvio's native integration and reports whether failure occurred at link creation, media verification, or the Nuvio callback. Full returned URLs and tokens remain absent from diagnostics.

