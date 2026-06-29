# NIMELSSA Vault

> **NIMELSSA** — *Nigerian Medical Laboratory Science Students Association* Academic Vault

A native Android application for curating, accessing, and managing Medical Laboratory Science (MLS) academic resources. Built for students by students.

## Features

### 🔐 Authentication & Roles
- **Secure sign-in / sign-up** with Firebase Auth (email/password)
- **Forgot password** — sends reset link via Firebase (check spam folder!)
- **Role-based access**: Student, Class Rep (level-scoped), Admin
- **Rep self-signup** — register as a level representative during sign-up

### 📚 Workspace
- **Course repository** — 58 courses across 100–400 levels, all MLS departments
- **Filter by level & semester** — dropdown level picker + semester chips
- **Course cards** — tap to view course resources, or use quick-access buttons
- **Refresh button** — manually reload courses and resources from Firestore
- **Empty state** — "Be the first to contribute" CTA when no courses exist

### 📄 Document Viewer
- **In-app WebView** — opens Drive resources (files + folders) inside the app
- **Resource filtering** — by type: Study Notes (LN), Past Questions (PQ), Textbook (TB)
- **Offline save** — download and view resources without internet
- **Broken link handler** — shows error card when WebView fails to load

### ✍️ Resource Proposals
- **Students propose resources** by submitting Google Drive links or files
- **Auto-fill** — detects level from user profile, semester from current month
  - Unilorin calendar: Oct–Feb = 1st semester, Mar–Jul = 2nd semester
- **Pending tracker** — banner on workspace shows your pending proposals count

### ✅ Admin / Rep Approval
- **Approval queue** — review pending proposals with Drive file preview
- **Per-file URL handling** — individual file URLs instead of folder URLs (fixes 404s)
- **Auto-create courses** — when approving resources for a new course code
- **Role scoping** — Reps see only their level's proposals; Admin sees all

### 📊 Reports & Feedback
- **Report issues** — submit bug reports or feature requests
- **Reports dashboard** — view submitted reports and their status

### 🧠 AI-Powered Scanning (Experimental)
- **Drive folder scanner** — scans public Google Drive folders for MLS resources
- **Filename parsing** — auto-detects course code, level, and resource type from filenames
- **Used by admins** to bulk-import resources from shared Drive folders

### 🧪 CBT Exam Portal
- **Practice multiple-choice questions** with instant scoring
- **Subject-based** question banks

### 👤 Profile
- **View account details** — name, email, level, role
- **Email change** — update registered email (Firebase re-auth required)

## Tech Stack

| Layer           | Technology                              |
|-----------------|-----------------------------------------|
| Language        | Kotlin                                  |
| UI              | Jetpack Compose (Material 3)            |
| Architecture    | Single-Activity, Composable Navigation  |
| Navigation      | Navigation Compose (NavHost)            |
| State Mgmt     | `MutableStateFlow` + `collectAsState`   |
| Backend         | Firebase Auth + Firestore               |
| Drive API       | Google Drive API v3 (read-only, API key)|
| CI/CD           | GitHub Actions (APK build + release)    |
| Min SDK         | Android 8.0 (API 26)                    |
| Target SDK      | Android 14 (API 34)                     |

## Firestore Collections

| Collection        | Purpose                               |
|-------------------|---------------------------------------|
| `users/{uid}`     | User profiles (name, level, role)     |
| `courses/{code}`  | Course metadata (58 seeded courses)   |
| `resources/{id}`  | Resource documents (LN/PQ/TB per course) |
| `proposals/{id}`  | Student resource proposals            |
| `reports/{id}`    | Bug reports and feedback              |
| `level_textbooks/{level}` | Level-wide textbook entries    |

## Project Structure

