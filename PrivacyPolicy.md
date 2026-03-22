# Privacy Policy

**Effective Date**: 2026-02-11

## 1. Overview
Jago Assistant is designed with privacy as a priority. All voice processing (Hotword detection) is performed **on-device** using a custom TensorFlow Lite model. Speech-to-Text conversion is performed either on-device (via Vosk) or using Android's built-in Speech Recognizer (which may use network depending on OS settings).

## 2. Data Collection
- **Microphone**: Used only to listen for the wake word "Jago" and subsequent commands. Audio is not recorded or stored permanently.
- **Contacts**: Accessed locally to resolve names to phone numbers for calling and messaging features. Contact data is **never** uploaded to any external server by this app.

## 3. Permissions
The app requests the following sensitive permissions:
- `RECORD_AUDIO`: To hear commands.
- `READ_CONTACTS`: To find "Mom" or "John" in your address book.
- `CALL_PHONE`: To initiate calls on your behalf.

## 4. Third-Party Services
- **TensorFlow Lite**: Used for custom wake word detection. Runs locally on-device.
- **Vosk**: Used for offline speech recognition. Runs locally.
- **Android Speech Recognizer**: Used as a fallback. Subject to Google's Privacy Policy if online recognition is active.

## 5. Control
You can stop the "Always Listening" service at any time by tapping the "Stop Jago" button in the app.
