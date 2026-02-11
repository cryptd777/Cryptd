# Cryptd VM (Android)

An Android app that boots **arm64 Linux ISOs** using QEMU, with an embedded VNC viewer.

## Features
- Select ISO/QCOW2/RAW from storage (SAF)
- Configure RAM/CPU
- VNC display inside the app
- Raw disk creation (persistent disk)
- QEMU bundle shipped in assets

## Requirements
- **ARM64 device**
- **ARM64/aarch64 Linux ISO**

## How It Works
- The app copies the selected ISO/disk into app-private storage.
- QEMU is extracted from assets into app-private storage and executed.
- Display is provided by an embedded VNC client.

## Notes
- QEMU bundle adds ~262MB to APK size.
- For best performance, use virtio devices and reasonable RAM.

## Build
Open with Android Studio or build with Gradle:

```bash
./gradlew :app:assembleDebug
```

## Usage
1. Install the APK.
2. Pick an ARM64 ISO.
3. Tap **Start VM**.

If you want to install an OS, create a **RAW disk** first and boot the ISO.

## License
Internal / private project.
