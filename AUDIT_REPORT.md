# Click Browser — Code Audit Report

**Repo:** `Salmanlaghari/Click` (local: `/home/hatch/workspace/repos/Click`)
**Date:** 2026-10-08
**Scope:** All 17 `.kt` files under `app/src`, `README.md`, `ASO_METADATA.md`, `AndroidManifest.xml`, Gradle files.
**Method:** Full source read + grep verification of every toggle/handler. A feature counts as REAL only if the code path performs the claimed work; UI that only shows a Toast / animates a fake widget counts as FAKE.

**Overall verdict:** The browsing core is REAL (WebView, tabs, modes, adblock, devtools injection, anti-detection spoofing). Almost everything *around* it — media players, AI, PDF, downloads, and most "Hack shield" items — is FAKE (toast-only or hardcoded demo). Several privacy toggles are dead switches.

> Note: `README.md` makes no feature claims (one-liner only), so MISSING items below are measured against `ASO_METADATA.md` marketing copy and in-app UI text.

---

## MODE 1 — SIMPLE (blue)

### REAL
| Feature | Evidence |
|---|---|
| Mode switch + persistence | `MainActivity.kt:261-268` (drawer), `ModeManager.kt:39-87` `applySettings()`, DataStore `modeFlow` `ModeManager.kt:26-34` |
| Mobile UA per mode | `ModeManager.kt:16` `UA_SIMPLE` |
| Search / URL navigation | `formatUrl()` `MainActivity.kt:1374-1410` (Google/Yahoo/Bing); search bar + address bar `onNavigate` |
| Tabs: add / select / close / grid manager | `TabItem` `MainActivity.kt:78-87`; `PremiumTabsManager.kt:27-154` |
| Incognito tab flag (history skip) | `MainActivity.kt:955-959` history write skipped when `isIncognito` |
| AdBlock host blocklist | `AdBlocker.kt:10-30`; enforced in `shouldInterceptRequest` `MainActivity.kt:975-982` |
| History: record / view / clear | `MainActivity.kt:952-960`, `BookmarksHistoryScreens.kt:79-134`, `BrowserRepository.kt:79-120` |
| Bookmarks: view / delete | `BookmarksHistoryScreens.kt:22-76`, `BrowserRepository.kt:24-76` |
| Downloads: list view | `DownloadsScreen.kt:21-61` (list only) |
| Clear cache / clear cookies | `MainActivity.kt:441-460` (`clearCache`, `removeAllCookies`) |
| Find in page | `MainActivity.kt:~1300-1325` (`findAllAsync`/`findNext`/`clearMatches`) |
| Text zoom | `MainActivity.kt:571-580` (`textZoom`) |
| Block images toggle | `MainActivity.kt:560-570` (`blockNetworkImage`) |
| JavaScript toggle | `MainActivity.kt:551-559` |
| Translate via Google Translate URL | `MainActivity.kt:484-500` |
| Print / save-as-PDF via PrintManager | `MainActivity.kt:507-519` |
| Auto-scroll via JS | `MainActivity.kt:468-478` |
| Camera intent launch | `MainActivity.kt:372-385` (real `ACTION_IMAGE_CAPTURE`, despite "Simulator" name) |
| Import videos from PowerCut folders | `MainActivity.kt:1433-1502` (real file copy + download-item record) |
| Storage permission request flow | `MainActivity.kt:1413-1432` |
| Custom error page (legacy path) | `MainActivity.kt:986-1020` (deprecated `onReceivedError` only — see risks) |

