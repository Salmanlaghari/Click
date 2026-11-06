import React, { useState, useEffect } from 'react';
import { 
  BrowserProfile, 
  Tab, 
  Bookmark, 
  HistoryItem 
} from './types/browser';
import { MOCK_PAGES } from './data/mockWebsites';
import { PhoneFrame } from './components/PhoneFrame';
import { AndroidNotificationShade } from './components/AndroidNotificationShade';
import { BrowserAddressBar } from './components/BrowserAddressBar';
import { BrowserToolbar } from './components/BrowserToolbar';
import { BrowserMenuSheet } from './components/BrowserMenuSheet';
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

export const App: React.FC = () => {
  // Profiles
  const [currentProfile, setCurrentProfile] = useState<BrowserProfile>('developer');

  // Tabs state
  const [tabs, setTabs] = useState<Tab[]>([
    {
      id: 'tab-1',
      url: 'click://home',
      title: 'Click Home',
      isIncognito: false,
      history: ['click://home'],
      historyIndex: 0,
      profile: 'developer',
      lastActive: Date.now(),
    },
    {
      id: 'tab-2',
      url: 'https://en.wikipedia.org/wiki/Web_browser',
      title: 'Web browser - Wikipedia',
      isIncognito: false,
      history: ['https://en.wikipedia.org/wiki/Web_browser'],
      historyIndex: 0,
      profile: 'developer',
      lastActive: Date.now() - 1000,
    },
    {
      id: 'tab-3',
      url: 'click://vpn',
      title: 'V9 Shield VPN',
      isIncognito: false,
      history: ['click://vpn'],
      historyIndex: 0,
      profile: 'developer',
      lastActive: Date.now() - 2000,
    }
  ]);

  const [activeTabId, setActiveTabId] = useState<string>('tab-1');

  // VPN State
  const [vpnConnected, setVpnConnected] = useState<boolean>(true);

  // Modals & Drawers
  const [isNotificationShadeOpen, setIsNotificationShadeOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isTabSwitcherOpen, setIsTabSwitcherOpen] = useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);

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
    { id: 'h1', title: 'Click Home', url: 'click://home', visitedAt: Date.now() - 120000, profile: 'developer', isIncognito: false },
    { id: 'h2', title: 'V9 Shield VPN', url: 'click://vpn', visitedAt: Date.now() - 60000, profile: 'developer', isIncognito: false },
    { id: 'h3', title: 'Web browser - Wikipedia', url: 'https://en.wikipedia.org/wiki/Web_browser', visitedAt: Date.now() - 30000, profile: 'developer', isIncognito: false },
  ]);

  // Current Active Tab
  const activeTab = tabs.find((t) => t.id === activeTabId) || tabs[0];

  // Navigate URL on active tab
  const handleNavigate = (newUrl: string) => {
    setTabs((prev) =>
      prev.map((tab) => {
        if (tab.id !== activeTabId) return tab;
        const newHist = tab.history.slice(0, tab.historyIndex + 1);
        newHist.push(newUrl);

        let pageTitle = newUrl;
        if (newUrl.startsWith('click://')) {
          const name = newUrl.replace('click://', '');
          pageTitle = `Click ${name.charAt(0).toUpperCase() + name.slice(1)}`;
        } else if (MOCK_PAGES[newUrl]) {
          pageTitle = MOCK_PAGES[newUrl].title;
        }

        return {
          ...tab,
          url: newUrl,
          title: pageTitle,
          history: newHist,
          historyIndex: newHist.length - 1,
          isLoading: false,
          lastActive: Date.now(),
        };
      })
    );

    // Record history if not incognito
    if (!activeTab.isIncognito) {
      let title = newUrl;
      if (newUrl.startsWith('click://')) {
        const name = newUrl.replace('click://', '');
        title = `Click ${name.charAt(0).toUpperCase() + name.slice(1)}`;
      } else if (MOCK_PAGES[newUrl]) {
        title = MOCK_PAGES[newUrl].title;
      }
      setHistory((prev) => [
        {
          id: String(Date.now()),
          title,
          url: newUrl,
          visitedAt: Date.now(),
          profile: currentProfile,
          isIncognito: false,
        },
        ...prev,
      ]);
    }
  };

  const handleGoBack = () => {
    if (!activeTab || activeTab.historyIndex <= 0) return;
    const newIdx = activeTab.historyIndex - 1;
    const prevUrl = activeTab.history[newIdx];
    setTabs((prev) =>
      prev.map((t) => (t.id === activeTab.id ? { ...t, url: prevUrl, historyIndex: newIdx } : t))
    );
  };

  const handleGoForward = () => {
    if (!activeTab || activeTab.historyIndex >= activeTab.history.length - 1) return;
    const newIdx = activeTab.historyIndex + 1;
    const nextUrl = activeTab.history[newIdx];
    setTabs((prev) =>
      prev.map((t) => (t.id === activeTab.id ? { ...t, url: nextUrl, historyIndex: newIdx } : t))
    );
  };

  const handleReload = () => {
    setTabs((prev) =>
      prev.map((t) => (t.id === activeTab.id ? { ...t, isLoading: true } : t))
    );
    setTimeout(() => {
      setTabs((prev) =>
        prev.map((t) => (t.id === activeTab.id ? { ...t, isLoading: false } : t))
      );
    }, 400);
  };

  const handleNewTab = (incognito: boolean = false) => {
    const newId = `tab-${Date.now()}`;
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
    setTabs((prev) => [...prev, newTabObj]);
    setActiveTabId(newId);
  };

  const handleCloseTab = (id: string) => {
    if (tabs.length === 1) {
      // Keep at least one tab
      const fallbackId = `tab-${Date.now()}`;
      setTabs([
        {
          id: fallbackId,
          url: 'click://home',
          title: 'Click Home',
          isIncognito: false,
          history: ['click://home'],
          historyIndex: 0,
          profile: currentProfile,
          lastActive: Date.now(),
        },
      ]);
      setActiveTabId(fallbackId);
      return;
    }
    const remaining = tabs.filter((t) => t.id !== id);
    setTabs(remaining);
    if (activeTabId === id) {
      setActiveTabId(remaining[remaining.length - 1].id);
    }
  };

  const handleToggleReaderMode = () => {
    setTabs((prev) =>
      prev.map((t) => (t.id === activeTab.id ? { ...t, readerMode: !t.readerMode } : t))
    );
  };

  const handleToggleDesktop = () => {
    setTabs((prev) =>
      prev.map((t) => (t.id === activeTab.id ? { ...t, isDesktop: !t.isDesktop } : t))
    );
  };

  const handleSelectProfile = (p: BrowserProfile) => {
    setCurrentProfile(p);
    setIsProfileModalOpen(false);
  };

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
      return <ClickDevToolsPage currentUrl={activeTab.url} />;
    }

    if (url === 'click://ai') {
      return <ClickAiPage currentUrl={activeTab.url} />;
    }

    if (url === 'click://games') {
      return <ClickGamesPage />;
    }

    if (url === 'click://settings') {
      return (
        <ClickSettingsPage
          currentProfile={currentProfile}
          onSelectProfile={handleSelectProfile}
          onClearData={() => {
            setHistory([]);
          }}
        />
      );
    }

    if (url === 'click://passwords') {
      return <ClickPasswordsPage />;
    }

    if (url === 'click://downloads') {
      return <ClickDownloadsPage />;
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

    // 3. Mock site renderer (avoid X-Frame-Options blocking)
    if (MOCK_PAGES[url]) {
      return (
        <div 
          className="h-full overflow-y-auto bg-neutral-950 text-neutral-100"
          dangerouslySetInnerHTML={{ __html: MOCK_PAGES[url].content }}
        />
      );
    }

    // 4. Fallback live iframe / embed preview
    return (
      <div className="h-full w-full relative bg-neutral-950 flex flex-col">
        <iframe
          src={url}
          title={activeTab.title}
          className="w-full flex-1 border-0"
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
        />
      </div>
    );
  };

  return (
    <div className="min-h-screen w-full bg-neutral-950 flex flex-col text-neutral-100">
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
          setTabs([
            {
              id: 'tab-1',
              url: 'click://home',
              title: 'Click Home',
              isIncognito: false,
              history: ['click://home'],
              historyIndex: 0,
              profile: currentProfile,
              lastActive: Date.now(),
            },
          ]);
          setActiveTabId('tab-1');
          setVpnConnected(true);
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
          {/* Top Address Bar (Omnibox) */}
          <BrowserAddressBar
            currentUrl={activeTab.url}
            isLoading={!!activeTab.isLoading}
            onNavigate={handleNavigate}
            onReload={handleReload}
            readerMode={activeTab.readerMode}
            onToggleReaderMode={handleToggleReaderMode}
            isDesktop={activeTab.isDesktop}
            onToggleDesktop={handleToggleDesktop}
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
            onOpenMenu={() => setIsMenuOpen(true)}
            onOpenTabs={() => setIsTabSwitcherOpen(true)}
          />

          {/* Web Content Render Area */}
          <div className="flex-1 relative overflow-hidden">
            {renderWebView()}
          </div>

          {/* Bottom Browser Navigation Toolbar */}
          <BrowserToolbar
            tabCount={tabs.length}
            onOpenTabs={() => setIsTabSwitcherOpen(true)}
            onOpenMenu={() => setIsMenuOpen(true)}
            onOpenBookmarks={() => handleNavigate('click://bookmarks')}
            onOpenAi={() => handleNavigate('click://ai')}
            onQuickAction={() => setIsMenuOpen(true)}
            currentProfile={currentProfile}
          />

          {/* Overlays inside Phone viewport */}
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
                    { id: 'simple' as const, name: 'Simple', desc: 'Minimal & clean reading mode', color: 'text-blue-400' },
                    { id: 'developer' as const, name: 'Developer', desc: 'DevTools, DOM inspector & console', color: 'text-emerald-400' },
                    { id: 'power' as const, name: 'Power', desc: 'High performance & aggressive adblock', color: 'text-amber-400' },
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
                      {currentProfile === p.id && <span className="text-emerald-400 text-xs">✓ Active</span>}
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
