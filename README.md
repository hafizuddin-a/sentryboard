<p align="center">
  <img src="assets/logo.png" alt="Sentryboard logo" width="200">
</p>

<h1 align="center">Sentryboard</h1>

<p align="center">
  An Android keyboard that deletes the words you choose the moment you finish typing them.
</p>

<p align="center">
  <a href="../../releases/latest"><b>Download</b></a> ·
  <a href="#install"><b>Install</b></a> ·
  <a href="#use"><b>Use</b></a> ·
  <a href="LICENSE"><b>Apache 2.0 License</b></a>
</p>

<br>

Sentryboard is the Android counterpart of [WordBlock](https://github.com/hafizuddin-a/wordblock-mac), a Mac
program that does the same thing: a small, self-imposed friction tool for a phone you own. It is a fork of
[Simple Keyboard](https://github.com/rkkr/simple-keyboard), so it keeps that keyboard's small size.

- Whole-word matching that ignores case, accents and apostrophes, and catches letter swaps (`sh@t`, `h3ll`).
  Words and **phrases** (`hello world`) both work.
- A **settings lock**: once locked, adding words is instant but removing them, switching the filter off or
  changing the delay needs a countdown (you choose how long). While locked, the settings screen shows only
  how many words there are, not which; they come back when you unlock.
- Optional **uninstall protection**: Android will not uninstall the app until you switch it off first.
- No network access (there is no `INTERNET` permission), no account, no analytics.

It only looks at the word you are typing right now. Nothing you type is saved, logged or sent anywhere, and
your word list is never written to the log or to Android's cloud backup.

Requires Android 7.0 or later.

## Install

1. On your phone, download `Sentryboard-1.0.0.apk` from the [Releases](../../releases) page.
2. Open it. Android asks you to allow installs from the app you downloaded it with (your browser or Files):
   allow it. Google Play Protect may say it has not seen the app before: tap **More details**, then
   **Install anyway**. That is because the app is not on the Play Store, not because anything is wrong.
3. Open **Sentryboard**. It tells you the keyboard is not enabled: tap **OK**, switch **Sentryboard** on, then
   pick it as your keyboard (tap the keyboard icon at the bottom right of any keyboard, or choose it under
   *Settings > System > Keyboard*).
4. Open **Sentryboard > Word filter > Add a word** and add your first words.

**Updating:** install a newer APK over the old one. Your list, lock and settings are kept, as long as it is
signed with the same key (every release is). Do not uninstall first: that erases them.

**Check the download:** the APK is signed with a key whose SHA-256 fingerprint is
`9d:bf:4f:e2:12:32:e0:c0:64:3a:76:54:58:a7:34:12:0d:97:83:5b:89:a4:09:1c:96:36:a9:b2:db:5a:85:79`.
You can see it with `apksigner verify --print-certs Sentryboard-1.0.0.apk`.

Prefer to read the code first? It is all in this repository, and you can build it yourself (below).

## Use

Type a blocked word followed by a space, punctuation or Enter, and it disappears. Your words live in
**Sentryboard > Word filter**, one entry per line:

| Entry   | Meaning |
|---------|---------|
| `word`  | Blocked when you finish typing it |
| `word*` | Also blocks longer words that start with it (`words`, `wording`, not `sword`) |
| `word!` | Blocked the instant it is typed, without waiting for a separator |
| `hello world` | A phrase of two or more words. The whole phrase is deleted when you finish its last word |

Use `word!` only for words that are not the start of harmless words: with `ass!`, typing `assess` deletes
`ass` as soon as its third letter is typed.

- **Disguises:** accents are removed, case and apostrophes are ignored, and common leet is read
  (`0`=o, `1`=i or l, `3`=e, `4`=a, `5`=s, `7`=t, `@`=a, `$`=s). A blocked `ass` never matches inside `class`.
- **Phrases:** any gap of up to 64 characters counts as the space between the words, so `hello  world`,
  `hello, world`, `hello_world` and a line break all match `hello world`. `*` and `!` apply to the last word.
- **Pasting is covered:** a blocked word or phrase in pasted text is removed from it (see
  [What it can't do](#what-it-cant-do) for the limits).
- **Editing is covered:** adding the missing letter to `helo `, or putting a space into `helloworld`, after
  the fact still gets the word or phrase deleted.
- **Underscores** are part of a word, so `snake_case` is a single entry.
- An entry is made of letters, digits, apostrophes, underscores, `@` and `$`, with spaces between the words
  of a phrase. Password, number, phone and date fields are never touched.

Under **Remove after a pause** (off by default) a blocked word is also removed if you stop typing for a
moment, which helps when you tap an app's own Send button without typing a separator.

## Settings lock

Under *Word filter > Settings lock* you can lock the filter's settings for a chosen time, from 1 minute to
24 hours. While locked you can still **add** words (that can only make the filter stronger), but you cannot
**remove** words, switch the filter off, change the pause options or change the lock itself, and the word list
is **hidden**. To edit again, tap **Unlock** and stay on the full-screen countdown until it finishes.

- Leaving the countdown page (Home, another app, screen off, Back) **resets it to zero**. Rotating the phone
  does not.
- Afterwards the settings are editable until you leave them, for 5 minutes, or until the phone restarts.
- The countdown uses the phone's monotonic clock, so changing the clock does not shorten it.
- The lock is enforced underneath the screens too, and a damaged lock state is treated as **locked**.

## Uninstall protection

Under *Uninstall protection*. Android has one hard rule for an ordinary app: an active device admin cannot be
uninstalled until it is deactivated first. So Sentryboard can be a device admin, and that is all this does. It
asks for **no** device policies: it cannot lock, wipe or control your phone. Turning it off from the app needs
the unlock countdown while the settings are locked.

## What it can't do

- It is friction, not a security boundary. Android's own *Device admin apps* screen can still switch
  uninstall protection off, you can switch to another keyboard, and a factory reset or safe mode removes the
  app. "Clear storage" wipes the lock and the list (the app still cannot be uninstalled).
- Text pasted into a field is checked: the pasted text and the words at either end of it are scanned, and
  only the blocked words and phrases are removed from it. The rest of the paste stays. This also covers
  autofill and drag and drop. It works by noticing that the cursor jumped forward
  without the keyboard having typed anything, so tapping forward past a blocked word that is
  already in the text removes it too, and it can miss a paste in an app that reports its cursor late or not
  at all, it only scans the first 4,000
  characters of a very long paste, and the text it rewrites loses rich formatting such as bold. Voice input
  through another keyboard is not seen.
- If you type a blocked word and tap an app's own Send button without a separator, a plain `word` entry may
  not have been removed yet. Use `word!` entries or the pause option for that.
- Hiding the list only hides it on the settings screen. You can still find out whether a word is on it by
  typing it and seeing whether it is deleted.
- A word with text after the cursor counts as finished, so typing a longer word in front of existing text can
  delete a blocked start of it early (with `hello world` blocked, `hello worldwide` typed before an existing
  space loses `hello world`).
- It only works while Sentryboard is the active keyboard. English only: scripts that rely on combining marks
  (Indic, Thai, Arabic vowel marks) are compared without them.

## Permissions

`VIBRATE` is Simple Keyboard's and is unchanged. `INTERNET` is never requested. The one extra thing is the
optional device admin above (declared on the receiver; you activate it yourself, and it declares no policies).
Not used, on purpose: accessibility services, Device Owner, overlays, hiding the app icon.

## Build from source

You need a JDK (17 or newer) and the Android SDK.

```bash
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest       # unit tests (pure JVM, no phone needed)
./gradlew assembleRelease              # signed only if you set up a key (below)
```

Upstream does not commit `gradle/wrapper/gradle-wrapper.jar`. If it is missing, open the project in Android
Studio, or generate it once with `gradle wrapper --gradle-version 9.6.0`.

A release build is signed if `~/.sentryboard-signing/keystore.properties` exists, with the keys `storeFile`,
`storePassword`, `keyAlias` and `keyPassword` (see `app/build.gradle`), and left unsigned otherwise. An APK you
sign with your own key will not install over one signed with mine: uninstall first, which erases your data.

The application id is `com.example.sentryboard` (set in one place, `app/build.gradle`), so it installs alongside
Simple Keyboard. The Java package is still `rkr.simplekeyboard.inputmethod` to keep the diff against upstream
small.

## Removing everything

1. Open *Word filter*, tap **Unlock** and wait for the countdown (skip this if the lock is off).
2. Open *Uninstall protection* and tap **Remove uninstall protection**. (Or deactivate Sentryboard under
   Android's *Settings > Security > Device admin apps*.)
3. Uninstall the app normally.

## Credits and license

Sentryboard is a modified fork of [Simple Keyboard](https://github.com/rkkr/simple-keyboard) by Raimondas
Rimkus, which is based on the AOSP LatinIME keyboard. Everything is licensed under the
[Apache License 2.0](LICENSE); see [NOTICE](NOTICE) for what was changed. The privacy statement is in
[PRIVACY.md](PRIVACY.md).
