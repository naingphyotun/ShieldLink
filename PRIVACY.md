# ShieldLink Privacy Policy

**Effective date:** 10 November 2026
**App:** ShieldLink (package `com.darren.shieldlink`)
**Developer:** Naing Phyo Tun
**Contact:** alitadarren@gmail.com

ShieldLink checks links before they open. This policy explains what the app does with your data. ShieldLink has no accounts and no servers of its own. The developer does not receive your links, your history or your API key.

## What ShieldLink does with your data

### Links you check
When you tap a link handled by ShieldLink, check a link in the manual checker, or scan a QR code, ShieldLink first compares the link's domain with a gambling blocklist stored on your phone. This step happens entirely on your device.

If the link is not blocked, ShieldLink sends it to **VirusTotal** (a service owned by Google) to see whether security engines flag it. If VirusTotal has a file behind the link, ShieldLink also asks VirusTotal about that file's hash. This is the main way ShieldLink works, and links are sent over an encrypted (HTTPS) connection.

**Important:** VirusTotal may keep the links you submit and may share them with its security community and partners. A link that has not been seen before may be submitted for a new scan. Do not check links that contain private information, such as password-reset links, login links or personal document links. VirusTotal's own policies apply to what it does with the data: https://docs.virustotal.com/docs/privacy-policy

### Your VirusTotal API key
You add your own free VirusTotal API key. ShieldLink stores it on your phone, encrypted with the Android Keystore. It is sent only to VirusTotal, to authorise your checks. The developer never sees it. You can remove it at any time in the app.

### Check history
ShieldLink keeps a history of links you have checked, with the verdict and time, on your phone only. It is not uploaded anywhere. You can clear it inside the app, or remove it by uninstalling the app.

### Camera
The camera is used only when you open the QR scanner, to read a QR code. Images are processed on your device and are not saved or sent anywhere. Only the link inside a code is checked, as described above.

### Permissions
- **Internet:** to contact VirusTotal.
- **Camera:** for the QR scanner.
- **Default link handler:** if you choose ShieldLink as your default browser, Android passes it the links you tap so it can check them before opening them.

## What ShieldLink does not do
- It does not create accounts or ask for your name, email or phone number.
- It does not use advertising or analytics services. 
- It does not sell your data.
- It does not send your data to the developer.

## Your choices
- Do not add an API key, and ShieldLink will not contact VirusTotal. Only the on-device gambling check will run.
- Remove your key, or clear your history, in the app at any time.
- Uninstalling the app deletes everything it stores on your phone.

## Children
ShieldLink is not directed at children under 13 and does not knowingly collect their data.

## Changes
If this policy changes, the new version will be posted at this address with a new effective date.

## Contact
Questions: Naing Phyo Tun
