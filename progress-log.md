# NIMELSSA Vault — Progress Log

## Session: Sep 18–19, 2026

### Branch
test3

### Completed

#### Orientation Feature
- ✅ Created `OrientationManager.kt` — DataStore-backed singleton with `OrientationMode` enum (AUTO, PORTRAIT, LANDSCAPE)
- ✅ Settings → Orientation section — 3 radio options (Auto/System, Portrait, Landscape)
- ✅ Activity orientation lock via `LaunchedEffect` in `MainApp()` — no ANR
- ✅ Orientation preference persists across app restarts

#### Landscape Layouts
- ✅ **WorkspaceScreen** — compressed header row (level dropdown + semester chips + refresh on one line), 2-column `LazyVerticalGrid` for course cards
- ✅ **ProposeScreen** — side-by-side form (left: tip card + level/semester, right: link + notes + submit)
- ✅ **AdminScreen/Rep Desk** — side-by-side proposal cards (2 per row via `chunked(2)`), 2-column `LazyVerticalGrid` for course inventory
- ✅ **PDF Reader** — compact single-row toolbar in landscape, full-bleed reader

#### PDF Reader Improvements
- ✅ Chrome-style cascade toolbar — auto-hides on scroll down, reappears on scroll up
  - JavaScript injection in WebView detects scroll direction
  - `@JavascriptInterface` bridge communicates to Compose state
  - `AnimatedVisibility` with `slideInVertically`/`slideOutVertically` + `fadeIn`/`fadeOut`
- ✅ Solid background on toolbar overlay (`surface.copy(alpha = 0.95f)`)
- ✅ `BackHandler` in PdfReaderScreen — system back returns to resource list, not workspace
- ✅ `BackHandler` in DocumentViewerScreen — steps through states (PDF → WebView → resource list → workspace)
- ✅ Compact portrait title bar (labelSmall, 12dp/4dp padding)
- ✅ Page counter changed from hardcoded `1/pageCount` to `"$pageCount pages"`

#### Navigation Fixes
- ✅ Removed broken route-saving logic that caused "Course not found" loop after pressing back from PDF reader
- ✅ Added `LaunchedEffect(Unit) { CourseRepository.loadAll(); ResourceRepository.loadAll() }` to DocumentViewerScreen — fixes "Course not found" after activity recreation (rotation in background)
- ✅ Loading spinner in DocumentViewerScreen while courses load from Firestore

#### UI Compaction
- ✅ Bottom nav bar height reduced from ~80dp to 56dp, icons scaled to 20dp
- ✅ Settings screen padding reduced (16dp → 12dp horizontal)
- ✅ Settings section spacing tightened (24dp → 12dp between sections)
- ✅ Navigation drawer scrollable via `verticalScroll(rememberScrollState())`

### Errors / Failed Attempts
- ❌ Activity orientation lock in PdfReaderScreen's `DisposableEffect` — caused ANR because `requestedOrientation` triggers activity recreation mid-composition. Fixed by moving to `LaunchedEffect` in `MainApp()` which runs after composition.
- ❌ Route-saving logic (`savedRoute` + `LaunchedEffect`) to restore viewer route after rotation — caused infinite "Course not found" loop because it re-navigated to the viewer route every time user pressed back. Removed entirely; DocumentViewerScreen's own `loadAll()` handles the rotation case.

### Files Modified
- `app/src/main/java/com/nimelssa/vault/MainActivity.kt`
- `app/src/main/java/com/nimelssa/vault/ui/theme/OrientationManager.kt` (new)
- `app/src/main/java/com/nimelssa/vault/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/nimelssa/vault/ui/screens/PdfReaderScreen.kt`
- `app/src/main/java/com/nimelssa/vault/ui/screens/DocumentViewerScreen.kt`
- `app/src/main/java/com/nimelssa/vault/ui/screens/WorkspaceScreen.kt`
- `app/src/main/java/com/nimelssa/vault/ui/screens/ProposeScreen.kt`
- `app/src/main/java/com/nimelssa/vault/ui/screens/AdminScreen.kt`
- `app/src/main/java/com/nimelssa/vault/ui/components/BottomNavBar.kt`
- `app/src/main/java/com/nimelssa/vault/ui/components/AppDrawer.kt`

### NEXT STEPS
- [ ] Any new features or bugs to address
