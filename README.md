# Jago Android Assistant

A privacy-focused, offline-first voice assistant for Android.

## Features
- **Wake Word**: "Jago" (using custom TFLite model).
- **Commands**:
  - "Call [Name]"
  - "Open WhatsApp"
  - "Message [Name] [Message]"
- **Privacy**: Runs completely offline (Wake word + STT).

## Setup
1. **Open in Android Studio**: Open the `JagoAssistant` folder.
2. **Model**: The `jagoo.tflite` file is located in `app/src/main/assets/`.
3. **Build**: Run the app on a physical device.

## Prerequisites
- Physical Android Device (Android 8.0+ / API 26+)
- Microphone permission
- Contacts permission
- Phone permission

## Usage
1. Open the App.
2. Grant all requested permissions.
3. Tap "Start Jago".
4. Say "Jago" (wait for beep/log).
5. Say "Call Mom" or "Open WhatsApp".

## Troubleshooting
- **Wake word not working?** Ensure volume is up and permissions are granted. Check logs via Logcat (`tag:Jago`).
- **Calls not connecting?** Ensure the contact name exists in your phone book.

## Architecture
- `WakeWordService`: Foreground service hosting custom TFLite wake-word engine.
- `SpeechService`: Modular STT (Android SpeechRecognizer or Vosk).
- `CommandParser`: Regex-based command extraction.
