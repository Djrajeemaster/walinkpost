# WA Link Poster (Android Only)

Small Android app to post generated links one-by-one into an already open WhatsApp chat/channel.

Flow:
1. Paste URLs (one per line)
2. Start
3. App opens WhatsApp
4. You keep target chat/channel screen visible
5. App pastes link -> waits preview delay -> taps send -> repeats

## Important
- This is accessibility-based UI automation.
- WhatsApp UI updates can break IDs/behavior.
- Use carefully; third-party app automation may violate platform policies.
- Keep screen ON and WhatsApp foreground for best results.

## Project Path
- `tools/wa-link-poster-android`

## Build (Android Studio)
1. Open Android Studio
2. Open folder: `tools/wa-link-poster-android`
3. Let Gradle sync
4. Run app on your Android phone (USB debugging)

## Build Without Android Studio (Recommended for you)
1. Push these files to your GitHub repo.
2. Open GitHub -> Actions tab.
3. Run workflow: `Build WA Link Poster APK`.
4. After workflow success, open the run -> `Artifacts`.
5. Download `wa-link-poster-debug-apk` and extract `app-debug.apk`.
6. Copy APK to your phone and install.

Note: On phone, allow installation from unknown sources for your browser/files app.

## First-time Setup on Phone
1. Open app
2. It will ask you to open Accessibility Settings
3. Enable `WA Link Poster` accessibility service
4. Go back to app

## Usage
1. Paste generated links (one URL per line)
2. Set:
   - Preview wait seconds (example: 8)
   - Next link delay seconds (example: 3)
3. Tap Start
4. WhatsApp opens
5. Open target channel/chat once and keep it visible
6. App will send links one-by-one

## Notes
- If preview is slow, increase preview wait seconds.
- If links send too fast, increase next delay.
- Stop button immediately stops queue.

## Future Improvements (if needed)
- Auto-open specific chat by name
- Pause/Resume button
- Persistent queue after app restart
- Retry failed sends