### FAKE (UI exists, does nothing real)
| Claim | Evidence |
|---|---|
| Music Player ("Priscilla", equalizer) | `InteractiveToolsDialogs.kt:30-121` — fake progress timer (`delay(1000)`), hardcoded "Priscilla / Cyberpunk Neon Track", `1:45/4:20`; zero audio code |
| Video Player ("Click Cinema", gestures) | `InteractiveToolsDialogs.kt:123-176` — shows text `"Streaming 1080p @ ${speed}x Speed..."`; no video surface |
| PDF Reader | `InteractiveToolsDialogs.kt:178-230` — hardcoded `"Prince_Laghari_Portfolio.pdf"`, page counter 1–12; no PDF rendering |
| Image Gallery | `InteractiveToolsDialogs.kt:232-287` — rotate/zoom applied to a gradient `Box`; no images loaded |
| PK AI Chat Assistant | `InteractiveToolsDialogs.kt:334-384` — canned response `:365`: `"PK AI response: Click Browser is the world's first 5D Glassmorphism ecosystem..."`; no AI/backend |
| Extensions Center | `InteractiveToolsDialogs.kt:289-332` — only AdBlock + Night-mode switches; no extension system |
| Read Text Aloud (TTS) | `MainActivity.kt:502-506` — toast only, no `TextToSpeech` anywhere in repo |
| Voice Memo Recorder | `MainActivity.kt:387-391` — toast only, no `MediaRecorder` |
| 3D Spatial Soundboard | `MainActivity.kt:393-399` — toast only |
| Offline File Explorer | `MainActivity.kt:411-416` — toast naming `app/src/main/assets`, a path that doesn't exist in the APK |
| HTTPS-Only Mode toggle | `MainActivity.kt:153,547-548` — state flips, **never enforced**; directly contradicted by `MIXED_CONTENT_ALWAYS_ALLOW` (`:1030`) and `http://` allowed in `shouldOverrideUrlLoading` (`:909,:919`) |
| Force Night Mode toggle | `MainActivity.kt:152,538-539` — state flips, no `WebSettingsCompat.setAlgorithmicDarkening` anywhere |
| Data Saver toggle | `MainActivity.kt:156,881-882` — state flips, does nothing |
| Check Updates | `MainActivity.kt:720-725` — hardcoded toast `"Click Browser Pro v1.0.0 is fully up to date."` |
| "Recent Tabs" card | `MainActivity.kt:~2225-2240` — hardcoded `"Click Search Homepage" / "Active"` |
| APK size card | Hardcoded `"Release APK: ~11 MB" / "Debug APK: ~16 MB"` |
| Sponsored ad banner | Hardcoded `"📢 SPONSORED AD: ... Pro Upgrade!"` |
| AI greeting hero card | Hardcoded welcome text |
| Bottom-nav "Search" button | `MainActivity.kt:1113` — `onClick = { /* trigger search Focus */ }` (empty) |

### MISSING (advertised, no code)
- **Add bookmark**: `BrowserRepository.addBookmark()` (`BrowserRepository.kt:36`) is *never called* — no UI path adds a bookmark (view/delete only).
- Real download manager (list exists; actual downloading is fake — see Hack mode).
- Real music / video / PDF / gallery implementations (ASO claims "equalizer", "gesture controls", "Document Viewer").
- `savePasswordsEnabled` state (`MainActivity.kt:155`) — declared, never read.
- Real "120FPS" optimization, real extension support, AI search backend.

---

## MODE 2 — DEVELOPER (purple)

### REAL
| Feature | Evidence |
|---|---|
| Dev UA (`...Chrome/125.0.0.0 Mobile Safari/537.36 DevTools`) | `ModeManager.kt:17`, applied `:55-68` |
| Console hijack → native log list | `DevToolsInjections.kt:8-63` → `DevToolsBridge.kt:20-23` |
| Network (XHR/fetch) intercept → list | `DevToolsInjections.kt:66-127` → `DevToolsBridge.kt:26-29` |
| DOM dump + sources list | `DevToolsInjections.kt:130-145` → `DevToolsBridge.kt:32-50` |
| Element inspector (click → tag + CSS info) | `DevToolsInjections.kt:148-196`; toggled `MainActivity.kt:592-601` and address bar `:1965-1978` |
| JS eval console | `DevToolsPanel.kt:129-220` `ConsoleTab`; wired `MainActivity.kt:1090-1094` (`evaluateJavascript`) |
| DevTools bottom panel (Elements/Console/Network/Sources tabs) | `DevToolsPanel.kt:26-71` — renders real bridge data |
| Device emulator (Tablet 768dp / Desktop 1024dp + UA/viewport) | `MainActivity.kt:~1985-2000` |
| Floating debug overlay (load time = real, RAM = real) | `FloatingDebugOverlay.kt:17-47`; `pageLoadTime` measured `MainActivity.kt:940,950` |
| Dev search engines (Yandex/DuckDuckGo/Baidu) | `MainActivity.kt:190-196`, `formatUrl` `:1395-1400` |

