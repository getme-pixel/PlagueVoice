# Dottore della Peste - Halloween voice changer

An Android app that takes your microphone, turns your voice into a low, gravelly
plague-doctor voice, and plays it out through a Bluetooth speaker / stereo. It runs
in the background and only stops when you fully close the app.

This repo contains:

- `app/` - the Android app (pure Java, no 3rd-party audio libraries, so it builds anywhere).
- `.github/workflows/build.yml` - a GitHub Action that builds and **signs the APK** for you
  and attaches it to a release, so you never have to install the Android SDK yourself.
- `docs/index.html` - the download web page (the one with the **Download APK** button).

---

## Step 1 - Get the APK built (no Android tools needed on your PC)

1. Create a free GitHub account if you do not have one and create an empty repository
   (for example `plague-voice`).
2. Upload this whole folder to that repository (web upload is fine, or `git push`).
3. Open the repo on GitHub, go to the **Actions** tab. The `Build APK` workflow runs
   automatically. Wait for the green check.
4. Open the **Releases** section. The APK is attached there as `PlagueVoice.apk`.
   Your permanent download link is:

   ```
   https://github.com/<YOUR_USER>/<YOUR_REPO>/releases/latest/download/PlagueVoice.apk
   ```

If the workflow does not start on its own, open **Actions -> Build APK -> Run workflow**.

## Step 2 - Point the web page at your APK

Open `docs/index.html` and replace `YOUR_USER` / `YOUR_REPO` in this line with your details:

```js
var APK_URL = "https://github.com/YOUR_USER/YOUR_REPO/releases/latest/download/PlagueVoice.apk";
```

### Publish the web page (optional, so you can open it on your Samsung phone)

In the repo go to **Settings -> Pages**, set **Source = Deploy from a branch**,
**Branch = main**, **Folder = /docs**, save. After a minute your page is live at:

```
https://<YOUR_USER>.github.io/<YOUR_REPO>/
```

Open that on your phone and tap **Download APK**.

## Step 3 - Install on the phone

1. Open the APK on the phone. Android will ask to allow installs from this source -
   allow it (this is normal for any app you make yourself).
2. Tap **Install**, then **Open**.
3. The first time you press **Start**, allow the **microphone** permission.

## Step 4 - Use it

1. Pair your Bluetooth speaker / stereo **before** opening the app.
2. Open **Dottore della Peste**.
3. Tap **Refresh audio devices**.
4. Pick your Bluetooth speaker in the **Output** list (it is selected automatically
   if a Bluetooth device is connected).
5. Tap **Start voice changer** and talk. The changed voice comes out of the speaker.

Sliders:

- **Pitch** - how low the voice is (default -7 semitones). Slide further left for a
  deeper demon-voice effect.
- **Growl / gravel** - raspiness / distortion.
- **Cave reverb** - roomy, echoing, creepy space.

The app keeps running when it is in the background (there is an ongoing notification
with a **Stop** button). Close the notification or swipe the app away to stop it.

---

## Important real-world notes

- **Bluetooth adds delay.** Wireless output is roughly 0.15-0.3 s behind your voice.
  For a costume coming out of a stereo this usually sounds spooky rather than wrong.
  Wired output (3.5 mm or USB-C) has much less delay if you want it tighter.
- **One Bluetooth device at a time, effectively.** Android cannot reliably use one
  Bluetooth mic *and* a different Bluetooth speaker as two separate links in an app.
  The dependable setup is: **phone mic (or a wired mic) -> effects -> one Bluetooth
  speaker**. That is what this app is built and tuned for.
- **Microphone permission is mandatory** - without it the app cannot hear you.
- If your phone aggressively kills background apps, add this app to the
  "never sleeping"/battery-unrestricted list in Android settings.

## Building locally instead (optional)

You need JDK 17 and Gradle 8.7 (or open the folder in Android Studio, which will
create the wrapper for you):

```
gradle assembleRelease
```

The APK appears in `app/build/outputs/apk/release/app-release.apk`. When no
`keystore.properties` is present, the release build is signed with the debug key so it
is still installable. To sign with your own key, create `keystore.properties`:

```
storeFile=/absolute/path/to/your.jks
storePassword=yourpassword
keyAlias=youralias
keyPassword=yourpassword
```

## Project layout

```
app/src/main/java/com/plaguevoice/
  MainActivity.java          - UI: start/stop, device pickers, sliders
  VoiceService.java          - foreground service (background + stop-on-close)
  AudioEngine.java           - mic capture -> processor -> speaker output
  PlagueVoiceProcessor.java  - the DSP voice chain (pitch, muffle, growl, reverb)
  DeviceUtils.java           - lists/resolves audio input & output devices
```
