# Manual Device Test Guide

## 1. Wake Word Latency
**Goal**: Verify "Hey Jago" triggers < 500ms.
1. Start the service.
2. Watch Logcat for `Wake word detected`.
3. Say "Hey Jago".
4. Verify the log appears almost instantly.

## 2. Global Voice Commands
**Goal**: Verify commands work from background.
1. Start service.
2. Press Home (put app in background).
3. Say "Hey Jago".
4. Say "Open WhatsApp".
5. Verify WhatsApp opens.

## 3. Call Flow
**Goal**: Verify Contact Resolution and Calling.
1. Create a dummy contact "Test User" with a real number.
2. Say "Hey Jago, Call Test User".
3. Verify the dialer opens or call is placed.

## 4. WhatsApp Message
**Goal**: Verify messaging intent.
1. Say "Hey Jago, Message Test User Hello World".
2. Verify WhatsApp opens to the chat with "Hello World" pre-filled.

## 5. Permission Grace
1. Go to Android Settings -> Apps -> Jago -> Permissions.
2. Deny "Microphone".
3. Open App. Verify UI shows error or requests permission again.