### FAKE
| Claim | Evidence |
|---|---|
| FPS counter in debug overlay | `FloatingDebugOverlay.kt:20-27` — `fps = (55..60).random()`; simulated, not measured |
| Drawer "Active DOM Explorer" | `MainActivity.kt:603-612` — toasts first 50 chars of `domHtml` |
| Drawer "Live Network Traffic Monitor" | `MainActivity.kt:614-621` — toasts `"${networkRequests.size} secure requests traced"` |
| Drawer "Embedded Resource Sniffer" | `MainActivity.kt:623-629` — toasts count only |
| Drawer "JavaScript Interactive Console" | `MainActivity.kt:631-636` — toasts `"${logs.size} ... entries tracked"` |
| (Note: the underlying data for all four IS real in the bottom `DevToolsPanel`; only these drawer shortcuts are stubs.) | |

### MISSING
- Real remote debugging (`chrome://inspect`), breakpoints, request/response *body* inspection (only method/URL/status/size captured), proper element tree (flat tag regex list only, `DevToolsPanel.kt:129-160`).

---

## MODE 3 — HACK / POWER (red)

### REAL
| Feature | Evidence |
|---|---|
| Desktop UA spoof + forced desktop viewport | `ModeManager.kt:18`, `:69-77` |
| 10-layer anti-detection JS injection | `AntiDetectionInjections.kt:5-100` (navigator UA/platform/touch, screen 1920×1080, DPR, hardwareConcurrency, WebGL vendor+renderer spoof, plugins, language) |
| Injection gated on toggle + page finish | `MainActivity.kt:963-971` |
| Video element detection (2s interval scan) | `AntiDetectionInjections.kt:103-132` → `VideoGrabberBridge.kt:8-23` |
| Floating download FAB when videos detected | `MainActivity.kt:1055-1072`; drawer entry `:403-409` |
| Rotate spoofed User-Agent (4 presets) | `MainActivity.kt:652-664` |
| Matrix rain animation on Hack home | `MatrixGridAnimation.kt:14-76`, wired `MainActivity.kt:~2043` |
| Hack search engines list | `MainActivity.kt:197-202` |

### FAKE
| Claim | Evidence |
|---|---|
| **Video downloader** | `MainActivity.kt:1334` — `targetFile.writeText("Fake payload: $url")`: creates a `.mp4` file containing the *text* "Fake payload: <url>". The whole "Universal Video Downloader" dialog (`:1314-1360`) is theater. |
| Spoof HTTP Headers | `MainActivity.kt:666-671` — toast `"Sec-Ch-Ua, DNT, and GPC headers spoofed active."`; no header code anywhere |
| Defeat WebRTC IP Leak | `MainActivity.kt:673-679` — toast only; no `PeerConnection` code |
| Block Web Fingerprinting | `MainActivity.kt:681-687` — toast only; the JS itself admits it: `// Add hooks if needed` (`AntiDetectionInjections.kt:51-55`), canvas/audio spoofing not implemented |
| Secure DNS Tunneling | `MainActivity.kt:689-695` — toast `"Tunneling traffic via PK SECURE DNS (1.1.1.1 Over HTTPS)."`; no DNS code |
| "Onion/Dark Web search" | `MainActivity.kt:1402-1404` — just `https://ahmia.fi` over clearnet; no Tor |
| "integrated AI search" | `:1405` — just `https://perplexity.ai` website |
| Search-hint claims | `MainActivity.kt:~1660-1685` (`assistantHint`): `"Ahmia secure relay active. WebRTC is protected."` — false |

### MISSING
- Real file downloading (use Android `DownloadManager` — no `MANAGE_EXTERNAL_STORAGE` needed).
- Real HTTP header injection (could be done in `shouldInterceptRequest`/`loadUrl(headers)` — not done).
- Real WebRTC leak mitigation, canvas/audio-context fingerprint spoofing, real DoH/private-DNS.

---

## Cross-cutting findings

### Dead code
- `ShortcutWidgetInfo` data class — defined `MainActivity.kt:2528`, never used.
- `savePasswordsEnabled` (`MainActivity.kt:155`) — state declared, never read.
- `pageTitle` param of `PremiumAddressBar`, `isIncognito` param of `PremiumHomeScreen`, `antiDetectionEnabled`/`onToggleAntiDetection` params of `PremiumHomeScreen` — all `@Suppress("UNUSED_PARAMETER")`, wired but unused.
- `addBookmark()` — implemented in repository, zero call sites.

### Hardcoded demo data (never real)
- `"Priscilla" / "Cyberpunk Neon Track" / 1:45 / 4:20` (music dialog)
- `"Prince_Laghari_Portfolio.pdf"`, 12 pages (PDF dialog)
- `"Streaming 1080p @ ${speed}x Speed..."` (video dialog)
- `"Release APK: ~11 MB" / "Debug APK: ~16 MB"`, sponsored ad text, AI greeting, canned AI response.

