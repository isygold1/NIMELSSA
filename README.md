# NIMELSSA Vault

> **NIMELSSA** – *Nigerian Medical Laboratory Science Students Association* Academic Vault

A private, mobile-first Progressive Web App (PWA) for curating, accessing, and managing Medical Laboratory Science (MLS) academic resources. Built for students by students.

## Features

- **Authentication Gateway** — Secure sign-in / sign-up with role-based access (Student / Class Rep)
- **Course Repository** — Browse lecture notes and past questions filtered by level and semester
- **In-App Document Viewer** — Read notes and PQs with page navigation and text-selection-driven contextual search
- **Resource Proposals** — Students can submit Google Drive links or local files for moderation
- **Rep Approval Queue** — Level Representatives review and publish student submissions
- **Offline Support** — Service worker caches core assets; per-course offline toggle via IndexedDB (future)
- **CBT Exam Portal** — Placeholder for upcoming Computer-Based Testing feature

## Tech Stack

| Layer           | Technology                              |
|-----------------|-----------------------------------------|
| Frontend        | Vanilla HTML / CSS / JS (PWA)           |
| App Shell       | Phone-frame simulation (mobile UI)      |
| Native Wrapper  | Capacitor 6 (Android APK)               |
| Offline         | Service Worker (Cache API)              |
| Icons           | SVG + PNG multi-resolution              |
| CI/CD           | GitHub Actions (APK + Pages)            |
| Hosting         | GitHub Pages / any static server        |

## Project Structure

```
NIMELSSA/
├── index.html                # Main application entry point
├── manifest.json             # PWA manifest
├── sw.js                     # Service worker (offline caching)
├── capacitor.config.json     # Capacitor native app config
├── package.json              # Project metadata & build scripts
├── .github/workflows/
│   ├── build-apk.yml         # CI: build Android APK on push
│   └── deploy-pages.yml      # CD: deploy PWA to GitHub Pages
├── .gitignore
├── README.md
└── icons/
    ├── icon.svg              # Scalable vector icon
    ├── icon-72.png           # 72×72 PNG icon
    ├── icon-96.png           # 96×96 PNG icon
    ├── icon-128.png          # 128×128 PNG icon
    ├── icon-144.png          # 144×144 PNG icon
    ├── icon-152.png          # 152×152 PNG icon
    ├── icon-192.png          # 192×192 PNG icon
    ├── icon-384.png          # 384×384 PNG icon
    └── icon-512.png          # 512×512 PNG icon
```

## Install as an App

You can install NIMELSSA Vault **two ways**:

### Option 1 — PWA (browser install)

1. Serve the app locally or on any static host:
   ```bash
   python3 -m http.server 8000
   # or use npx serve .
   ```
2. Open in Chrome / Edge and tap **"Install"** or **"Add to Home Screen"**
3. Launches standalone with no browser chrome

### Option 2 — Android APK (native app)

1. Go to the **Actions** tab in this repository
2. Click the latest **"Build Android APK"** workflow run
3. Scroll down to **Artifacts** and download `nimelssa-vault-debug-apk.zip`
4. Extract and side-load the `.apk` on your Android device
5. Install and launch as a native app

> **Tip:** On first run, Android may ask you to allow "Install from unknown sources" — this is normal for debug APKs.

## Build Locally

### Prerequisites

- Node.js 20+
- Android Studio (for local APK builds)
- Java 17+

### Commands

```bash
# 1. Clone
git clone https://github.com/isygold/NIMELSSA.git
cd NIMELSSA

# 2. Install dependencies
npm install

# 3. Init and build APK
npx cap init NIMELSSA vault
npx cap add android
npx cap sync android
cd android && ./gradlew assembleDebug

# APK located at: android/app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions Workflows

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
