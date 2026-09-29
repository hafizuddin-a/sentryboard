# Sentryboard

Sentryboard is a modified fork of [Simple Keyboard](https://github.com/rkkr/simple-keyboard),
which is itself based on AOSP LatinIME. It keeps Simple Keyboard's small size and its single
permission (`VIBRATE`), and adds one feature: **words on a list you choose are deleted from the text
field as you type them.** There is no network access, no analytics, and no accounts.

Licensed under Apache License 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Everything below the
next horizontal rule is upstream's README, unchanged.

## What it does

When you finish a word by typing a space, punctuation or Enter (including Enter as Send / Go / Next),
Sentryboard checks the word immediately before the cursor against your list. If it is on the list,
the word is deleted and the separator you typed is inserted as usual. There is no toast or dialog.

Matching is by **whole word** and forgiving about disguise. Both the typed word and each list entry are
normalized the same way before they are compared: accents are removed (`wórd` = `word`), case is
ignored, apostrophes are ignored (`don't` = `dont`), and common leet substitutions are mapped
(`0`=o, `1`=i or l, `3`=e, `4`=a, `5`=s, `7`=t, `@`=a, `$`=s). A blocked `ass` never matches inside a
longer word such as `class`, `assignment` or `pass`.

### Entry syntax

One entry per line in *Settings > Word filter*:

| Entry   | Meaning |
|---------|---------|
| `word`  | Blocks exactly that word. |
| `word*` | Also blocks longer words that start with `word` (`words`, `wording`, but not `sword`). |
| `word!` | Blocks exactly that word **and removes it the instant it is typed**, without waiting for a separator. Use it only for words that are not the start of harmless words: with `ass!`, typing `assess` deletes `ass` as soon as its third letter is typed. |

An entry must be a single word of letters, digits and apostrophes, optionally ending in `*` or `!`.
Anything else (spaces, hyphens, other punctuation) could never match a typed word and is rejected.

### Settings

- **Remove blocked words**: master switch.
- **Remove after a pause** (off by default) and **Pause length** (250 ms to 2 s, default 500 ms): also
  removes a blocked word if you stop typing for that long. See the Send-button gap below.
- **Blocked words**: add and remove entries. The list lives in a private file in device-protected
  storage and is excluded from Android's cloud backup and device-to-device transfer by the app's backup rules. Its contents are
  never logged.

Password fields (including "visible password" fields, which many terminal-style fields report), number,
phone and date fields, and fields that declare no input type are never touched.

## Known limitations

- **The Send-button gap.** If you type a blocked word and tap the *app's own* Send button, no separator
  was typed, so a plain `word` entry has not been removed yet. Two mitigations:
  `word!` entries are removed the moment they are typed, and the optional pause setting removes
  a blocked word after a short idle. **A fast tap on Send can still get a plain entry through.** That is
  expected and cannot be closed from inside a keyboard.
- The pause setting can cut short a word you type slowly (if `ass` is blocked, pausing after `ass` in
  the middle of typing `assassin` deletes `ass`). That is why it is off by default.
- Only the word immediately before the cursor is checked, when you type a separator there. Words you
  edit in the middle of existing text are checked the next time you type a separator at that spot.
- **Voice input and pasted text are not filtered.** A paste that starts with a separator does cause the
  word typed just before it to be checked.
- It only works while Sentryboard is the active keyboard.
- Digits reach editors as asynchronous key events, so an `!` entry that *ends in a digit* is removed at
  the next separator rather than instantly.
- Because normalization strips combining marks, a blocked word in a script that uses them heavily
  (Indic scripts, Thai, Arabic vowel marks) is compared without those marks and can match other words
  with the same base letters. Word boundaries in such scripts are also approximate, since combining
  marks are not word characters.
- `@` and `$` count as leet letters when a word is compared, but as separators when a word is *typed*,
  so `w@rd` is seen as `w`, `@`, `rd` and is not caught. `$word` is caught, as `word`.
- Multi-word phrases are not supported.

## Building

You need a JDK (17 or newer) and the Android SDK.

```
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest        # unit tests (pure JVM)
```

Upstream does not commit `gradle/wrapper/gradle-wrapper.jar`. If it is missing, open the project in
Android Studio, or generate it once with `gradle wrapper --gradle-version 9.6.0`.

The application id is `com.example.sentryboard` (set in one place, `app/build.gradle`), so it installs
alongside Simple Keyboard. The Java package is still `rkr.simplekeyboard.inputmethod` to keep the diff
against upstream small.

---

# Simple Keyboard

[![Crowdin](https://d322cqt584bo4o.cloudfront.net/simple-keyboard/localized.svg)](https://crowdin.com/project/simple-keyboard)

<img src="images/screenshot-0.png"
      alt="closeup"
      width="500"/>
      
## About

Features:
- Small size (<1MB)
- Adjustable keyboard height for more screen space
- Number row
- Swipe space to move pointer
- Delete swipe
- Custom theme colors
- Minimal permissions (only Vibrate)
- Ads-free

Feature it doesn't have and probably will never have:
- Emojis
- GIFs
- Spell checker
- Swipe typing

## Downloads

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/packages/rkr.simplekeyboard.inputmethod/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
      alt="Get it on Google Play"
      height="80">](https://play.google.com/store/apps/details?id=rkr.simplekeyboard.inputmethod)

## Credits

Licensed under Apache License Version 2

This keyboard is based on AOSP LatinIME keyboard. You can get the original source code in https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
