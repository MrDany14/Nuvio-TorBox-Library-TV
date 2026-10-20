# TorBox Library TV — test plugin

Separate native extension for Nuvio Android TV 1.0.0. It does not change RoDubbed, AIOStreams or any catalog/list.

## Install and configure

Add this repository in Nuvio TV → Settings → Content & Discovery → Plugins:

https://raw.githubusercontent.com/MrDany14/Nuvio-TorBox-Library-TV/main/repo.json

Select **Test** for **TorBox Library [TV Test]**. With no saved key, a private setup dialog opens. Enter your TorBox API key there and choose **Save and test**. Do not paste it into chat, GitHub, an installer URL or a screenshot. Leave the release groups unchanged to retain the previous AIOStreams fallback, or edit the comma-separated list.

After it says Connected, close the dialog. Open the desired title/episode and look for this provider. First real-account cases to test: Peppa Pig S4E15, and Mickey Mouse Funhouse (must never return Mickey Mouse Clubhouse).

The key is stored encrypted using Android Keystore in Nuvio's app storage. It is sent only to TorBox's fixed API endpoint. This is not isolation from other code installed in the same Nuvio app. No hosted resolver, extra hosting account or running PC is required after installation. TorBox account/service requirements still apply.

If a saved key is revoked or rejected, the setup dialog reopens on the next lookup. The dialog includes Forget key. Settings are device-local and are not synchronized to another device.

## Scope and limits

- Searches only files already in your TorBox torrent Library. No Comet/global torrent search, torrent creation, cache warming or downloading.
- Requires both download_finished and download_present, and rechecks the matched Library item before requesting its exact file link.
- Matches full normalized title/aliases and exact season/episode. Different named series and sequels are rejected; movies need a matching year. Ambiguous/unsupported filenames may be missed rather than guessed. Alternate episode orderings are not automatically remapped.
- Excludes samples, trailers and non-video files. Checks the generated media URL with a small byte-range request, without playback. Up to three results are returned.
- Filename dubbing declarations are labelled RO Dub (filename). Trusted release groups are labelled **Possible RO Dub**. Neither is audio-track inspection, and generic groups can also publish English-only content.
- Some errors can themselves be valid video files. These checks cannot guarantee that every stream plays or that the audio is Romanian.
- The first build is a TEST release: real authenticated Library selection/playback has not yet been tested. No user credential was available during development.

## Verification

22 offline matching assertions passed, including Peppa S4E15 versus other episodes, missing/incomplete downloads, wrong titles/years/sequels, and Funhouse versus Clubhouse (including a contradictory file inside a correctly named pack). Nuvio TV emulator installed the native package and opened its masked, secure setup dialog. An invalid dummy key produced a connection failure and cleared the input. The earlier isolated setup harness separately tested encrypted-storage roundtrip, persistence after restart, and clearing. These are distinct from a real-account playback test.

## Build

Use a JDK with javac/jar, an official Android platform android.jar and the Android D8/R8 jar:

```powershell
./build.ps1 -AndroidJar 'C:/Android/platforms/android-34/android.jar' -D8Jar 'C:/Android/r8.jar' -JdkBin 'C:/Program Files/Android/Android Studio/jbr/bin'
```

Stubs compile against Nuvio's existing Kotlin/Cloudstream classes; they are deliberately excluded from the packaged DEX. The package contains only classes.dex and manifest.json. Matcher fixtures under tests use org.json (provide a desktop org.json jar when running outside Android).

API reference: [TorBox torrent API](https://github.com/TorBox-App/torbox-sdk-js/blob/main/documentation/services/TorrentsService.md). Metadata is loaded through Nuvio's existing TmdbProvider SDK; this repository does not embed a TMDB API credential. Native Test queries without movie-year metadata may show zero results even when a regular title lookup can match. Matching uses the SDK's English display title; unsupported localized-only filenames can be missed.