### Permissions: declared vs used (`AndroidManifest.xml`)
| Permission | Verdict |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Used |
| `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_VIDEO/IMAGES/AUDIO` | Requested at runtime (`MainActivity.kt:1413-1432`) but **never actually used to read media** — downloads are fake, import uses raw `File` API |
| `MANAGE_EXTERNAL_STORAGE` | **Play-policy red flag.** Worse: `requestStoragePermissions()` fires `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` settings intent on **every** Downloads/Import tap (`:1426-1431`) |
| `requestLegacyExternalStorage="true"` | Ignored on API 30+ (targetSdk 34) — dead manifest attribute |

### Build / crash / correctness risks
1. **Stale mode in WebViewClient**: `activeMode` is captured when the `AndroidView` factory runs (`MainActivity.kt:~900`); `onPageFinished` (`:963-971`) keeps injecting for the *old* mode after a mode switch until the tab changes. Fix: read current mode inside the callback or recreate the client on mode change.
2. **Custom error page dead on modern Android**: only the deprecated `onReceivedError(WebView, Int, String?, String?)` (`:986-1020`) is overridden; API 23+ calls `onReceivedError(WebView, WebResourceRequest, WebResourceError)`, which is not overridden — the pretty error page never shows on current devices.
3. **`MIXED_CONTENT_ALWAYS_ALLOW`** (`:1030`) + `http://` explicitly allowed (`:909,:919`) — contradicts every "HTTPS-Only / secure" claim in UI and ASO copy; real security hole.
4. `BackHandler`/`TabItem` logic is fine; `TabItem(url=...)` fallback on invalid index creates a throwaway object per recomposition — harmless.
5. Gradle setup is consistent (AGP 8.5.0, Kotlin 1.9.22, Compose BOM 2024.06.00, `kotlinCompilerExtensionVersion 1.5.8` matches Kotlin 1.9.22, DataStore present) — should compile; no test sources exist.

---

## Top 5 things to fix first

1. **Video downloader is literally fake** — `MainActivity.kt:1334` writes the text `"Fake payload: <url>"` into a `.mp4`. Either wire Android's `DownloadManager` (no special permission needed) or delete the feature. This is the single most dishonest code in the repo.
2. **Dead privacy toggles** — HTTPS-Only, Force Night Mode, and Data Saver flip state and do nothing (`MainActivity.kt:152-156`). Either enforce them (http→https upgrade in `shouldOverrideUrlLoading`; `WebSettingsCompat.setAlgorithmicDarkening`; `blockNetworkImage` for data saver) or remove the switches. Note `MIXED_CONTENT_ALWAYS_ALLOW` must go regardless.
3. **`MANAGE_EXTERNAL_STORAGE` + settings-intent loop** (`MainActivity.kt:1426-1431`, manifest) — Play Store policy risk and terrible UX. Drop the permission; use `DownloadManager`/MediaStore/app-specific dirs.
4. **No way to add a bookmark** — `addBookmark()` has zero call sites. Add a bookmark button to the address bar; the view/delete screens already work.
5. **Stale mode + dead error page** — WebViewClient captures `activeMode` at factory time (mode-specific injections don't follow mode switches); and the custom error page never renders on API 23+ because only the deprecated `onReceivedError` is overridden.

---

## Scorecard (rough)

| Mode | Real | Fake | Missing |
|---|---|---|---|
| SIMPLE | ~20 features (core browsing solid) | ~18 (media/AI/tools theater + 3 dead toggles) | add bookmark, real downloads, real players |
| DEVELOPER | ~10 (console/network/DOM/sources/inspector/eval/emulator) | ~5 (FPS + 4 drawer stubs) | remote debug, body inspection |
| HACK | ~8 (UA spoof, 10-layer injection, video detect, UA rotate) | ~8 (downloader, headers, WebRTC, fingerprint, DNS, onion/AI search) | real download, real header/WebRTC/fingerprint/DoH |

**Bottom line for the "revive" question:** the WebView engine + mode system + devtools are genuinely built and worth keeping. The revive work is almost entirely: (a) replace the fake media/AI/download features with real implementations or cut them, (b) wire up or remove the dead privacy toggles, (c) fix the permission story for Play Store, (d) fix the two WebViewClient bugs. Roughly 60% of the *visible* feature list is theater; roughly 80% of the *engine* is real.
