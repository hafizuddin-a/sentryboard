# Sentryboard

Sentryboard is a modified fork of [Simple Keyboard](https://github.com/rkkr/simple-keyboard),
which is itself based on AOSP LatinIME. It keeps Simple Keyboard's small size and adds three things:

1. **A word filter**: words on a list you choose are deleted from the text field as you type them.
2. **A settings lock**: the filter's settings can be locked so that changing them means waiting out
   a countdown.
3. **Uninstall protection**: an optional device admin, which makes Android refuse to uninstall the
   app until the admin is deactivated first.

There is no network access (there is no `INTERNET` permission), no analytics, and no accounts. Beyond
Simple Keyboard's `VIBRATE`, the only thing it asks for is the optional device admin (see
[Permissions](#permissions)). This is a self-imposed friction tool for a phone you own, not a security
product: see [Known limitations](#known-limitations) for what it cannot stop.

Licensed under Apache License 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Everything below the
next horizontal rule is upstream's README, unchanged.

## What it does

When you finish a word by typing a space, punctuation or Enter (including Enter as Send / Go / Next),
Sentryboard checks the word immediately before the cursor against your list. If it is on the list,
the word is deleted and the separator you typed is inserted as usual. There is no toast or dialog.

Editing a word that is already finished is covered too. Whenever there is text after the cursor, the
whole word around the cursor (the part before it and the part after it) is checked right after each
key, so slipping a blocked word through by typing `helo`, a space, and then going back to add the
missing `l` does not work. That includes adding or deleting a letter inside a word, adding one at the
end of a word that is followed by a space, and splitting or joining words with a space.

Matching is by **whole word** and forgiving about disguise. Both the typed word and each list entry are
normalized the same way before they are compared: accents are removed (`wórd` = `word`), case is
ignored, apostrophes are ignored (`don't` = `dont`), and common leet substitutions are mapped
(`0`=o, `1`=i or l, `3`=e, `4`=a, `5`=s, `7`=t, `@`=a, `$`=s), so `sh@t` is read as `shat`. A blocked `ass` never matches inside a
longer word such as `class`, `assignment` or `pass`.

### Entry syntax

One entry per line in *Settings > Word filter*:

| Entry   | Meaning |
|---------|---------|
| `word`  | Blocks exactly that word. |
| `word*` | Also blocks longer words that start with `word` (`words`, `wording`, but not `sword`). |
| `word!` | Blocks exactly that word **and removes it the instant it is typed**, without waiting for a separator. Use it only for words that are not the start of harmless words: with `ass!`, typing `assess` deletes `ass` as soon as its third letter is typed. |

An entry must be a single word of letters, digits, apostrophes, underscores, `@` and `$`, optionally ending
in `*` or `!`, with at least one letter or digit. Anything else (spaces, hyphens, other punctuation) could
never match a typed word and is rejected.

Underscores are part of a word, so `snake_case` can be an entry and is matched as one word. They also
mark a checkpoint: a blocked word right before an underscore (`word` in `word_list`) is still removed
when you type the underscore, and only that word goes, so `my_word` becomes `my_`.

### Settings

- **Remove blocked words**: master switch.
- **Remove after a pause** (off by default) and **Pause length** (250 ms to 2 s, default 500 ms): also
  removes a blocked word if you stop typing for that long. See the Send-button gap below.
- **Blocked words**: add and remove entries. The list lives in a private file in device-protected
  storage and is excluded from Android's cloud backup and device-to-device transfer by the app's backup rules. Its contents are
  never logged.

Password fields (including "visible password" fields, which many terminal-style fields report), number,
phone and date fields, and fields that declare no input type are never touched.

## Settings lock

Under *Settings > Word filter > Settings lock* you can lock the filter's settings for a chosen
duration. While locked the blocked-word list is **hidden** (the screen shows only how many words there
are) and you can still **add** words to it: adding can only make the filter stronger. Adding a word
that is already on the list gets the same answer as adding a new one, so the screen does not tell you
what is on the list. The words show again once you unlock. What is locked is **removing** words, the on/off switch, the pause
options, and the lock itself (its length, and removing it). Keyboard settings such as theme and height
are not affected.

To edit again, tap **Unlock**. A full-screen page counts down the length you chose; you must stay on
it until it finishes. Then the settings are editable until you leave the settings, or for 5 minutes,
or until the phone restarts, whichever comes first.

- The countdown is timed with the phone's monotonic clock, so changing the clock does not shorten it.
- **Leaving the page resets it to zero**: Home, Recents, another app, the screen turning off, Back or
  Cancel. Rotating the phone, changing the font size or switching dark mode do not.
- The countdown is never saved. If the app is killed mid-countdown the lock simply stays on.
- The lock's length is fixed while it is on. It can be changed only while the settings are unlocked,
  and applies the next time you lock.
- The lock is enforced in the screens **and** underneath them: removing from the word list and changing
  the filter settings are refused while locked, whatever a screen does. If the saved lock state ever looks
  corrupt, the lock is treated as **on** (with a 30 minute countdown).
- Lengths from 1 minute to 24 hours (test builds also allow a few seconds, so the lock can be tried
  without waiting).

## Uninstall protection

Under *Settings > Uninstall protection*. Android has exactly one hard rule for an ordinary app: **an
active device admin cannot be uninstalled until it is deactivated first.** So Sentryboard can be a
device admin, and that is all this feature is. It asks for **no** device policies: it cannot lock,
wipe or control the phone.

- **Enable uninstall protection** opens Android's screen that asks you to activate the admin.
- **Remove uninstall protection** deactivates it, so the app can be uninstalled. While the settings
  lock is on this needs the unlock countdown, like every other setting.
- Android's own *Device admin apps* screen can also deactivate it directly, at any time. Since Android
  7.1 the Uninstall button even takes you straight there. So this is a speed bump, not a lock.

An earlier version also ran a background "guard" that covered Android's device-admin screen while the
settings lock was on. It has been removed: it needed several extra permissions and it did not work on the
phone it was tried on. The plain device admin is what remains.

## Permissions

`VIBRATE` is Simple Keyboard's and is unchanged. `INTERNET` is never requested.

| Access | Why | Needed for |
|---|---|---|
| Device admin (`BIND_DEVICE_ADMIN` on the receiver; you activate it yourself) | Makes Android require deactivation before uninstall. Declares no policies. | Uninstall protection |

Not used, on purpose: accessibility services, Device Owner or Profile Owner, any owner-only device
policy API, wipe or password-reset policies, hiding the app icon, or obfuscation. The app also turns
Android's automatic backup off and excludes everything from data extraction, so the lock and the list
cannot be restored from an older copy.

## Removing everything

1. Open *Settings > Word filter*, tap **Unlock** and wait for the countdown (skip this if the lock is off).
2. Open *Uninstall protection* and tap **Remove uninstall protection**. (Or deactivate Sentryboard under
   Android's *Settings > Security > Device admin apps*.)
3. Uninstall the app normally.

If the app will not open, the Device admin screen route above still works, and so does safe mode or a
factory reset. Turning the lock off does not delete your word list; uninstalling does.

## Known limitations

- **The Send-button gap.** If you type a blocked word and tap the *app's own* Send button, no separator
  was typed, so a plain `word` entry has not been removed yet. Two mitigations:
  `word!` entries are removed the moment they are typed, and the optional pause setting removes
  a blocked word after a short idle. **A fast tap on Send can still get a plain entry through.** That is
  expected and cannot be closed from inside a keyboard.
- The pause setting can cut short a word you type slowly (if `ass` is blocked, pausing after `ass` in
  the middle of typing `assassin` deletes `ass`). That is why it is off by default.
- A word with text after the cursor counts as finished, so it is checked after every key. The
  flip side: if you type a *longer* word in front of existing text and its first letters are a blocked
  word, that blocked prefix is deleted early (with `hell` blocked, typing `hello` right before an
  existing space deletes `hell` at the fourth letter). Typing at the very end of the text is not
  affected, and neither are words that are not blocked.
- Text edited by something other than the keyboard (pasting, dictation, the app itself) is not seen.
  The edited word is only checked the next time you press a key next to it.
- **Voice input and pasted text are not filtered.** A paste that starts with a separator does cause the
  word typed just before it to be checked.
- It only works while Sentryboard is the active keyboard.
- Digits reach editors as asynchronous key events, so an `!` entry that *ends in a digit* (such as
  `word123!`) is removed a fraction of a second (about 0.15 s) after the last digit rather than at the
  very moment it is typed. Typing another key within that moment cancels the wait, but the word is
  still removed when you type a separator.
- Because normalization strips combining marks, a blocked word in a script that uses them heavily
  (Indic scripts, Thai, Arabic vowel marks) is compared without those marks and can match other words
  with the same base letters. Word boundaries in such scripts are also approximate, since combining
  marks are not word characters.
- `@` and `$` are word characters, so a disguised word like `sh@t` or `w$rd` is one word and is caught.
  At the very start or end of a word they are ignored when matching, so `@word` and `word$` are still
  caught. The flip side is that `me@word.com` is treated as the single word `me@word`, so `word` is not
  caught inside an address.
- Multi-word phrases are not supported.

### Settings lock and uninstall protection

- **Neither is a real security boundary.** A factory reset, safe mode, recovery mode or another device
  can always remove the app.
- **Uninstall protection can be switched off from Android's own Device admin screen** without going
  through the app or its countdown.
- **"Clear storage" gets around the lock.** Android lets you clear an app's storage even while it is an
  active device admin (tested). That wipes the lock state and the word list. The device admin stays
  active, so the app still cannot be uninstalled, but the lock and the list are gone.
- The settings lock only covers this app's own settings. It does not stop you switching to a different
  keyboard; a stronger lock would need Device Owner.
- Adding a word is allowed while locked, on purpose; removing one is not.
- Hiding the list only hides it on the settings screen. You can still find out whether a particular
  word is on it by typing that word and seeing whether it is deleted.

## Building

You need a JDK (17 or newer) and the Android SDK.

```
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest        # unit tests (pure JVM; no phone needed)
```

A release build is signed if `~/.sentryboard-signing/keystore.properties` exists (see `app/build.gradle`), and left unsigned otherwise. Upstream does not commit `gradle/wrapper/gradle-wrapper.jar`. If it is missing, open the project in
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
