# NIMELSSA Vault

> **NIMELSSA** – *Nigerian Medical Laboratory Science Students Association* Academic Vault

A native Android application for curating, accessing, and managing Medical Laboratory Science (MLS) academic resources. Built for students by students.

## Features

- **Authentication Gateway** — Secure sign-in / sign-up with role-based access (Student / Class Rep)
- **Course Repository** — Browse lecture notes and past questions filtered by level and semester
- **In-App Document Viewer** — Read notes and PQs with page navigation
- **Resource Proposals** — Students can submit Google Drive links or local files for moderation
- **Rep Approval Queue** — Level Representatives review and publish student submissions
- **Personal Profile** — View account details and institution information
- **CBT Exam Portal** — Practice multiple-choice questions with instant scoring
- **Email Change Request** — Update registered email with OTP verification

## Tech Stack

| Layer           | Technology                              |
|-----------------|-----------------------------------------|
| Language        | Kotlin                                  |
| UI              | Jetpack Compose (Material 3)            |
| Architecture    | Single-Activity, Composable Navigation  |
| Navigation      | Navigation Compose (NavHost)            |
| State Mgmt     | StateFlow + Compose `collectAsState`    |
| CI/CD           | GitHub Actions (APK build)              |
| Min SDK         | Android 8.0 (API 26)                    |
| Target SDK      | Android 14 (API 34)                     |

## Project Structure

```
NIMELSSA/
├── app/
│   ├── build.gradle.kts          # App-level Gradle config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nimelssa/vault/
│       │   ├── MainActivity.kt           # Entry point + navigation
│       │   ├── data/
│       │   │   ├── Course.kt             # Course data model
│       │   │   ├── CourseRepository.kt   # In-memory course store
│       │   │   └── UserSession.kt        # Auth state management
│       │   └── ui/
│       │       ├── components/
│       │       │   ├── AppDrawer.kt      # Navigation drawer
│       │       │   ├── BottomNavBar.kt   # Bottom navigation
│       │       │   ├── CircularProgress.kt
│       │       │   └── CourseCard.kt
│       │       ├── screens/
│       │       │   ├── AdminScreen.kt
│       │       │   ├── AuthScreen.kt
│       │       │   ├── CbtExamScreen.kt
│       │       │   ├── DocumentViewerScreen.kt
│       │       │   ├── EmailChangeScreen.kt
│       │       │   ├── ProfileScreen.kt
│       │       │   ├── ProposeScreen.kt
│       │       │   └── WorkspaceScreen.kt
│       │       └── theme/
│       │           ├── Color.kt
│       │           ├── Theme.kt
│       │           └── Type.kt
│       └── res/                           # Resources (icons, themes, strings)
├── build.gradle.kts              # Root Gradle config
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # Gradle properties
├── gradlew / gradlew.bat         # Gradle wrapper scripts
├── .github/workflows/
│   └── build-apk.yml             # CI: build Android APK on push/PR
├── .gitignore
└── README.md
```

## Build & Install

### Prerequisites

- Android Studio (or Gradle + JDK 17)
- Java 17+
- Android SDK (API 34)

### Build APK

```bash
# 1. Clone
git clone https://github.com/isygold1/NIMELSSA.git
cd NIMELSSA

# 2. Build debug APK
./gradlew assembleDebug

# APK located at: app/build/outputs/apk/debug/app-debug.apk
```

### Install on Device

1. Build or download the debug APK from GitHub Actions artifacts
2. Side-load the `.apk` on your Android device (Android 8+)
3. On first run, allow "Install from unknown sources" for debug APKs

## GitHub Actions

| Workflow | Trigger | Output |
|----------|---------|--------|
| `build-apk.yml` | Push to `main`, PR, or manual dispatch | Debug APK artifact (+ GitHub Release on tag push) |

To trigger a **release build** with an APK attachment, push a tag:
```bash
git tag v1.0.0
git push origin v1.0.0
```

## Author

**AGBOOLA ISRAEL OLUWAGBOGO**  
<israelagboola53@gmail.com>

## License

Private — All rights reserved. Unauthorized distribution is prohibited.
