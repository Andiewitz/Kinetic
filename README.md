# Kinetic 📡

An elegant, high-fidelity **Bluetooth Low Energy (BLE) Scanner and Device Tracker** designed with a signature **Bento Grid** theme and built on modern Android development paradigms.

---

## 🎨 Bento Design Theme

Kinetic leverages the Material Design 3 (M3) dynamic layout patterns to offer an intuitive visual dashboard reminiscent of modern hardware telemetry decks:
- **Clean Grid Segments**: Segmented card units for device properties, telemetry stats, and custom sandboxing.
- **Dynamic Color Accents**: Context-aware styling blocks for connected/unconnected states and RSSI categories.
- **Micro-interactions**: Seamless smooth-scrolling logs, pulsing radar animations, and interactive drag-to-model range simulations.

---

## 🛠️ Tech Stack & Architecture

- **Jetpack Compose**: Declarative, responsive UI entirely modeled around modern container boundaries.
- **Orbit MVI Framework**: Unidirectional data flow (UDF) structure guiding UI states, actions, and side effects.
- **Room Database**: Local SQL caching and persistence for custom tags, classification names, and physical location notes.
- **Voyager Navigation**: Secure state-saving, type-safe backstack transitions.
- **Kotlin Coroutines**: Highly scalable asynchronous pipelines handling concurrent BLE background tasks and radar sweeps safely.
