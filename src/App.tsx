import React, { useState, useEffect } from 'react';
import { 
  BrowserProfile, 
  Tab, 
  Bookmark, 
  HistoryItem,
  BrowserExtension
} from './types/browser';
import { MOCK_PAGES } from './data/mockWebsites';
import { PhoneFrame } from './components/PhoneFrame';
import { AndroidNotificationShade } from './components/AndroidNotificationShade';
import { BrowserAddressBar } from './components/BrowserAddressBar';
import { BrowserToolbar } from './components/BrowserToolbar';
import { BrowserMenuSheet } from './components/BrowserMenuSheet';
import { BrowserSidebarDrawer } from './components/BrowserSidebarDrawer';
import { ClickExtensionsModal } from './components/ClickExtensionsModal';
import { TabSwitcherModal } from './components/TabSwitcherModal';
import { EmulatorControls } from './components/EmulatorControls';

// Internal Click Pages
import { ClickHomePage } from './components/pages/ClickHomePage';
import { ClickVpnPage } from './components/pages/ClickVpnPage';
import { ClickDevToolsPage } from './components/pages/ClickDevToolsPage';
import { ClickAiPage } from './components/pages/ClickAiPage';
import { ClickGamesPage } from './components/pages/ClickGamesPage';
import { ClickSettingsPage } from './components/pages/ClickSettingsPage';
import { ClickPasswordsPage } from './components/pages/ClickPasswordsPage';
import { ClickDownloadsPage } from './components/pages/ClickDownloadsPage';
import { ClickBookmarksPage } from './components/pages/ClickBookmarksPage';

export const VALID_PROFILES: readonly BrowserProfile[] = ['simple', 'developer', 'power', 'advance'] as const;

export const isValidProfile = (profile: unknown): profile is BrowserProfile => {
  return typeof profile === 'string' && VALID_PROFILES.includes(profile as BrowserProfile);
};

// Safe tab factory & sanitizer
export const sanitizeTab = (raw: unknown, targetProfile: BrowserProfile, fallbackIndex: number = 0): Tab => {
  const safeProfile = isValidProfile(targetProfile) ? targetProfile : 'power';
  const defaultTab: Tab = {
    id: `tab-${safeProfile}-${Date.now()}-${fallbackIndex}`,
    url: 'click://home',
    title: 'Click Home',
    isIncognito: false,
    history: ['click://home'],
    historyIndex: 0,
    profile: safeProfile,
    lastActive: Date.now(),
  };

  if (!raw || typeof raw !== 'object') {
    return defaultTab;
  }

  const obj = raw as Record<string, unknown>;
  const id = typeof obj.id === 'string' && obj.id.trim() ? obj.id.trim() : defaultTab.id;
  const isIncognito = Boolean(obj.isIncognito);
  const rawUrl = typeof obj.url === 'string' && obj.url.trim() ? obj.url.trim() : 'click://home';
  const validUrl = rawUrl.startsWith('http://') || rawUrl.startsWith('https://') || rawUrl.startsWith('click://')
    ? rawUrl
    : 'click://home';
  
  // History array reconciliation
  const rawHistory = Array.isArray(obj.history) ? obj.history : [];
  const safeHistory = rawHistory
    .filter((h): h is string => typeof h === 'string' && h.trim().length > 0)
    .map((h) => h.trim());
  if (safeHistory.length === 0) {
    safeHistory.push(validUrl);
  }

  // History index clamping
  const rawIndex = typeof obj.historyIndex === 'number' && Number.isFinite(obj.historyIndex)
    ? Math.floor(obj.historyIndex)
    : safeHistory.length - 1;
  const safeHistoryIndex = Math.max(0, Math.min(rawIndex, safeHistory.length - 1));

  // Title reconciliation
  const title = typeof obj.title === 'string' && obj.title.trim()
    ? obj.title.trim()
    : (validUrl === 'click://home' ? 'Click Home' : validUrl);

  const lastActive = typeof obj.lastActive === 'number' && Number.isFinite(obj.lastActive)
    ? obj.lastActive
    : Date.now();

  return {
    id,
    url: validUrl,
    title,
    isIncognito,
    history: safeHistory,
    historyIndex: safeHistoryIndex,
    profile: safeProfile,
    lastActive,
    isLoading: false,
    readerMode: Boolean(obj.readerMode),
    isDesktop: Boolean(obj.isDesktop),
  };
};

