# Releasing Lantern Sudoku

Three channels, all built from the same tag:

| Channel | Build | Signed by |
|---|---|---|
| **GitHub Releases** | `fdroid` flavour APK | Our release key (`lantern-release`) |
| **F-Droid** | `fdroid` flavour, built by F-Droid from the tag | F-Droid's key |
| **Google Play** | `play` flavour app bundle (AAB) | Our upload key (`lantern-upload`); Play re-signs it with the app-signing key (Play App Signing) |

The three builds have different signers, so a phone can't update one with another. To switch channels, uninstall first (your games are lost unless Android backup restores them).

Nothing secret is ever in this repository: keys live outside it, passwords live in your user-level Gradle properties, and `*.jks`, `*.keystore` and `keystore.properties` are git-ignored.

## Versioning

- `versionName` is SemVer: `1.0.0`.
- `versionCode = MAJOR × 10000 + MINOR × 100 + PATCH`: 1.0.0 = **10000**, 1.0.1 = 10001, 1.1.0 = 10100.
- Release candidates sit just below their release: `X.Y.Z-rcN` = that release's code − 100 + N (1.0.0-rc3 = 9903).
- Tags are `vX.Y.Z`, on a clean commit on `main`. A released version is never rebuilt or overwritten; a fix is a new version.

## Signing keys (one time)

| Key | File (outside the repo) | Alias | Gradle properties prefix | Used for |
|---|---|---|---|---|
| Release key | `~/.android-keys/lantern-release.jks` | `lantern-release` | `lantern.release.*` | GitHub Releases APKs (`fdroid` flavour) |
| Upload key | `~/.android-keys/lantern-upload.jks` | `lantern-upload` | `lantern.upload.*` | Play app bundles (`play` flavour) |

