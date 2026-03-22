# Jarvis Android Assistant

A privacy-focused, offline-first voice assistant for Android.

## Features
- **Wake Word**: "Hey Jarvis" (using Picovoice Porcupine).
- **Commands**:
  - "Call [Name]"
  - "Open WhatsApp"
  - "Message [Name] [Message]"
- **Privacy**: Runs completely offline (Wake word + STT).

## Setup
1. **Open in Android Studio**: Open the `JarvisAssistant` folder.
2. **AccessKey**: The `local.properties` file has been pre-configured with the provided Porcupine AccessKey.
3. **Build**: Run the app on a physical device.

## Prerequisites
- Physical Android Device (Android 8.0+ / API 26+)
- Microphone permission
- Contacts permission
- Phone permission

## Usage
1. Open the App.
2. Grant all requested permissions.
3. Tap "Start Jarvis".
4. Say "Hey Jarvis" (wait for beep/log).
5. Say "Call Mom" or "Open WhatsApp".

## Troubleshooting
- **Wake word not working?** Ensure volume is up and permissions are granted. Check logs via Logcat (`tag:Jarvis`).
- **Calls not connecting?** Ensure the contact name exists in your phone book.

## Architecture
- `WakeWordService`: Foreground service hosting Porcupine.
- `SpeechService`: Modular STT (Android SpeechRecognizer or Vosk).
- `CommandParser`: Regex-based command extraction.