export const sanitizeTabs = (rawTabs: unknown, targetProfile: BrowserProfile): Tab[] => {
  if (!Array.isArray(rawTabs) || rawTabs.length === 0) {
    return [sanitizeTab(null, targetProfile, 1)];
  }
  const seenIds = new Set<string>();
  const sanitized = rawTabs.map((t, idx) => {
    const tab = sanitizeTab(t, targetProfile, idx + 1);
    if (seenIds.has(tab.id)) {
      tab.id = `${tab.id}-${idx}-${Date.now()}`;
    }
    seenIds.add(tab.id);
    return tab;
  });
  return sanitized.length > 0 ? sanitized : [sanitizeTab(null, targetProfile, 1)];
};

export const sanitizeHistory = (rawHistory: unknown): HistoryItem[] => {
  if (!Array.isArray(rawHistory)) return [];
  return rawHistory
    .filter((h): h is Record<string, unknown> => Boolean(h && typeof h === 'object'))
    .map((h, idx) => {
      const url = typeof h.url === 'string' && h.url.trim() ? h.url.trim() : 'click://home';
      return {
        id: typeof h.id === 'string' && h.id.trim() ? h.id.trim() : `hist-${idx}-${Date.now()}`,
        title: typeof h.title === 'string' && h.title.trim() ? h.title.trim() : url,
        url,
        visitedAt: typeof h.visitedAt === 'number' && Number.isFinite(h.visitedAt) ? h.visitedAt : Date.now(),
        profile: isValidProfile(h.profile) ? h.profile : 'power',
        isIncognito: Boolean(h.isIncognito),
      };
    });
};

const createDefaultSession = (profile: BrowserProfile): Tab[] => [
  {
    id: `tab-${profile}-1`,
    url: 'click://home',
    title: 'Click Home',
    isIncognito: false,
    history: ['click://home'],
    historyIndex: 0,
    profile,
    lastActive: Date.now(),
  },
  {
    id: `tab-${profile}-2`,
    url: 'https://en.wikipedia.org/wiki/Web_browser',
    title: 'Web browser - Wikipedia',
    isIncognito: false,
    history: ['https://en.wikipedia.org/wiki/Web_browser'],
    historyIndex: 0,
    profile,
    lastActive: Date.now() - 1000,
  },
  {
    id: `tab-${profile}-3`,
    url: profile === 'developer' ? 'click://devtools' : 'click://vpn',
    title: profile === 'developer' ? 'Click DevTools' : 'V9 Shield VPN',
    isIncognito: false,
    history: [profile === 'developer' ? 'click://devtools' : 'click://vpn'],
    historyIndex: 0,
    profile,
    lastActive: Date.now() - 2000,
  }
];