Create each key (you'll be asked for a password and a name; use a long, unique password from a password manager):

```bash
mkdir -p ~/.android-keys && chmod 700 ~/.android-keys
keytool -genkeypair -v -keystore ~/.android-keys/lantern-release.jks -alias lantern-release \
  -keyalg RSA -keysize 4096 -validity 10000
keytool -genkeypair -v -keystore ~/.android-keys/lantern-upload.jks -alias lantern-upload \
  -keyalg RSA -keysize 4096 -validity 10000
chmod 600 ~/.android-keys/lantern-*.jks
```

Then add the properties to `~/.gradle/gradle.properties` (your home folder, **not** this repo's `gradle.properties`):

```properties
lantern.release.storeFile=/Users/<you>/.android-keys/lantern-release.jks
lantern.release.storePassword=<password>
lantern.release.keyAlias=lantern-release
lantern.release.keyPassword=<password>
lantern.upload.storeFile=/Users/<you>/.android-keys/lantern-upload.jks
lantern.upload.storePassword=<password>
lantern.upload.keyAlias=lantern-upload
lantern.upload.keyPassword=<password>
```

Without these properties, release builds are unsigned (that's what CI and F-Droid do).

**Back up both keys and their passwords on the day you create them:** the password manager plus one offline copy (for example an encrypted USB drive kept somewhere else). Losing `lantern-release.jks` means GitHub installs can never be updated again. A lost upload key can be reset through Play support, but that takes days.

### Certificate fingerprints

Print a fingerprint with `keytool -list -v -keystore ~/.android-keys/lantern-release.jks -alias lantern-release | grep SHA256`, or check a built APK with `apksigner verify --print-certs <apk>`.

| Certificate | SHA-256 |
|---|---|
| Release key (`lantern-release`): GitHub Releases APKs | `<to be filled in after the key is created>` |
| Upload key (`lantern-upload`): Play uploads | `<to be filled in after the key is created>` |
| Play app-signing key (held by Google): Play installs | `<from Play Console → Test and release → App integrity, after the first upload>` |
| F-Droid signing key: F-Droid installs | `<from the F-Droid package page after the first F-Droid build>` |

Users can compare the GitHub APK they downloaded against the release-key fingerprint with `apksigner verify --print-certs`.

## Every release

1. **Version.** In `app/build.gradle.kts` set `versionName` and `versionCode` (formula above).
2. **Release notes.** Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (plain text, ≤ 500 characters). Update the other fastlane texts if the app changed what it does.
3. **Baseline Profile.** On a device or emulator (API 33+): `./gradlew :app:generateBaselineProfile`, and commit the regenerated profile.
4. **Gates.** `scripts/ci.sh` must be green: tests, bank validator, lint for both flavours, R8 release builds with the licence gate, the permission gate (no permissions) and the size gate.
5. **Commit and tag** on a clean tree:
   ```bash
   git status --porcelain            # must print nothing
   git commit -am "release: X.Y.Z"
   git tag -a vX.Y.Z -m "Lantern Sudoku X.Y.Z"
   git push origin main vX.Y.Z
   ```
6. **Signed builds** from the tagged commit (needs the keys and properties above):
   ```bash
   ./gradlew clean assembleFdroidRelease bundlePlayRelease assemblePlayRelease
   apksigner verify --print-certs app/build/outputs/apk/fdroid/release/app-fdroid-release.apk   # release-key fingerprint
   ```
   Outputs: `app/build/outputs/apk/fdroid/release/app-fdroid-release.apk`, `app/build/outputs/bundle/playRelease/app-play-release.aab`, `app/build/outputs/apk/play/release/app-play-release.apk`, and R8 mappings in `app/build/outputs/mapping/{fdroidRelease,playRelease}/mapping.txt`.
7. **Archive** (maintainer only; the archive lives outside this repo and is never committed). Run from this repo, still on the tagged commit:
   ```bash
   V=X.Y.Z; T=$(mktemp -d)
   gzip -c app/build/outputs/mapping/playRelease/mapping.txt   > $T/play-mapping.txt.gz
   gzip -c app/build/outputs/mapping/fdroidRelease/mapping.txt > $T/fdroid-mapping.txt.gz
   ~/Desktop/PersonalProjects/builds/archive-build.sh lantern-sudoku $V \
     app/build/outputs/bundle/playRelease/app-play-release.aab=lantern-sudoku-$V-play.aab \
     app/build/outputs/apk/play/release/app-play-release.apk=lantern-sudoku-$V-play.apk \
     app/build/outputs/apk/fdroid/release/app-fdroid-release.apk=lantern-sudoku-$V-fdroid.apk \
     $T/play-mapping.txt.gz=mapping/lantern-sudoku-$V-play-mapping.txt.gz \
     $T/fdroid-mapping.txt.gz=mapping/lantern-sudoku-$V-fdroid-mapping.txt.gz
   ```
   Check that `BUILD-INFO.txt` shows the tag and "Dirty tree: no". Add a short `RELEASE.md` next to the files (what each file is, how it's signed, where it went) and update the Apps table in the archive's `README.md`.
8. **GitHub release** with the `fdroid` APK signed by the release key:
   ```bash
   cp app/build/outputs/apk/fdroid/release/app-fdroid-release.apk $T/lantern-sudoku-$V.apk
   ( cd $T && shasum -a 256 lantern-sudoku-$V.apk > lantern-sudoku-$V.apk.sha256 )
   gh release create v$V $T/lantern-sudoku-$V.apk $T/lantern-sudoku-$V.apk.sha256 \
     --title "Lantern Sudoku $V" --notes-file fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
   ```
   Add the release-key fingerprint and the "uninstall to switch channels" note to the release text.
9. **F-Droid** picks up the new tag by itself (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`), usually within a few days.
10. **Google Play:** upload `app-play-release.aab` in Play Console (internal testing first, then production), paste the changelog as "What's new", and upload the Play mapping (gunzipped) under *Android vitals → Deobfuscation files* if Play doesn't pick it up from the bundle.

## F-Droid: first submission (one time)

Prerequisites: the repository is public; tag `v1.0.1` exists; `fastlane/metadata/android/en-US/` has the texts and the images (`icon.png`, `featureGraphic.png`, `phoneScreenshots/`); the metadata file is in `fdroid/io.github.ukpratik.lanternsudoku.yml`.

1. Check locally with fdroidserver against the public tag: `fdroid lint`, `fdroid rewritemeta`, `fdroid build -v -l io.github.ukpratik.lanternsudoku`. The built APK must pass the permission gate (no permissions).
2. Fork https://gitlab.com/fdroid/fdroiddata and add `metadata/io.github.ukpratik.lanternsudoku.yml` (the same content as `fdroid/…yml` here) on a branch named `io.github.ukpratik.lanternsudoku`.
3. Open a merge request with the **App inclusion** template. Checklist answers: public source, GPL-3.0-or-later; no non-free dependencies in the `fdroid` flavour; no tracking, no ads and no network permission (no anti-features); builds with the Gradle wrapper and the Android SDK only; the puzzle bank (`bank/v1/`) is plain text and can be regenerated with `tools/generator`.
4. Answer reviewers in the merge request. After the merge, the app appears in F-Droid in about a week.

## Google Play: first release (one time, after the organisation developer account is verified)

1. Create the app: name "Lantern Sudoku: Learn & Unwind", default language English (United States), **Game**, **Free**.
2. Accept Play App Signing (Google-managed key). The first AAB registers the upload key.
3. Store listing from `fastlane/metadata/android/en-US/` (category **Puzzle**). Privacy policy: https://ukpratik.github.io/lantern-sudoku/privacy/
4. App content: no ads; all functionality without special access; content rating questionnaire (no objectionable content; expected Everyone / PEGI 3); target audience (not designed for children); Data safety **No data collected, no data shared** (the app has no permissions and sends nothing; Android's own backup goes to the player's Google account and is never readable by us).
5. Internal testing first, then production. Record the Play app-signing certificate fingerprint in the table above.
