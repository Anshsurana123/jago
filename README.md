<!-- Required Notice: Copyright 2026 Ansh. (https://github.com/Anshsurana123/jago) -->
<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3ddc84?logo=android&logoColor=white&style=for-the-badge" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge" />
  <img src="https://img.shields.io/badge/AI-ONNX_Runtime-005CED?logo=onnx&logoColor=white&style=for-the-badge" />
  <img src="https://img.shields.io/badge/LLM-Cerebras-FF6B35?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Min_SDK-26_(Oreo)-green?style=for-the-badge" />
</p>

<h1 align="center">🗣️ Jago — AI Voice Assistant for Android</h1>

<p align="center">
  <b>Privacy-first · Offline wake word · Hinglish-native · Zero-API GUI automation</b>
</p>

<p align="center">
  A fully voice-controlled Android assistant that understands both <b>Hindi</b> and <b>English</b>,<br/>
  executes device commands <i>without any cloud dependency for wake word detection</i>,<br/>
  and autonomously navigates app UIs through Accessibility-powered automation.
</p>

---

## ✨ Feature Highlights

### 🎙️ Wake Word Engine — `"Jaagrut"`
- **3-model ONNX pipeline** running entirely on-device:
  - `melspectrogram.onnx` → Mel-Spectrogram extraction
  - `embedding_model.onnx` → 96-dim feature embeddings
  - `jaag_ruut.onnx` → LSTM-based keyword spotting
- **Rolling hit-counter** with configurable threshold (`≥3/4` frames at `>0.85` confidence) to reject partial matches like *"jaag"* or *"jaago"*
- **Energy-gated** audio processing to skip silence
- **4.8s cooldown** after each trigger to prevent double-fires
- **Alternative activation**: Volume-down double-press via Accessibility key event interception

---

### 🧠 Natural Language Understanding

| Capability | Examples |
|---|---|
| **Phone Calls** | *"Call Mom"*, *"Dial Hemesh"*, *"Ring Papa"* |
| **WhatsApp Messaging** | *"Message Ansh that I'll be late"*, *"Text Mom saying I'm coming"* |
| **App Launch / Close** | *"Open Spotify"*, *"Launch Instagram"*, *"Close the app"* |
| **Media Controls** | *"Play music"*, *"Pause"*, *"Next song"*, *"Previous track"* |
| **Spotify Integration** | *"Play Timeless on Spotify"* — auto-searches and clicks first result via Accessibility |
| **Device Controls** | Volume, Brightness, Flashlight (with % levels), Lock Device |
| **Camera** | *"Take a photo"*, *"Click a selfie"* — launches camera + auto-triggers shutter |
| **Screenshots** | *"Take a screenshot"*, *"Screenshot and send to Mom on WhatsApp"* |
| **Alarms** | *"Set alarm for 7 AM"*, *"Wake me up in 30 minutes"* |
| **Reminders** | *"Remind me to drink water in 20 minutes"* — with multi-turn follow-ups for missing time/message |
| **Scheduled Actions** | *"Turn on flashlight at 5 PM"* — wraps any command in a time-trigger |
| **Search** | *"Search React tutorials on YouTube"*, *"Look up weather on Google"* |
| **Calculator** | *"What is 45 times 23?"*, *"Calculate 100 divided by 7"* |
| **Battery Check** | *"How much battery is left?"*, *"Battery status"* |
| **DND / Silent / Focus** | *"Enable Do Not Disturb"*, *"Silent mode"*, *"Focus mode"* |
| **Wi-Fi / Bluetooth** | *"Open Wi-Fi settings"*, *"Open Bluetooth settings"* |
| **Read Notifications** | *"Read my notifications"* — reads aloud, with *next / reply / stop* follow-up flow |
| **Read Screen** | *"Read what's on screen"* — extracts visible text via Accessibility and speaks it |
| **Send Recent Photo** | *"Send recent photo to Ansh"* — shares latest gallery image via WhatsApp |
| **Language Switching** | *"Speak in Hindi"* / *"English mein bolo"* — persisted across sessions |

---

### 🌐 Bilingual — Hindi + English + Hinglish

- **Offline Hinglish → English translator** (`HindiTranslator`) with 100+ phrase mappings for instant command normalization:
  - *"Torch jalao"* → `flashlight on`
  - *"Awaaz badhao"* → `volume up`
  - *"Screenshot lo"* → `take screenshot`
  - *"Yaad dilao"* → `remind me`
- **Message body protection** — only translates the command portion; the user's message stays untouched
- **Real-time transliteration** via Google InputTools API: Hinglish → Devanagari (e.g., *"kal milte hain"* → *"कल मिलते हैं"*)
- **Cloud translation** pipeline: Hinglish → Devanagari → English via Google Translate
- **Bilingual TTS responses** — every response has both English and Hindi variants; language auto-selects based on user preference

---

### 🤖 AI Fallback — Cerebras LLaMA 3.1

When no local command matches, the query is routed to **Cerebras Cloud** (LLaMA 3.1-8B):
- Concise 1–2 sentence responses (max 100 tokens)
- 15-second timeout with graceful fallback
- Secured API key via `local.properties` → `BuildConfig`

---

### 🖥️ Autonomous GUI Automation (Accessibility Service)

Jago doesn't just open apps — it **operates** them:

- **WhatsApp auto-send**: Primes a polling loop that detects and clicks the Send button after composing a message
- **WhatsApp search + contact selection**: Types contact name into search, clicks first result — fully hands-free
- **Spotify auto-play**: Opens search URI, then clicks the first result via node tree traversal
- **Camera shutter**: Opens camera app, waits for UI load, then triggers shutter button
- **Screenshot capture**: Uses `GLOBAL_ACTION_TAKE_SCREENSHOT`
- **Smart capture & share**: Takes screenshot → waits for save → resolves contact → shares via WhatsApp — all automated
- **Screen reader**: Recursively extracts visible text from Accessibility node tree with deduplication and noise filtering
- **App-close**: Triple back-press sequence
- **Fallback gesture click**: When node-based clicking fails, dispatches a raw gesture tap at known coordinates

---

### 📱 Notification Intelligence

- **`JagoNotificationListener`** captures incoming notifications in real-time
- **`NotificationStore`** — persistent singleton backed by `SharedPreferences` (survives process kills)
- **Deduplication** prevents duplicate entries
- **Interactive read-aloud flow**:
  1. *"Read my notifications"* → reads the first one
  2. Say *"next"* → reads the next
  3. Say *"reply main aa raha hoon"* → sends inline WhatsApp reply
  4. Say *"reply"* (without message) → prompts for message body
  5. Say *"stop"* → ends the flow

---

### 📞 Smart Contact Resolution

Multi-stage resolution pipeline with fuzzy matching:

1. **Exact match** — `"mom"` matches `"Mom"`
2. **Starts-with** — `"Hem"` matches `"Hemesh Sharma"`
3. **Contains** — `"sharma"` matches `"Hemesh Sharma"`
4. **First-name contains** — `"hemesh"` matches `"Hemesh Sharma"`
5. **Fuzzy (Levenshtein)** — `"himesh"` matches `"Hemesh Sharma"` (threshold: `len/3`, max 3)
6. **Ambiguity handling** — multiple matches → *"I found multiple contacts: X or Y. Please be more specific."*

---

### 🔧 Multi-Command & Contextual Parsing

- **Compound commands**: *"Turn on flashlight and increase volume"* → splits on `and`, `then`, `&` and executes each independently
- **Contextual phrases**: *"It's too bright"* → `BRIGHTNESS_DECREASE`, *"I can't hear"* → `VOLUME_UP`
- **Pronoun resolution**: *"Send it to Ansh"* → detects `"it"` as context reference, sends recent photo
- **Redundancy detection**: *"Volume is already at maximum"* — skips no-op adjustments
- **Intent priority system**: High-specificity intents (Screenshot, Reminder) lock first to prevent collisions with generic seeds like `"screen"`
- **Camera safety**: Photo intent blocked in question contexts (*"What is a photo?"* won't trigger camera)

---

### ⏰ Alarms & Reminders

- **Custom alarm engine** (`AlarmEngine`) with full-screen alarm activity, vibration, and snooze
- **Relative time**: *"Set alarm in 30 minutes"*
- **Absolute time**: *"Alarm at 7:30 AM"* — auto-advances to next day if time has passed
- **Reminders** with multi-turn follow-ups:
  - Missing time → *"When should I remind you?"*
  - Missing message → *"What should I remind you about?"*
  - Missing time unit → prompts for clarification

---

### 📅 Task Scheduler

- Schedule **any command** for future execution: *"Turn on flashlight at 5 PM"*
- Persisted via `SharedPreferences` + `Gson`
- Fired via `AlarmManager` with exact timing
- Dedicated **Scheduled Tasks UI** — view and manage pending tasks

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     WakeWordService                      │
│  ┌─────────┐   ┌───────────┐   ┌──────────────────────┐ │
│  │ AudioRec│──▶│ ONNX Mel  │──▶│ ONNX Embedding       │ │
│  │ (16kHz) │   │ Spectro   │   │ (96-dim)             │ │
│  └─────────┘   └───────────┘   └──────────┬───────────┘ │
│                                            ▼             │
│                                ┌───────────────────────┐ │
│                                │ ONNX LSTM Wake Word   │ │
│                                │ (rolling hit-counter) │ │
│                                └────────┬──────────────┘ │
│                                         ▼                │
│                              ┌─────────────────────┐     │
│                              │  Android STT / Vosk │     │
│                              └────────┬────────────┘     │
│                                       ▼                  │
│  ┌────────────────┐   ┌──────────────────────────────┐   │
│  │HindiTranslator │──▶│       CommandParser           │   │
│  └────────────────┘   │ (Intent Seeds + Modifiers +   │   │
│                       │  Contextual + Scheduling)     │   │
│                       └──────────────┬───────────────┘   │
│                                      ▼                   │
│  ┌──────────────────────────┐ ┌─────────────────────┐    │
│  │    ActionExecutor        │ │  CerebrasClient     │    │
│  │ (Device + App Control)   │ │  (AI Fallback)      │    │
│  └────────────┬─────────────┘ └─────────────────────┘    │
│               ▼                                          │
│  ┌────────────────────────┐  ┌────────────────────────┐  │
│  │ JagoAccessibilityServ  │  │      JagoTTS           │  │
│  │ (GUI Automation)       │  │  (Bilingual Speech)    │  │
│  └────────────────────────┘  └────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

**Key Components:**

| Module | Responsibility |
|---|---|
| `WakeWordService` | Foreground service: ONNX inference, STT, command routing, follow-up state machine |
| `CommandParser` | Intent-seed matching, modifier detection, contextual parsing, scheduling extraction |
| `ActionExecutor` | Executes 30+ command types — calls, media, device controls, automation |
| `JagoAccessibilityService` | GUI traversal, node clicking, auto-send, screen reading, key interception |
| `JagoNotificationListener` | Captures live notifications from all apps |
| `NotificationStore` | Persistent notification buffer with deduplication |
| `HindiTranslator` | Offline Hinglish→English phrase mapping (100+ entries) |
| `TranslationClient` | Cloud transliteration (Hinglish→Devanagari) + translation (Hindi→English) |
| `CerebrasClient` | LLaMA 3.1 API for open-domain Q&A fallback |
| `ContactResolver` | 5-stage fuzzy contact matching pipeline |
| `JagoTTS` | Bilingual text-to-speech with language persistence and callback chaining |
| `ScheduledTaskEngine` | Persistent task scheduler with `AlarmManager` integration |
| `CalculatorEngine` | Inline math expression evaluator |

---

## 🚀 Getting Started

### Prerequisites

- Android device running **Android 8.0+ (API 26)**
- **Android Studio** (latest stable)
- Physical device recommended (wake word needs microphone access)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/Anshsurana123/jago.git
   cd jago
   ```

2. **Configure API keys** — create/edit `local.properties`:
   ```properties
   CEREBRAS_API_KEY=your_cerebras_key_here
   GEMINI_API_KEY=your_gemini_key_here
   ```

3. **Open in Android Studio** → Sync Gradle → Build

4. **Deploy to device** and grant permissions:
   - 🎤 Microphone
   - 📇 Contacts
   - 📞 Phone
   - 🔔 Notification Access
   - ♿ Accessibility Service
   - 📱 Device Admin *(optional — for lock device)*

### Usage

1. Launch the app → tap **"Start Jago"**
2. Say **"Jaagrut"** (or double-press volume down) → overlay appears
3. Speak your command in **English, Hindi, or Hinglish**
4. Jago executes, responds bilingually, and auto-dismisses

---

## 🛡️ Privacy

- **Wake word detection** is 100% offline — no audio leaves the device
- **STT** runs on-device via Android's `SpeechRecognizer` (or optional offline Vosk)
- **Cloud calls** are made only for:
  - AI fallback (Cerebras) — when no local command matches
  - Message translation (Google InputTools / Translate)
- All API keys are stored in `local.properties` (git-ignored) and accessed via `BuildConfig`

---

## 📁 Project Structure

```
app/src/main/
├── assets/
│   ├── melspectrogram.onnx        # Mel-spectrogram model
│   ├── embedding_model.onnx       # Feature embedding model
│   └── jaag_ruut.onnx             # Wake word LSTM model
├── java/com/example/jago/
│   ├── MainActivity.kt
│   ├── JagoApp.kt
│   ├── logic/
│   │   ├── CommandParser.kt       # NLU engine (1050+ lines)
│   │   ├── ActionExecutor.kt      # Command execution (1165+ lines)
│   │   ├── ContactResolver.kt     # Fuzzy contact matching
│   │   ├── HindiTranslator.kt     # Offline Hinglish mapping
│   │   ├── TranslationClient.kt   # Cloud transliteration
│   │   ├── CerebrasClient.kt      # LLM fallback
│   │   ├── JagoTTS.kt             # Bilingual TTS
│   │   ├── CalculatorEngine.kt    # Math evaluator
│   │   ├── NotificationStore.kt   # Persistent notification buffer
│   │   ├── ReminderEngine.kt      # Reminder scheduling
│   │   ├── FuzzyMatcher.kt        # Levenshtein distance
│   │   └── BatteryReceiver.kt     # Battery monitoring
│   ├── service/
│   │   ├── WakeWordService.kt     # Core foreground service (1030+ lines)
│   │   ├── JagoAccessibilityService.kt  # GUI automation (840+ lines)
│   │   ├── JagoNotificationListener.kt  # Notification capture
│   │   ├── ReminderReceiver.kt    # Alarm broadcast receiver
│   │   ├── alarm/                 # Custom alarm system
│   │   └── speech/                # STT adapters (Android + Vosk)
│   ├── scheduler/                 # Scheduled task system
│   └── ui/                        # Assistant overlay & activities
```

---

## 📄 License

This project is proprietary. All rights reserved.

---

<p align="center">
  Built with ❤️ in Kotlin · Powered by ONNX Runtime + Cerebras AI
</p>
