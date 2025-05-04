# SafeKeyboard

**SafeKeyboard** is a custom Android keyboard (IME) designed for monitoring and logging typed messages across applications for research or parental control purposes. It functions like a regular QWERTY keyboard but with added capabilities for secure data capture and analysis.

## Features

- 🔠 **Standard QWERTY Layout** with symbol toggling and caps lock functionality
- ✍️ **Message Logging**: Automatically detects when a message is sent by monitoring when input fields are cleared (common behavior in messaging apps).
- 🕵️ **Per-Message Tracking**: Captures each sent message along with:
    - `userId` (persistent UUID)
    - `message` content
    - `timestamp`
    - `packageName` of the app where the message was typed
- 📁 **CSV Storage**: All messages are saved to a local CSV file with a header for easy import into analysis tools.
- ☁️ **Batch Upload**: Once a threshold (e.g., 50 messages) is reached, the log is sent to a configurable cloud API endpoint.
- 🧠 **Shift Behavior**: Handles single-tap shift (uppercase once) and double-tap shift (caps lock mode).
- 🔒 **Invisible Padding Keys**: Maintains visual symmetry in rows using transparent dummy keys that cannot be pressed.

> **Note:** This keyboard does not display popups for key presses and is optimized for discreet, non-intrusive monitoring.

## Disclaimer

This app is intended for ethical use only — such as controlled user studies, accessibility features, or parental supervision with full consent. Do not use this app to collect data without users' knowledge or permission.