export const App: React.FC = () => {
  // Profiles: Defaults to 'power' mode (matches user screenshot with warm sand theme and 16-tool card)
  const [currentProfile, setCurrentProfile] = useState<BrowserProfile>('power');

  // Isolated per-profile tab sessions (prevents state corruption and simulates Android WebView data partitions)
  const [profileTabSessions, setProfileTabSessions] = useState<Record<BrowserProfile, Tab[]>>({
    power: createDefaultSession('power'),
    developer: createDefaultSession('developer'),
    simple: createDefaultSession('simple'),
    advance: createDefaultSession('advance'),
  });

  // Active tabs state initialized with verified power session
  const [tabs, setTabs] = useState<Tab[]>(() => createDefaultSession('power'));
  const [activeTabId, setActiveTabId] = useState<string>('tab-power-1');

  // VPN State
  const [vpnConnected, setVpnConnected] = useState<boolean>(true);

  // Web Surfer & Immersive Modes
  const [isFullView, setIsFullView] = useState<boolean>(false);
  const [loadingProgress, setLoadingProgress] = useState<number>(0);

  // Modals & Drawers
  const [isNotificationShadeOpen, setIsNotificationShadeOpen] = useState(false);
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isTabSwitcherOpen, setIsTabSwitcherOpen] = useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [isExtensionsModalOpen, setIsExtensionsModalOpen] = useState(false);

  // Play Store Compliant Declarative WebExtensions State
  const [extensions, setExtensions] = useState<BrowserExtension[]>([
    {
      id: 'ext-adblock',
      name: 'Brave Shields & EasyList',
      description: 'Blocks intrusive video ads, trackers, and analytics telemetry before load.',
      version: '3.4.1',
      author: 'Click Privacy Lab',
      icon: 'ShieldCheck',
      category: 'security',
      enabled: true,
      permissions: ['declarativeNetRequest'],
      stats: '48,219 rules active · 14 blocked on current domain'
    },
    {
      id: 'ext-darkreader',
      name: 'Dark Reader Pro',
      description: 'Injects dynamic high-contrast dark theme CSS into websites to protect eyes.',
      version: '4.9.68',
      author: 'Alexander Shutov (Ported)',
      icon: 'Moon',
      category: 'appearance',
      enabled: false,
      permissions: ['activeTabCSS'],
      stats: 'Smart invert filter ready'
    },
    {
      id: 'ext-translate',
      name: 'Webpage Translator',
      description: 'Inline real-time neural translation ribbon powered by Google & DeepL.',
      version: '2.1.0',
      author: 'Click Tools',
      icon: 'Languages',
      category: 'productivity',
      enabled: false,
      permissions: ['activeTabScript'],
      stats: 'Auto-detect foreign language'
    },
    {
      id: 'ext-privacybadger',
      name: 'Privacy Badger & Anti-Fingerprint',
      description: 'Spoofs Canvas, WebRTC, and AudioContext fingerprinting signatures.',
      version: '2.8.4',
      author: 'EFF (Safe Sandbox)',
      icon: 'EyeOff',
      category: 'security',
      enabled: true,
      permissions: ['privacyProtection'],
      stats: 'Canvas noise spoof active'
    },
    {
      id: 'ext-https',
      name: 'HTTPS Everywhere',
      description: 'Automatically encrypts all cleartext HTTP connections with TLS 1.3.',
      version: '2024.1.0',
      author: 'Tor Project & EFF',
      icon: 'Lock',
      category: 'security',
      enabled: true,
      permissions: ['webRequestUpgrade'],
      stats: 'Forcing HTTPS on all routes'
    }
  ]);

  // Emulator Shell Config
  const [isLandscape, setIsLandscape] = useState(false);
  const [showFrame, setShowFrame] = useState(true);

  // Bookmarks & History
  const [bookmarks, setBookmarks] = useState<Bookmark[]>([
    { id: 'b1', title: 'Wikipedia Web Browser', url: 'https://en.wikipedia.org/wiki/Web_browser', category: 'Popular', createdAt: Date.now() },
    { id: 'b2', title: 'GitHub Trending', url: 'https://github.com/trending', category: 'Dev', createdAt: Date.now() },
    { id: 'b3', title: 'Hacker News', url: 'https://news.ycombinator.com', category: 'News', createdAt: Date.now() },
    { id: 'b4', title: 'DuckDuckGo Search', url: 'https://duckduckgo.com', category: 'Tools', createdAt: Date.now() },
  ]);

  const [history, setHistory] = useState<HistoryItem[]>([
    { id: 'h1', title: 'Click Home', url: 'click://home', visitedAt: Date.now() - 120000, profile: 'power', isIncognito: false },
    { id: 'h2', title: 'V9 Shield VPN', url: 'click://vpn', visitedAt: Date.now() - 60000, profile: 'power', isIncognito: false },
    { id: 'h3', title: 'Web browser - Wikipedia', url: 'https://en.wikipedia.org/wiki/Web_browser', visitedAt: Date.now() - 30000, profile: 'power', isIncognito: false },
  ]);

  // Continuous State Reconciliation Check: Resets invalid configurations instead of propagating them
  useEffect(() => {
    // 1. Reconcile invalid profile configuration
    if (!isValidProfile(currentProfile)) {
      console.warn('Reconciling invalid profile configuration:', currentProfile);
      setCurrentProfile('power');
      return;
    }

    // 2. Reconcile empty or non-array tabs
    if (!Array.isArray(tabs) || tabs.length === 0) {
      console.warn('Reconciling empty tabs configuration');
      const recovered = createDefaultSession(currentProfile);
      setTabs(recovered);
      setActiveTabId(recovered[0].id);
      return;
    }

    // 3. Reconcile orphan or missing activeTabId
    const activeExists = tabs.some((t) => t && t.id === activeTabId);
    if (!activeExists) {
      console.warn('Reconciling orphan activeTabId:', activeTabId);
      setActiveTabId(tabs[0].id);
    }
  }, [tabs, activeTabId, currentProfile]);

  // Defensive activeTab resolution: never undefined
  const activeTab: Tab = (Array.isArray(tabs) && tabs.find((t) => t && t.id === activeTabId))
    || (Array.isArray(tabs) && tabs[0])
    || sanitizeTab(null, currentProfile, 1);

  // Extension toggle handler
  const handleToggleExtension = (id: string) => {
    setExtensions((prev) =>
      prev.map((ext) => (ext.id === id ? { ...ext, enabled: !ext.enabled } : ext))
    );
  };

  // Safe navigation handler
  const handleNavigate = (newUrl: string) => {
    const safeUrl = (typeof newUrl === 'string' && newUrl.trim()) ? newUrl.trim() : 'click://home';

    setLoadingProgress(20);
    setTabs((prev) =>
      sanitizeTabs(prev, currentProfile).map((tab) => (tab.id === activeTab.id ? { ...tab, isLoading: true } : tab))
    );

    setTimeout(() => setLoadingProgress(65), 120);
    setTimeout(() => setLoadingProgress(90), 250);

    setTimeout(() => {
      setLoadingProgress(100);
      setTabs((prev) =>
        sanitizeTabs(prev, currentProfile).map((tab) => {
          if (tab.id !== activeTab.id) return tab;
          const currentHist = Array.isArray(tab.history) ? tab.history : ['click://home'];
          const currentIndex = typeof tab.historyIndex === 'number' ? tab.historyIndex : 0;
          const newHist = currentHist.slice(0, currentIndex + 1);
          newHist.push(safeUrl);

          let pageTitle = safeUrl;
          if (safeUrl.startsWith('click://')) {
            const name = safeUrl.replace('click://', '');
            pageTitle = `Click ${name.charAt(0).toUpperCase() + name.slice(1)}`;
          } else if (MOCK_PAGES[safeUrl]) {
            pageTitle = MOCK_PAGES[safeUrl].title;
          }

          return {
            ...tab,
            url: safeUrl,
            title: pageTitle,
            history: newHist,
            historyIndex: newHist.length - 1,
            isLoading: false,
            lastActive: Date.now(),
          };
        })
      );
      setTimeout(() => setLoadingProgress(0), 200);
    }, 400);

    // Record history if not incognito
    if (!activeTab.isIncognito) {
      let title = safeUrl;
      if (safeUrl.startsWith('click://')) {
        const name = safeUrl.replace('click://', '');
        title = `Click ${name.charAt(0).toUpperCase() + name.slice(1)}`;
      } else if (MOCK_PAGES[safeUrl]) {
        title = MOCK_PAGES[safeUrl].title;
      }
      setHistory((prev) => [
        {
          id: String(Date.now()),
          title,
          url: safeUrl,
          visitedAt: Date.now(),
          profile: currentProfile,
          isIncognito: false,
        },
        ...sanitizeHistory(prev),
      ]);
    }
  };

  const handleGoBack = () => {
    if (!activeTab || activeTab.historyIndex <= 0) return;
    const newIdx = Math.max(0, activeTab.historyIndex - 1);
    const prevUrl = activeTab.history[newIdx] || 'click://home';
    setTabs((prev) =>
      sanitizeTabs(prev, currentProfile).map((t) => (t.id === activeTab.id ? { ...t, url: prevUrl, historyIndex: newIdx } : t))
    );
  };

  const handleGoForward = () => {
    if (!activeTab || activeTab.historyIndex >= activeTab.history.length - 1) return;
    const newIdx = Math.min(activeTab.history.length - 1, activeTab.historyIndex + 1);
    const nextUrl = activeTab.history[newIdx] || 'click://home';
    setTabs((prev) =>
      sanitizeTabs(prev, currentProfile).map((t) => (t.id === activeTab.id ? { ...t, url: nextUrl, historyIndex: newIdx } : t))
    );
  };

  const handleReload = () => {
    setLoadingProgress(25);
    setTabs((prev) =>
      sanitizeTabs(prev, currentProfile).map((t) => (t.id === activeTab.id ? { ...t, isLoading: true } : t))
    );
    setTimeout(() => setLoadingProgress(75), 150);
    setTimeout(() => {
      setLoadingProgress(100);
      setTabs((prev) =>
        sanitizeTabs(prev, currentProfile).map((t) => (t.id === activeTab.id ? { ...t, isLoading: false } : t))
      );
      setTimeout(() => setLoadingProgress(0), 150);
    }, 350);
  };

  const handleNewTab = (incognito: boolean = false) => {
    const newId = `tab-${currentProfile}-${Date.now()}`;
    const newTabObj: Tab = {
      id: newId,
      url: 'click://home',
      title: incognito ? 'Private Tab' : 'Click Home',
      isIncognito: incognito,
      history: ['click://home'],
      historyIndex: 0,
      profile: currentProfile,
      lastActive: Date.now(),
    };
    setTabs((prev) => [...sanitizeTabs(prev, currentProfile), newTabObj]);
    setActiveTabId(newId);
  };

  const handleCloseTab = (id: string) => {
    const currentList = sanitizeTabs(tabs, currentProfile);
    if (currentList.length <= 1) {
      const fallback = sanitizeTab(null, currentProfile, 1);
      setTabs([fallback]);
      setActiveTabId(fallback.id);
      return;
    }
    const remaining = currentList.filter((t) => t.id !== id);
    setTabs(remaining);
    if (activeTabId === id) {
      setActiveTabId(remaining[remaining.length - 1].id);
    }
  };

  const handleToggleReaderMode = () => {
    setTabs((prev) =>
      sanitizeTabs(prev, currentProfile).map((t) => (t.id === activeTab.id ? { ...t, readerMode: !t.readerMode } : t))
    );
  };

  const handleToggleDesktop = () => {
    setTabs((prev) =>
      sanitizeTabs(prev, currentProfile).map((t) => (t.id === activeTab.id ? { ...t, isDesktop: !t.isDesktop } : t))
    );
  };

  // Robust Profile Switching with State Reconciliation & Session Partitioning
  const handleSelectProfile = (p: BrowserProfile | string) => {
    try {
      // 1. Reconcile target profile
      const targetProfile: BrowserProfile = isValidProfile(p)
        ? p
        : (typeof p === 'string' && isValidProfile(p.toLowerCase()) ? (p.toLowerCase() as BrowserProfile) : 'power');

      if (targetProfile === currentProfile) {
        setIsProfileModalOpen(false);
        return;
      }

      // 2. Safely snapshot and deep-copy current profile tabs
      const sanitizedCurrentTabs = sanitizeTabs(tabs, currentProfile);
      setProfileTabSessions((prev) => ({
        ...prev,
        [currentProfile]: JSON.parse(JSON.stringify(sanitizedCurrentTabs)),
      }));

      // 3. Reconcile target profile tab session
      const targetSessionRaw = profileTabSessions[targetProfile];
      const nextTabs = sanitizeTabs(
        targetSessionRaw && targetSessionRaw.length > 0 ? targetSessionRaw : createDefaultSession(targetProfile),
        targetProfile
      );

      // 4. Safely set new profile state and active tab
      setCurrentProfile(targetProfile);
      setTabs(nextTabs);
      setActiveTabId(nextTabs[0]?.id || `tab-${targetProfile}-${Date.now()}`);
      setIsProfileModalOpen(false);
    } catch (err) {
      console.error('Safe fallback during profile switch:', err);
      // Hard fallback if anything throws: safely reset to power profile with verified default tabs
      const safeTabs = createDefaultSession('power');
      setCurrentProfile('power');
      setTabs(safeTabs);
      setActiveTabId(safeTabs[0].id);
      setIsProfileModalOpen(false);
    }
  };

  const handleClearData = () => {
    setHistory([]);
  };

  const isDarkReaderActive = extensions.find((e) => e.id === 'ext-darkreader')?.enabled ?? false;
  const isTranslatorActive = extensions.find((e) => e.id === 'ext-translate')?.enabled ?? false;

  // Render Web View Content
  const renderWebView = () => {
    const url = activeTab?.url || 'click://home';

    // 1. Built-in click:// URLs
    if (url === 'click://home') {
      return (
        <ClickHomePage
          currentProfile={currentProfile}
          vpnConnected={vpnConnected}
          onNavigate={handleNavigate}
          onOpenProfilePicker={() => setIsProfileModalOpen(true)}
          onSelectProfile={handleSelectProfile}
          onNewTab={handleNewTab}
          onOpenTabs={() => setIsTabSwitcherOpen(true)}
          onToggleDesktop={handleToggleDesktop}
          isDesktop={!!activeTab.isDesktop}
          bookmarks={bookmarks}
          onOpenDevTools={() => handleNavigate('click://devtools')}
          onOpenVpn={() => handleNavigate('click://vpn')}
          onOpenAi={() => handleNavigate('click://ai')}
        />
      );
    }

    if (url === 'click://vpn' || url === 'click://shield') {
      return (
        <ClickVpnPage
          vpnConnected={vpnConnected}
          onToggleVpn={() => setVpnConnected(!vpnConnected)}
        />
      );
    }

    if (url === 'click://devtools') {
      return (
        <ClickDevToolsPage
          currentUrl={url}
        />
      );
    }

    if (url === 'click://ai') {
      return (
        <ClickAiPage
          currentUrl={activeTab.url}
        />
      );
    }

    if (url === 'click://games') {
      return (
        <ClickGamesPage />
      );
    }

    if (url === 'click://settings') {
      return (
        <ClickSettingsPage
          currentProfile={currentProfile}
          onSelectProfile={handleSelectProfile}
          onClearData={handleClearData}
        />
      );
    }

    if (url === 'click://passwords') {
      return (
        <ClickPasswordsPage />
      );
    }

    if (url === 'click://downloads') {
      return (
        <ClickDownloadsPage />
      );
    }

    if (url === 'click://bookmarks') {
      return (
        <ClickBookmarksPage
          initialTab="bookmarks"
          bookmarks={bookmarks}
          history={history}
          onNavigate={handleNavigate}
          onClearHistory={() => setHistory([])}
        />
      );
    }

    if (url === 'click://history') {
      return (
        <ClickBookmarksPage
          initialTab="history"
          bookmarks={bookmarks}
          history={history}
          onNavigate={handleNavigate}
          onClearHistory={() => setHistory([])}
        />
      );
    }

    // 2. Reader mode view
    if (activeTab.readerMode) {
      const mock = MOCK_PAGES[url];
      return (
        <div className="h-full overflow-y-auto p-6 bg-neutral-900 text-neutral-100 font-serif leading-relaxed">
          <div className="max-w-xl mx-auto space-y-4">
            <div className="border-b border-neutral-800 pb-3 font-sans">
              <span className="text-[10px] uppercase font-bold text-amber-400 tracking-wider">
                READER VIEW · DISTRACTION FREE
              </span>
              <h1 className="text-xl font-bold mt-1 text-white">{activeTab.title}</h1>
              <p className="text-xs text-neutral-400 font-mono mt-0.5">{url}</p>
            </div>
            {mock ? (
              <div dangerouslySetInnerHTML={{ __html: mock.content }} />
            ) : (
              <p className="text-sm text-neutral-300">
                Extracted clean article typography without advertisements, tracking cookies, or heavy scripts.
              </p>
            )}
          </div>
        </div>
      );
    }

    // 3. External Webpage Surfing (with Dark Reader & Translator extension overlays)
    const mock = MOCK_PAGES[url];

    return (
      <div className={`h-full w-full relative flex flex-col ${isDarkReaderActive ? 'filter invert hue-rotate-180 bg-neutral-950 text-neutral-100' : 'bg-white text-black'}`}>
        {/* Play Store Safe Extension: Google/DeepL Translator Ribbon */}
        {isTranslatorActive && (
          <div className="bg-neutral-900 text-neutral-200 px-3 py-1.5 border-b border-neutral-700 flex items-center justify-between text-[11px] z-10 flex-shrink-0">
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-blue-400" />
              <span>Translated to English</span>
              <span className="text-neutral-400 text-[10px]">(Google Neural Translate)</span>
            </div>
            <button 
              onClick={() => handleToggleExtension('ext-translate')}
              className="text-[10px] text-amber-400 hover:underline"
            >
              Show original
            </button>
          </div>
        )}

        {/* Content area: Mock Site or Live Iframe */}
        {mock ? (
          <div 
            className="flex-1 overflow-y-auto p-4"
            dangerouslySetInnerHTML={{ __html: mock.content }}
          />
        ) : (
          <iframe
            src={url}
            title={activeTab.title}
            className="w-full flex-1 border-0"
            sandbox="allow-scripts allow-forms allow-popups"
          />
        )}
      </div>
    );
  };

  return (
    <div className="min-h-screen w-full bg-neutral-950 flex flex-col text-neutral-100 font-sans">
      {/* Emulator Hardware & Action Controls Header */}
      <EmulatorControls
        isLandscape={isLandscape}
        onToggleOrientation={() => setIsLandscape(!isLandscape)}
        showFrame={showFrame}
        onToggleFrame={() => setShowFrame(!showFrame)}
        currentProfile={currentProfile}
        onSelectProfile={handleSelectProfile}
        onOpenDevTools={() => handleNavigate('click://devtools')}
        onOpenVpn={() => handleNavigate('click://vpn')}
        onResetEmulator={() => {
          const freshTabs = createDefaultSession('power');
          setTabs(freshTabs);
          setActiveTabId(freshTabs[0].id);
          setCurrentProfile('power');
          setVpnConnected(true);
          setIsFullView(false);
        }}
        vpnConnected={vpnConnected}
      />

      {/* Main Emulator Viewport Area */}
      <main className="flex-1 flex items-center justify-center p-2 sm:p-4 overflow-hidden relative">
        <PhoneFrame
          vpnConnected={vpnConnected}
          onOpenNotificationShade={() => setIsNotificationShadeOpen(true)}
          onHomeGesture={() => handleNavigate('click://home')}
          onBackGesture={handleGoBack}
          onTabsGesture={() => setIsTabSwitcherOpen(true)}
          isLandscape={isLandscape}
          showFrame={showFrame}
        >
          {/* Top Address Bar (Omnibox with smooth loader and mode colors) */}
          <BrowserAddressBar
            currentUrl={activeTab.url}
            isLoading={!!activeTab.isLoading}
            loadingProgress={loadingProgress}
            onNavigate={handleNavigate}
            onReload={handleReload}
            readerMode={activeTab.readerMode}
            onToggleReaderMode={handleToggleReaderMode}
            isDesktop={activeTab.isDesktop}
            onToggleDesktop={handleToggleDesktop}
            isFullView={isFullView}
            onToggleFullView={() => setIsFullView(!isFullView)}
            currentProfile={currentProfile}
            onOpenProfilePicker={() => setIsProfileModalOpen(true)}
            vpnConnected={vpnConnected}
            tabs={tabs}
            activeTabId={activeTabId}
            onSelectTab={(id) => setActiveTabId(id)}
            onCloseTab={handleCloseTab}
            onNewTab={() => handleNewTab(false)}
            canGoBack={activeTab.historyIndex > 0}
            canGoForward={activeTab.historyIndex < activeTab.history.length - 1}
            onGoBack={handleGoBack}
            onGoForward={handleGoForward}
            onOpenMenu={() => setIsSidebarOpen(true)}
            onOpenSidebar={() => setIsSidebarOpen(true)}
            onOpenTabs={() => setIsTabSwitcherOpen(true)}
            onOpenExtensions={() => setIsExtensionsModalOpen(true)}
          />

          {/* Web Content Render Area */}
          <div className="flex-1 relative overflow-hidden">
            {renderWebView()}
          </div>

          {/* Bottom Browser Navigation Toolbar */}
          <BrowserToolbar
            tabCount={tabs.length}
            onOpenTabs={() => setIsTabSwitcherOpen(true)}
            onOpenMenu={() => setIsSidebarOpen(true)}
            onOpenBookmarks={() => handleNavigate('click://bookmarks')}
            onOpenAi={() => handleNavigate('click://ai')}
            onQuickAction={() => setIsSidebarOpen(true)}
            currentProfile={currentProfile}
            isFullView={isFullView}
          />

          {/* Overlays inside Phone viewport */}
          <BrowserSidebarDrawer
            isOpen={isSidebarOpen}
            onClose={() => setIsSidebarOpen(false)}
            currentProfile={currentProfile}
            onSelectProfile={handleSelectProfile}
            vpnConnected={vpnConnected}
            onToggleVpn={() => setVpnConnected(!vpnConnected)}
            isFullView={isFullView}
            onToggleFullView={() => setIsFullView(!isFullView)}
            isDesktop={!!activeTab.isDesktop}
            onToggleDesktop={handleToggleDesktop}
            isReaderMode={!!activeTab.readerMode}
            onToggleReaderMode={handleToggleReaderMode}
            onNavigate={handleNavigate}
            onOpenExtensions={() => setIsExtensionsModalOpen(true)}
            onClearData={handleClearData}
            activeExtensionsCount={extensions.filter((e) => e.enabled).length}
          />

          <ClickExtensionsModal
            isOpen={isExtensionsModalOpen}
            onClose={() => setIsExtensionsModalOpen(false)}
            extensions={extensions}
            onToggleExtension={handleToggleExtension}
            currentUrl={activeTab.url}
          />

          <AndroidNotificationShade
            isOpen={isNotificationShadeOpen}
            onClose={() => setIsNotificationShadeOpen(false)}
            vpnConnected={vpnConnected}
            onToggleVpn={() => setVpnConnected(!vpnConnected)}
            currentProfile={currentProfile}
            onOpenProfilePicker={() => {
              setIsNotificationShadeOpen(false);
              setIsProfileModalOpen(true);
            }}
            onOpenSettings={() => {
              setIsNotificationShadeOpen(false);
              handleNavigate('click://settings');
            }}
          />

          <BrowserMenuSheet
            isOpen={isMenuOpen}
            onClose={() => setIsMenuOpen(false)}
            onNewTab={handleNewTab}
            onNavigate={handleNavigate}
            isDesktop={!!activeTab.isDesktop}
            onToggleDesktop={handleToggleDesktop}
            isReaderMode={!!activeTab.readerMode}
            onToggleReaderMode={handleToggleReaderMode}
          />

          <TabSwitcherModal
            isOpen={isTabSwitcherOpen}
            onClose={() => setIsTabSwitcherOpen(false)}
            tabs={tabs}
            activeTabId={activeTabId}
            onSelectTab={(id) => setActiveTabId(id)}
            onCloseTab={handleCloseTab}
            onNewTab={handleNewTab}
          />

          {/* Profile Switcher Modal */}
          {isProfileModalOpen && (
            <div className="absolute inset-0 z-50 bg-black/70 backdrop-blur-xs flex items-center justify-center p-4 animate-in fade-in select-none">
              <div className="w-full max-w-xs bg-neutral-900 border border-neutral-700 rounded-3xl p-5 space-y-3 shadow-2xl">
                <div className="flex items-center justify-between border-b border-neutral-800 pb-2">
                  <h3 className="font-bold text-sm text-neutral-100">Switch Browser Profile</h3>
                  <button onClick={() => setIsProfileModalOpen(false)} className="text-neutral-400 hover:text-white">
                    ✕
                  </button>
                </div>
                <p className="text-[11px] text-neutral-400">
                  Each profile operates an isolated WebView data partition for cache and cookies.
                </p>
                <div className="space-y-2">
                  {[
                    { id: 'power' as const, name: 'Power', desc: 'Desktop-style browsing · Wide-page layout', color: 'text-amber-400' },
                    { id: 'developer' as const, name: 'Developer', desc: 'DevTools, DOM inspector & console', color: 'text-emerald-400' },
                    { id: 'simple' as const, name: 'Simple', desc: 'Minimal & clean reading mode', color: 'text-sky-400' },
                    { id: 'advance' as const, name: 'Advance', desc: 'Separate app-data store & matrix HUD', color: 'text-lime-400' },
                  ].map((p) => (
                    <button
                      key={p.id}
                      onClick={() => handleSelectProfile(p.id)}
                      className={`w-full p-3 rounded-2xl border text-left transition flex items-center justify-between ${
                        currentProfile === p.id
                          ? 'bg-neutral-800 border-neutral-600 ring-1 ring-white/20'
                          : 'bg-neutral-950/60 border-neutral-800 hover:border-neutral-700'
                      }`}
                    >
                      <div>
                        <div className={`text-xs font-bold ${p.color}`}>{p.name}</div>
                        <div className="text-[10px] text-neutral-400">{p.desc}</div>
                      </div>
                      {currentProfile === p.id && <span className="text-emerald-400 text-xs font-bold">✓ Active</span>}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}
        </PhoneFrame>
      </main>
    </div>
  );
};

export default App;
