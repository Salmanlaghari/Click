# Click Browser — Current Feature Overview

This overview reflects repository source, not necessarily the version currently published in an app store.

## Browser profiles and browsing

Click has four profiles—Simple, Developer, Power and Advance. Regular tabs currently use GeckoView; private tabs remain on WebView. A mode selects a browsing profile and its shortcut scope, not a rendering engine. Simple, Developer and Power share app-level history, bookmarks, passwords and other browser settings; Advance also has a separate app-data store. Profiles do not create separate browser engines or device identities.

### Mode-specific shortcut scope

The shared shortcut catalog is filtered by the active mode and the selected tab's engine:

| Mode | Mode-specific shortcuts |
| --- | --- |
| Simple | Everyday navigation and core browsing; advanced storage-management and Developer/Power shortcuts stay out of this catalog. |
| Developer | DevTools, experimental flags, wide layout and find-in-page. DevTools requires a WebView tab and a loaded page. |
| Power | Wide layout, find-in-page, recent tabs and fullscreen for productivity and tab workflows. |
| Advance | Cookies, storage clearing and experimental flags for advanced configuration. |

DevTools, cookie management, wide-layout overrides, Click's custom AdBlock control, Reader mode, page screenshots and page text zoom are currently WebView-only. When a mode offers one of these actions on a GeckoView tab, the catalog disables it and explains the limitation; it does not switch engines to make the action appear supported. GeckoView's own tracking protection remains active when Click's custom AdBlock control is unavailable.

Other browsing features include tabs, private tabs, bookmarks, history, downloads, per-site desktop layout, reader mode and browser-menu customization. Internal `click://` pages provide settings, history, downloads, bookmarks, VPN, cookies, DNS, games and build/profile information.

## Developer and page tools

The developer tools can inspect DOM, network requests, sources and read-only console logs. Other tools include screenshots, print-to-PDF, page translation, text-to-speech and search-in-page. AI chat and page-aware help depend on the selected AI provider and its terms.

## Privacy and security

Click includes ad/tracker blocking, optional tracking-parameter cleanup, HTTPS settings, private tabs, a password manager and V9 Shield VPN/DNS controls. DNT/GPC are voluntary website signals, not enforcement guarantees. Click does not request Android device-location permission; website geolocation requests are denied.

## Downloads and media

The app supports standard user-initiated browser downloads, local video playback, music playback, image viewing and PDF reading.
