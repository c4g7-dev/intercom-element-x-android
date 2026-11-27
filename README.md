> [!IMPORTANT]
> **This is Lattice for Android (called InterCom for Android up to 26.11.1-intercom.14), an independent fork based on
> Element X Android.**
> It is not an Element product and is not affiliated with, endorsed by or supported by Element.
> Please report problems with this fork at <https://github.com/c4g7-dev/InterCom/issues>, not upstream.
> Everything below the "Lattice for Android" section is the upstream README, kept for reference.

# Lattice for Android

The Android companion of [Lattice](https://github.com/c4g7-dev/InterCom), built from the `intercom` branch of
this repository. Downloads are on the [releases page](https://github.com/c4g7-dev/InterCom/releases).

The app was called InterCom until 26.11.1-intercom.14. Only what is seen was renamed: the application id, the
OAuth redirect scheme, the branch, the version's format (`-intercom.<n>`), the names in the code and the main
repository (`c4g7-dev/InterCom`) are as they were, so 26.11.1-intercom.15 installs over an InterCom and keeps its
accounts and data.

What differs from upstream Element X Android:

- Own name, icon and application id (`com.c4g7.intercom.android`): installs next to Element X, shares no data with it.
  The icon, the status bar glyph, the splash screen and the logo of the first screens are Lattice's (three bars
  between two rows of dots; `appicon/element`, `app/src/main/res/drawable/splash_lattice.xml`).
- **Lattice's loading animation** (`libraries/designsystem/…/components/lattice/LatticeLoadingIndicator.kt`): on the
  splash screen (Android 12 or newer animate it), while the session is restored, where there was an empty screen,
  and while a room loads. It stands still with Android's animations turned off.
- Calls use the native MatrixRTC stack by default instead of the Element Call WebView, with **screen sharing** enabled
  (the WebView cannot share the screen). It can be switched back in the developer options ("Native calls").
  The stack is Lattice's fork of element-call-android,
  [lattice-element-call-android](https://github.com/c4g7-dev/lattice-element-call-android), which adds:
  - the **phone's audio** in a screen share ("Share phone audio" in the sheet the share button opens, on by default;
    Android 10 or newer; apps that forbid capture, and calls, are silent in it), sent for **music**: stereo Opus at
    160 kbit/s (128 or 192 in the same sheet), every frame sent;
  - the **audio of a screen somebody else shares** is played, with the voices and through the same output;
  - a **volume for every tile**: press and hold somebody's tile for a slider from 0 to 200 % and a mute, for their
    voice and separately for their shared screen's audio. Only this phone hears it, and it is remembered per person;
  - screen shares at the screen's **own resolution up to 1440p**, 30 or 15 fps, one layer, up to 10 Mbit/s;
  - an **equal grid** layout (call settings → Preferences → Layout): every tile the same size, shared screens shown whole;
  - **alone in a call of a direct chat, the call goes on for five minutes** with a countdown, *Stay* and *Leave
    now*, where upstream ends it the moment the other person leaves (`ElementCallOptions.aloneTimeout`, set in
    `features/callnative/…/ElementCallControllers.kt`). Left for being alone, the app says so afterwards
    (`DefaultNativeCallAloneNotice`). A room's call is never left this way;
  - a **camera that cannot be used** is drawn as such: a no-entry badge on the camera button when its permission
    was refused (a tap asks again, or leads to the settings) or the phone has none.
- OAuth client metadata for Matrix Authentication Service: redirect scheme `com.c4g7.intercom.android:/` with
  client URI `https://c4g7.com` (MAS requires the scheme to be the reversed host of the client URI).
  Change both in `plugins/src/main/kotlin/config/BuildTimeConfig.kt` when building for another domain.
- No analytics (PostHog, Sentry) and no bug report upload: the no-op analytics module is used and no rageshake
  server is configured.
- No Firebase: push notifications use [UnifiedPush](https://unifiedpush.org/) only, so a distributor app such
  as [ntfy](https://ntfy.sh/) must be installed on the phone.
- Element's `https://*.element.io` app links are not claimed.
- **Updates itself** from the [InterCom releases](https://github.com/c4g7-dev/InterCom/releases)
  (`features/intercomupdate`, Settings → Lattice updates). While the app is on screen it looks at the releases
  feed (`releases.atom`, with the ETag of its last answer): when the app comes to the screen or the network returns
  (once a minute at most) and every half hour. Only when the feed's `android-v…` entries changed does it ask the
  GitHub API for the releases (and every six hours whatever the feed says), because the API allows an address
  without a login 60 requests an hour and counts unchanged answers too; WorkManager still checks every twelve hours
  in the background. A newer release is installed automatically or offered in a dialog ("Update now" / "Later")
  and a notification: the APK is downloaded (on unmetered networks unless mobile data is allowed or the user
  asked), its SHA-256 checked against the release's `android-update.json` and its signing certificate against the
  running app's, and it is installed through `PackageInstaller`. The first update needs the user's permission to
  install apps and possibly a confirmation; on Android 12 or newer, later ones need neither.
  - While a newer release is known, the avatar that leads to the settings has a **red dot** and the settings begin
    with an **Update available** row.
  - The dialog shows **what is new**: the notes of the release and of every release between it and the running
    one, newest first, drawn as text and lists (HTML is taken out, pictures are not loaded, a link is its text).
    After an update the app shows "What's new in Lattice <version>" once; "Show what's new after updates" in the
    updater's settings turns that off.
  - When Android does not install the update, the app says what can be done: on Samsung phones that **Auto
    Blocker** may be blocking app installs (with a button to the security settings), otherwise about storage, an
    unsupported device or another build being installed. The installer's status code is written to the app's log.
  - The release's side of it is `tools/android-release.sh` and `docs/BUILDING.md` of the main repository. A
    build for another repository or application id changes `ReleaseSelector.DOWNLOAD_PREFIXES`,
    `DefaultReleaseSource.RELEASES_URL` and `ReleaseFeed.URL`; a debug build does not update itself.
  - A release carries the APK twice, as `Lattice-Android-…` and as `InterCom-Android-…`: 26.11.1-intercom.14 and
    older take a release only with the second name, with `android-update.json` naming it, and only from below
    `https://github.com/c4g7-dev/InterCom/releases/download/`. GitHub's API names a renamed repository's files by
    its new name, so the main repository keeps its name until those versions are gone; from 26.11.1-intercom.15
    on the app also accepts `c4g7-dev/Lattice`, and either name of the APK.
- **Lattice's look**: the near-black neutral surfaces, hairlines and single accent (`#6875f5`) of the desktop app,
  and a light variant, as Compound semantic colours (`libraries/compound/…/theme/InterComPalette.kt`) that every
  component reads; corners of 6, 8, 12 and 16 dp. Green stays for "success", red for "critical".
- **Pictures sent together are one gallery**, as on the desktop: consecutive picture messages of one sender,
  within two minutes of each other or of one batch (`io.intercom.gallery`), with nothing shown between them, are
  drawn as one grid (`timeline/groups/ImageGalleries.kt`), and a tap opens the viewer that pages through them.
  Several items can be chosen at once in the picker; they are sent as a message each, pictures marked as one batch
  (`libraries/mediaupload/…/ImageBatch.kt`), never as one `m.gallery` message.
- **The composer's buttons stay on the text's line.** The attachment button, the send or voice button and the emoji
  button are centred on the field's one line of text and stay on its last line when it grows, whatever the size of
  the font or the height of the device's font (`libraries/textcomposer/…/ComposerMetrics.kt`); upstream lifts them
  by a fixed 5 dp, which fits the default font only.
- **Lattice's own ringtone** for incoming calls (Settings → Notifications → Call ringtone; "System default" and
  any other tone can be chosen instead). It is Lattice's own sound, worked out from sine waves by
  `apps/desktop/intercom/gen-sounds.py` of the web fork, no recording and taken from no other product.
- The version is `<Element X version>-intercom.<n>` with the same `<n>` as the desktop release of the round
  (`INTERCOM_REVISION` in `plugins/src/main/kotlin/Versions.kt`, 1 to 79), and the version code adds `<n>` after
  the ABI digit: 2026110135 for 26.11.1-intercom.15.

Known limitations:

- arm64-v8a phones only (every phone of the last years): the RTC core is Lattice's own build of
  [lattice-matrix-rust-rtc](https://github.com/c4g7-dev/lattice-matrix-rust-rtc), made for that one ABI.
- A shared screen's audio is played as part of the call, so it follows the call's output. Over a Bluetooth headset
  that means the headset's call mode, which is mono and narrow; on the speaker, the earpiece and wired headphones it
  is as it was sent.
- Stereo and the bitrate of the phone's audio in a share depend on the media server (a LiveKit SFU) answering the
  request for them, which a current one does. None of this release's call features has been tried in a real call.
- Encoding a 1440p or 1080p share at 30 fps is heavy for a phone: choose a lower resolution or 15 fps in the share
  sheet if the phone gets hot or the picture stutters.
- Maps need a MapTiler key, none is bundled.

Build (JDK 21, Android SDK 37, and for the RTC core: rustup with the `aarch64-linux-android` target, `cargo-ndk`
and the Android NDK). Neither the RTC core fork nor the call library fork is on Maven Central, so each is published
to the local Maven repository (`~/.m2`) first; `gradle/libs.versions.toml` (`element_call`) names the library version
expected there, and the library names the core's:

```sh
git clone -b intercom https://github.com/c4g7-dev/lattice-matrix-rust-rtc.git
(cd lattice-matrix-rust-rtc && scripts/build-intercom-android.sh)
git clone -b intercom https://github.com/c4g7-dev/lattice-element-call-android.git
(cd lattice-element-call-android && ./gradlew publishToMavenLocal)
./gradlew :app:assembleFdroidRelease
```

In the [main](https://github.com/c4g7-dev/InterCom) repository the three are submodules, `matrix-rust-rtc`,
`element-call-android` and `element-x-android`; its `docs/BUILDING.md` has the details.

The release build is signed with the key given by the environment variables `INTERCOM_ANDROID_STOREFILE`,
`INTERCOM_ANDROID_STOREPASSWORD`, `INTERCOM_ANDROID_KEYALIAS` and `INTERCOM_ANDROID_KEYPASSWORD`, and with the
debug key when they are not set. The APKs are written to `app/build/outputs/apk/fdroid/release/`.

Lattice for Android is free software under the [AGPL-3.0](LICENSE), like the code it is based on, which is
copyright New Vector Ltd and Element Creations Ltd. "Element" is a trademark of New Vector Ltd.

---

[![Latest build](https://github.com/element-hq/element-x-android/actions/workflows/build.yml/badge.svg?query=branch%3Adevelop)](https://github.com/element-hq/element-x-android/actions/workflows/build.yml?query=branch%3Adevelop)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=element-x-android&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=element-x-android)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=element-x-android&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=element-x-android)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=element-x-android&metric=bugs)](https://sonarcloud.io/summary/new_code?id=element-x-android)
[![codecov](https://codecov.io/github/element-hq/element-x-android/branch/develop/graph/badge.svg?token=ecwvia7amV)](https://codecov.io/github/element-hq/element-x-android)
[![Element X Android Matrix room #element-x-android:matrix.org](https://img.shields.io/matrix/element-x-android:matrix.org.svg?label=%23element-x-android:matrix.org&logo=matrix&server_fqdn=matrix.org)](https://matrix.to/#/#element-x-android:matrix.org)
[![Localazy](https://img.shields.io/endpoint?url=https%3A%2F%2Fconnect.localazy.com%2Fstatus%2Felement%2Fdata%3Fcontent%3Dall%26title%3Dlocalazy%26logo%3Dtrue)](https://localazy.com/p/element)

# Element X Android

Element X Android is the next-generation [Matrix](https://matrix.org/) client provided by [Element](https://element.io/).

Compared to the previous-generation [Element Classic](https://github.com/element-hq/element-android), the application is a total rewrite, using the [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) underneath and targeting devices running Android 7+. The UI layer is written using [Jetpack Compose](https://developer.android.com/jetpack/compose), and the navigation is managed using [Appyx](https://github.com/bumble-tech/appyx).

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=io.element.android.x)[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/io.element.android.x)

## Table of contents

<!--- TOC -->

* [Screenshots](#screenshots)
* [Translations](#translations)
* [Rust SDK](#rust-sdk)
* [Status](#status)
* [Minimum SDK version](#minimum-sdk-version)
* [Contributing](#contributing)
* [Build instructions](#build-instructions)
* [Support](#support)
* [Copyright and License](#copyright-and-license)

<!--- END -->

## Screenshots

Here are some screenshots of the application:

<!--
Commands run before taking the screenshots:
adb shell settings put system time_12_24 24
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1337
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command battery -e plugged false -e level 100

And to exit demo mode:
adb shell am broadcast -a com.android.systemui.demo -e command exit
-->

|<img src="./docs/images-lfs/screen_1_light.png" width="280" />|<img src="./docs/images-lfs/screen_2_light.png" width="280" />|<img src="./docs/images-lfs/screen_3_light.png" width="280" />|<img src="./docs/images-lfs/screen_4_light.png" width="280" />|
|-|-|-|-|
|<img src="./docs/images-lfs/screen_1_dark.png" width="280" />|<img src="./docs/images-lfs/screen_2_dark.png" width="280" />|<img src="./docs/images-lfs/screen_3_dark.png" width="280" />|<img src="./docs/images-lfs/screen_4_dark.png" width="280" />|

## Translations

Element X Android supports many languages. You can help us to translate the app in your language by joining our [Localazy project](https://localazy.com/p/element). You can also help us to improve the existing translations.

Note that for now, we keep control on the French and German translations.

Translations can be checked screen per screen using our tool Element X Android Gallery, available at https://element-hq.github.io/element-x-android/. Note that this page is updated every Tuesday.

More instructions about translating the application can be found at [CONTRIBUTING.md](CONTRIBUTING.md#strings).

## Rust SDK

Element X leverages the [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) through an FFI layer that the final client can directly import and use.

We're doing this as a way to share code between platforms and while we've seen promising results it's still in the experimental stage and bound to change.

## Status

This project is actively developed and supported. New users are recommended to use Element X instead of the previous-generation app.

## Minimum SDK version

Element X Android requires a minimum SDK version of 24 (Android 7.0, Nougat). We aim to support devices running Android 7.0 and above, which covers a wide range of devices still in use today.

Element Android Enterprise requires a minimum SDK version of 33 (Android 13, Tiramisu). For Element Enterprise, we support only devices that still receive security updates, which means devices running Android 13 and above. Android does not have a documented support policy, but some information can be found at [https://endoflife.date/android](https://endoflife.date/android).

## Contributing

Want to get actively involved in the project? You're more than welcome! A good way to start is to check the issues that are labelled with the [good first issue](https://github.com/element-hq/element-x-android/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) label. Let us know by commenting the issue that you're starting working on it.

But first make sure to read our [contribution guide](CONTRIBUTING.md) first.

You can also come chat with the community in the Matrix [room](https://matrix.to/#/#element-x-android:matrix.org) dedicated to the project.

## Build instructions

Just clone the project and open it in Android Studio. Make sure to select the
`app` configuration when building (as we also have sample apps in the project).

To build against a local copy of the Rust SDK, see the [Developer
onboarding](docs/_developer_onboarding.md#building-the-sdk-locally) instructions.

## Support

When you are experiencing an issue on Element X Android, please first search in [GitHub issues](https://github.com/element-hq/element-x-android/issues)
and then in [#element-x-android:matrix.org](https://matrix.to/#/#element-x-android:matrix.org).
If after your research you still have a question, ask at [#element-x-android:matrix.org](https://matrix.to/#/#element-x-android:matrix.org). Otherwise feel free to create a GitHub issue if you encounter a bug or a crash, by explaining clearly in detail what happened. You can also perform bug reporting from the application settings. This is especially recommended when you encounter a crash.

## Copyright and License

Copyright (c) 2025 Element Creations Ltd.
Copyright (c) 2022 - 2025 New Vector Ltd.

This software is dual licensed by Element Creations Ltd (Element). It can be used either:

(1) for free under the terms of the GNU Affero General Public License (as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version); OR

(2) under the terms of a paid-for Element Commercial License agreement between you and Element (the terms of which may vary depending on what you and Element have agreed to).

Unless required by applicable law or agreed to in writing, software distributed under the Licenses is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the Licenses for the specific language governing permissions and limitations under the Licenses.
