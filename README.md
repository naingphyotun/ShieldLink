# ShieldLink

ShieldLink is an Android app that checks a link before it opens. When you tap a link in any app,
ShieldLink checks it and warns you about gambling sites and links that VirusTotal's antivirus engines flag.

Final-year project, BE Cybersecurity, 2026.

## How it works

1. **Gambling check (local).** The domain is compared with the Blocklist Project gambling list on the device. No network needed.
2. **URL reputation (VirusTotal).** The link is looked up with the VirusTotal API. Two or more engines flagging it gives a malicious verdict.
3. **Payload check.** If VirusTotal has fetched a file behind the link, that file's hash is checked as well, so a clean-looking page that serves malware is still caught.
4. **Verdict.** Safe, malicious, or unknown. If VirusTotal can't be reached or no key is set, the result is unknown and the gambling check still works.

ShieldLink registers as a link handler (default browser), so it sees links before the browser does.
It includes a manual link checker, a QR scanner and a history screen.

## Bring your own VirusTotal key

ShieldLink does not ship with an API key. Each user adds their own free key:

1. Create a free account at https://www.virustotal.com/gui/join-us
2. Copy your key from https://www.virustotal.com/gui/my-apikey
3. In ShieldLink open **Settings**, paste the key, and tap **Save and test**

The key is checked once, then stored encrypted with the Android Keystore. It is never sent anywhere except VirusTotal.
The free key allows 4 requests per minute.

## Results

Tested on 198 links (gambling, malware, phishing and safe sites):

| Test | Correct | Accuracy |
|---|---|---|
| Round 1 (after repair) | 97 / 98 | 98.98% |
| Round 2 | 99 / 100 | 99% |
| Combined | 196 / 198 | 98.99% |

## Build

- Android Studio, Kotlin, minSdk [FILL IN], targetSdk [FILL IN]
- Clone the repo, open it in Android Studio, sync Gradle, and run on a device or emulator
- No key is needed to build. Add yours in the app after installing.

## Privacy

Links you check are sent to VirusTotal for analysis. Your API key stays on your device. ShieldLink has no account system and no servers of its own.

## Licence

Copyright (c) 2026 Naing Phyo Tun. All rights reserved.
The code is public for viewing only; see [LICENSE](LICENSE). Third-party notices are in [NOTICE.md](NOTICE.md).