```
NIMELSSA/
├── app/
│   ├── build.gradle.kts                  # App-level Gradle config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nimelssa/vault/
│       │   ├── MainActivity.kt           # Entry point + navigation
│       │   ├── NimelssaApp.kt            # App lifecycle + initialisation
│       │   ├── data/
│       │   │   ├── Course.kt             # Course data model
│       │   │   ├── CourseRepository.kt   # Course CRUD + seeding + Firestore fallback
│       │   │   ├── DriveScanner.kt       # Google Drive API scanning
│       │   │   ├── FilenameParser.kt     # Parse filenames for course/resource info
│       │   │   ├── Levels.kt             # Level constants
│       │   │   ├── OfflineManager.kt     # Offline file caching
│       │   │   ├── Proposal.kt           # Proposal data model
│       │   │   ├── ProposalRepository.kt # Proposal CRUD
│       │   │   ├── Resource.kt           # Resource data model
│       │   │   ├── ResourceRepository.kt # Resource CRUD + textbook migration
│       │   │   └── UserSession.kt        # Auth state, sign-in/sign-up/profile
│       │   └── ui/
│       │       ├── components/
│       │       │   ├── AppDrawer.kt      # Navigation drawer (profile, CBT, reports)
│       │       │   ├── BottomNavBar.kt   # Bottom nav (Workspace / Propose / Admin)
│       │       │   ├── CircularProgress.kt
│       │       │   └── CourseCard.kt     # Course card with clickable area + resource buttons
│       │       ├── screens/
│       │       │   ├── AdminScreen.kt    # Proposal approval queue + unmatched files
│       │       │   ├── AuthScreen.kt     # Sign-in / Sign-up / Forgot password
│       │       │   ├── CbtExamScreen.kt  # Practice MCQ exams
│       │       │   ├── DocumentViewerScreen.kt # Resource viewer with WebView
│       │       │   ├── EmailChangeScreen.kt
│       │       │   ├── ProfileScreen.kt
│       │       │   ├── ProposeScreen.kt  # Submit resource proposals
│       │       │   ├── ReportScreen.kt   # Submit bug reports
│       │       │   ├── ReportsDashboardScreen.kt # View submitted reports
│       │       │   └── WorkspaceScreen.kt # Course browser with filters + refresh
│       │       └── theme/
│       │           ├── Color.kt
│       │           ├── Theme.kt
│       │           └── Type.kt
│       └── res/
│           ├── values/
│           │   └── secrets.xml           # Drive API key (gitignored)
│           ├── drawable/                 # App icon
│           └── ...
├── build.gradle.kts                      # Root Gradle config
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── .github/workflows/
│   └── build-apk.yml                     # CI: build APK on push/PR/manual
├── .gitignore
├── PASSWORD_RESET_NOTE.txt               # Firebase password reset troubleshooting
├── README.md
└── icons/                                # App icon source files
```

## Branches

| Branch  | Purpose |
|---------|---------|
| `main`  | Latest stable release (auto-build) |
| `Stable`| Mirrors `main`, used for release tagging |
| `test3` | Active development — new features land here first |
| `test`  | Archived development branch |

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

# APK at: app/build/outputs/apk/debug/app-debug.apk
```

### Install on Device

1. Build or download the debug APK from GitHub Actions artifacts
2. Side-load the `.apk` on your Android device (Android 8+)
3. On first run, allow "Install from unknown sources" for debug APKs

## GitHub Actions

| Workflow | Trigger | Output |
|----------|---------|--------|
| `build-apk.yml` | Push to `main`, manual dispatch | Debug APK artifact |

Manual dispatch available on all branches from the Actions tab.

## Password Reset

If the password reset email doesn't appear in your inbox:

1. **Check your SPAM / Promotions folder** — Firebase's default sender often lands there
2. **Mark as Not Spam** — future emails will go to the inbox
3. **For a permanent fix**, configure a custom sender in **Firebase Console → Authentication → Templates → Password reset**
4. See `PASSWORD_RESET_NOTE.txt` in the project root for detailed instructions

## Offline Support

- ✅ Firestore disk persistence enabled (cached queries survive app restart)
- ✅ OfflineManager tracks saved courses and downloaded files
- ✅ Resources can be saved for offline viewing via the DocumentViewer
- ✅ Course data falls back to in-memory seed list when Firestore is unavailable

## Author

**AGBOOLA ISRAEL OLUWAGBOGO**  
<israelagboola53@gmail.com>

## License

Private — All rights reserved. Unauthorized distribution is prohibited.
