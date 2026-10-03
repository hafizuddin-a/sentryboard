# Sentryboard

Sentryboard is a modified fork of [Simple Keyboard](https://github.com/rkkr/simple-keyboard),
which is itself based on AOSP LatinIME. It keeps Simple Keyboard's small size and adds three things:

1. **A word filter**: words on a list you choose are deleted from the text field as you type them.
2. **A settings lock**: the filter's settings can be locked so that changing them means waiting out
   a countdown.
3. **Uninstall protection**: an optional layer that makes the app harder to uninstall while the
   settings lock is on.

There is no network access (there is no `INTERNET` permission), no analytics, and no accounts. Every
permission it asks for is listed under [Permissions](#permissions), with the reason. This is a
self-imposed friction tool for a phone you own, not a security product: see
[Known limitations](#known-limitations) for what it cannot stop.

Licensed under Apache License 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE). Everything below the
next horizontal rule is upstream's README, unchanged.

## What it does

When you finish a word by typing a space, punctuation or Enter (including Enter as Send / Go / Next),
Sentryboard checks the word immediately before the cursor against your list. If it is on the list,
the word is deleted and the separator you typed is inserted as usual. There is no toast or dialog.

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
duration. While locked, you can still **view** the blocked-word list, but you cannot change the
on/off switch, add or remove entries, change the pause options, or change or remove the lock itself.
Keyboard settings such as theme and height are not affected.

To edit again, tap **Unlock**. A full-screen page counts down the length you chose; you must stay on
it until it finishes. Then the settings are editable until you leave the settings, or for 5 minutes,
or until the phone restarts, whichever comes first.

- The countdown is timed with the phone's monotonic clock, so changing the clock does not shorten it.
- **Leaving the page resets it to zero**: Home, Recents, another app, the screen turning off, Back or
  Cancel. Rotating the phone, changing the font size or switching dark mode do not.
- The countdown is never saved. If the app is killed mid-countdown the lock simply stays on.
- The lock's length is fixed while it is on. It can be changed only while the settings are unlocked,
  and applies the next time you lock.
- The lock is enforced in the screens **and** underneath them: the word list and the filter settings
  refuse to be written while locked, whatever a screen does. If the saved lock state ever looks
  corrupt, the lock is treated as **on** (with a 30 minute countdown).
- Lengths from 1 minute to 24 hours (test builds also allow a few seconds, so the lock can be tried
  without waiting).

## Uninstall protection

Under *Settings > Uninstall protection*. Android has exactly one hard rule for an ordinary app: **an
active device admin cannot be uninstalled until it is deactivated first.** So Sentryboard can be a
device admin, and asks for **no** device policies: it cannot lock, wipe or control the phone. That
alone is only a small speed bump, because the Uninstall button takes you straight to the screen that
deactivates it.

So there is a second layer, the **guard**. While uninstall protection and the settings lock are both
on, a foreground service (with a permanent notification, "Uninstall protection active") watches which
screen is showing, and if it is Android's device-admin screen it covers it with a message and two
buttons, *Open app* and *Go home*. Removing protection then has to go through the settings lock's
countdown: unlock the settings, then use **Remove uninstall protection** inside the app.

- The guard is on only when: protection is on, the settings lock is on and not open for editing, the
  screen is on, and every permission below is granted. With no lock it is off (there would be no way to
  earn removal), and the plain device admin remains.
- If a permission is revoked later, the setup card turns red and the notification says the guard is
  degraded. If Android stops the guard from covering the screen, it says that too.
- If protection is switched off some other way (from Android's own Device admin screen while the lock
  is on), the app records it, posts a notification, and shows a banner with a one-tap **re-enable**.
- The cover never traps you: the system's Home, Back and Recents keys are outside it, its own Back key
  goes Home, and it closes as soon as the guarded screen is no longer in front.
- It only guards the device-admin screen. It does not try to intercept generic uninstall dialogs or
  App info pages, which are shared by every app.

### Setup

Open *Settings > Uninstall protection*. The **Guard setup** card lists what is needed; tap a row with a
cross to open the right system screen: **Device admin** (activate), **Usage access**, **Display over
other apps**, and **Notifications**. There is also a shortcut to battery-optimization settings, which
matters on phones that stop background services to save power. Then turn the settings lock on under
*Word filter*.

### Which screens are covered

The screens are listed in [`guard_targets.xml`](app/src/main/res/xml/guard_targets.xml) as a package
and part of a class name. The default is the stock Android device-admin screen
(`com.android.settings` / `DeviceAdminAdd`), confirmed on an Android 16 emulator. **Manufacturers
rename these screens**, so on another phone you may need to add its entry. A test build has a
*Foreground readout* row on the Uninstall protection screen that shows the package and class of the
screen in front, to find the names. It stores nothing.

### How the cover is shown

On Android 16, an overlay window (the "Display over other apps" kind) is **rejected** on the
device-admin screen: Android hides other apps' overlays over that screen and even reports the
permission as missing while it is showing. Starting a cover *activity* from the foreground service
works, so that is what the guard does. It tries the activity first; if the screen is still uncovered
after a moment it tries again and then falls back to an overlay; if that also fails it reports itself
as blocked instead of failing silently. Background-start rules differ between Android versions and
manufacturers, so this is something to check on your own phone.

## Permissions

Everything else Simple Keyboard asked for (`VIBRATE`) is unchanged. `INTERNET` is never requested.

| Permission or access | Why | Needed for |
|---|---|---|
| Device admin (`BIND_DEVICE_ADMIN` on the receiver) | Makes Android require deactivation before uninstall. Declares no policies. | Uninstall protection |
| `PACKAGE_USAGE_STATS` (you grant it under *Usage access*) | To see which screen is in front, without an accessibility service. It reads nothing else. | The guard |
| `SYSTEM_ALERT_WINDOW` (you grant it under *Display over other apps*) | Draws the fallback cover, and is what lets the guard start the cover on older Android versions. | The guard |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the guard running. | The guard |
| `POST_NOTIFICATIONS` | The guard's permanent notification, and the "protection was turned off" alert. | The guard |
| `RECEIVE_BOOT_COMPLETED` | Restarts the guard after the phone restarts. | The guard |

Not used, on purpose: accessibility services, Device Owner or Profile Owner, any owner-only device
policy API, wipe or password-reset policies, hiding the app icon, or obfuscation. The app also turns
Android's automatic backup off and excludes everything from data extraction, so the lock and the list
cannot be restored from an older copy.

## Removing everything

1. Open *Settings > Word filter*, tap **Unlock** and wait for the countdown.
2. Still in the app, open *Uninstall protection* and tap **Remove uninstall protection**.
3. Uninstall the app normally.

If you cannot get through that (for example the app will not open): restart the phone in **safe mode**
(third-party apps do not run, so the guard is off), go to *Settings > Security > Device admin apps*,
deactivate Sentryboard, then uninstall it. A factory reset also removes it. Turning the lock off
does not delete your word list; uninstalling does.

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
- `@` and `$` are word characters, so a disguised word like `sh@t` or `w$rd` is one word and is caught.
  At the very start or end of a word they are ignored when matching, so `@word` and `word$` are still
  caught. The flip side is that `me@word.com` is treated as the single word `me@word`, so `word` is not
  caught inside an address.
- Multi-word phrases are not supported.

### Settings lock and uninstall protection

- **Neither is a real security boundary.** A factory reset, safe mode, recovery mode or another device
  can always remove the app.
- **"Clear storage" gets around the lock.** Android lets you clear an app's storage even while it is an
  active device admin (tested). That wipes the lock state and the word list. The device admin stays
  active, so the app still cannot be uninstalled, but the lock and the list are gone.
- The guard depends on **Usage access** and the other permissions. If you revoke them it stops, and the
  app shows a warning but cannot prevent it.
- The guard relies on Settings class names that vary by manufacturer and Android version; update
  `guard_targets.xml` for your phone.
- While the guard is active, the **Device admin screens of other apps are also covered**, because the
  class name does not say which admin is being shown.
- **The cover is not instant.** The screen is checked about every 0.75 s and the cover then has to
  start, so the device-admin screen is usable for roughly a second. Someone quick enough could tap
  before it appears.
- Background services can be killed by aggressive battery managers. On the emulator the guard came back
  after the system killed the app (it is restarted by Android) and after a restart of the phone. After
  **Force stop** it stays off until the app is opened, the keyboard is used, or the phone restarts.
- The settings lock only covers this app's own settings. It does not stop you switching to a different
  keyboard; a stronger lock would need Device Owner.
- Polling costs about 0.2% of one CPU core on the emulator with the screen on, and nothing with it off.
  A real battery figure over an hour of use has not been measured.

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
