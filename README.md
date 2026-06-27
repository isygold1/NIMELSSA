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

| Layer       | Technology                        |
|-------------|-----------------------------------|
| Frontend    | Vanilla HTML / CSS / JS (PWA)     |
| App Shell   | Phone-frame simulation (mobile UI)|
| Offline     | Service Worker (Cache API)        |
| Icons       | SVG + PNG multi-resolution        |
| Hosting     | GitHub Pages / any static server  |

## Project Structure

```
NIMELSSA/
├── index.html          # Main application entry point
├── manifest.json       # PWA manifest
├── sw.js               # Service worker (offline caching)
├── package.json        # Project metadata
├── .gitignore
├── README.md
└── icons/
    ├── icon.svg        # Scalable vector icon
    ├── icon-72.png     # 72×72 PNG icon
    ├── icon-96.png     # 96×96 PNG icon
    ├── icon-128.png    # 128×128 PNG icon
    ├── icon-144.png    # 144×144 PNG icon
    ├── icon-152.png    # 152×152 PNG icon
    ├── icon-192.png    # 192×192 PNG icon
    ├── icon-384.png    # 384×384 PNG icon
    └── icon-512.png    # 512×512 PNG icon
```

## Getting Started

### Prerequisites

- Any modern browser (Chrome, Firefox, Safari, Edge)
- A static HTTP server (optional — the app works opened directly as a file)

### Run Locally

```bash
# Clone the repository
git clone https://github.com/isygold/NIMELSSA.git

# Serve with any static server (e.g., Python)
cd NIMELSSA
python3 -m http.server 8000

# Open http://localhost:8000 in your browser
```

### Install as PWA

1. Open the app in Chrome / Edge on Android or Desktop
2. Tap the **"Install"** or **"Add to Home Screen"** prompt
3. The app launches in standalone mode with no browser chrome

## Author

**AGBOOLA ISRAEL OLUWAGBOGO**  
<israelagboola53@gmail.com>

## License

Private — All rights reserved. Unauthorized distribution is prohibited.
