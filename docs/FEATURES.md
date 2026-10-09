# Click Browser — Feature Catalog (141)

Every user-facing feature in the app, grouped by category. Count is shown in
`click://version` and the About screen (`ClickInternalPages.FEATURE_COUNT`).

## Core browsing (10)
1. System WebView engine (Chromium, always up to date via Play)
2. 3 browser modes: Simple / Developer / Hack
3. Per-mode themes: Simple=light, Developer=dark glass, Hack=OLED neon
4. Visual tab switcher (Mises-style 2-column grid)
5. Animated tab open/close with staggered entry
6. Tab-close UNDO snackbar (4s, restores at index)
7. Private/incognito tabs (no history recorded)
8. Recent tabs list
9. Pull-to-refresh on web pages (toggleable)
10. Per-site desktop/mobile toggle (persisted per host)

## FAB quick menu (34)
11. History 12. Settings 13. Storage 14. Passwords 15. Tools
16. DevTools 17. Downloads 18. Bookmarks 19. AI Chat 20. Translate
21. Desktop toggle 22. New Tab 23. Private Tab 24. Tabs 25. Recent Tabs
26. Share 27. Find in Page 28. Extensions 29. AdBlock toggle 30. Reader Mode
31. Screenshot 32. Save as PDF 33. Add to Home 34. Site Info 35. Privacy Guards
36. UA Spoofer 37. UA Switcher 38. Fullscreen 39. Text Size 40. Night Mode
41. Clear Data 42. About 43. Experiments (click://flags) 44. Version (click://version)

## AI Premium Assist (10)
45. Premium AI chat screen
46. Three-dot typing indicator
47. Voice input (SpeechRecognizer + mic permission)
48. Suggestion chips: Summarize / Explain / Translate to Urdu / Key points
49. Page-aware answers (current title + URL in context)
50. Pre-call content safety filter (AiSafetyFilter)
51. Report/flag button on AI messages (Play policy)
52. User's own API key takes precedence over built-in
53. Tamper-gated AI (disabled on repackaged builds)
54. Animated "Welcome to the Team PK AI Era" splash (tap to skip)

## Privacy & security (10)
55. Ad-blocker with live blocked counter
56. "Protected · N trackers blocked" badge + privacy strip
57. Fingerprint protection toggle
58. Custom header spoofing (per-request headers)
59. Secure DNS toggle
60. HTTPS-only auto-upgrade
61. Privacy Guards dashboard screen
62. Safe Browsing dangerous-site warnings (flag)
63. Do-Not-Track header (flag)
64. One-tap clear browsing data

## click:// internal pages (9)
65. click://flags — experiments lab
66. click://version — V9 engine info + feature count
67. click://settings — settings shortcut
68. click://history — history shortcut
69. click://downloads — downloads shortcut
70. click://bookmarks — bookmarks shortcut
71. click://vpn — VPN shortcut (coming soon until V9 lands)
72. click://dns — DNS shortcut (coming soon until V9 lands)
73. click://newtab — premium home shortcut

## Experimental flags (22)
74. Desktop by default 75. Aggressive ad-block 76. Clear data on exit
77. Clear history on exit 78. Bottom address bar 79. Pull to refresh
80. Do-Not-Track header 81. Block 3rd-party cookies 82. Block images
83. Block pop-ups 84. Block autoplay 85. Force zoom 86. Block screenshots
87. Safe Browsing 88. Tab animations 89. Confirm exit 90. Large text (125%)
91. Allow mixed content 92. Master cookie switch 93. Overscroll glow
94. Custom User-Agent string 95. Custom homepage URL

## Userscripts / extensions (17)
96. Userscript engine (page-level JS injection)
97. Per-script enable/disable + delete + add custom
98. Force-dark-pages 99. Video speed controller 100. Enable copy/right-click
101. Scroll-to-top 102. Reader mode 103. Disable video autoplay
104. Cosmetic ad hider 105. Image zoom 106. Direct links
107. JSON formatter 108. Code copy buttons 109. YouTube volume booster
110. Scroll progress bar 111. Cookie-banner helper 112. Link text preview

## Premium home surface (8)
113. Premium home dashboard (new-tab page)
114. Smart search bar (URL/search detect, voice, QR)
115. Quick site icons (Google, YouTube, Facebook, …)
116. News feed (client-side RSS, 30-min cache, offline fallback)
117. News category tabs (All/News/Tech/AI/Sports)
118. Quick Actions (AI / Summarize / Translate / Reader)
119. Time-based greeting
120. Bottom nav: Home · Tabs · AI · Bookmarks · Menu

## Browsing surface (6)
121. Compact URL bar (lock, reload, menu)
122. Privacy status strip under the URL bar
123. Floating AI button + Ask/Summarize/Translate popup
124. Browse bottom nav: Back · Forward · Home · Tabs · Menu
125. Live tab-count badge
126. Chrome-style browser menu sheet

## DevTools (4)
127. DevTools overlay panel
128. Elements/DOM inspector tab
129. JavaScript console tab (eval)
130. Network + Sources tabs

## Media & tools (7)
131. In-page video detector/grabber
132. Download manager screen
133. Text-to-speech engine hooks
134. Page screenshot
135. Save page as PDF
136. Share / copy link
137. QR code button (home search bar)

## Settings & misc (4)
138. Premium settings (categories, search, modern toggles)
139. Per-mode search engines
140. Custom wallpaper
141. Signing tamper detection with blocking dialog
