import React, { useState, useEffect } from 'react';
import { 
  Lock, 
  RotateCw, 
  X, 
  ShieldCheck, 
  Plus, 
  ArrowLeft, 
  ArrowRight, 
  Menu, 
  MoreVertical, 
  Puzzle
} from 'lucide-react';
import { BrowserProfile, Tab } from '../types/browser';

interface BrowserAddressBarProps {
  currentUrl: string;
  isLoading: boolean;
  loadingProgress?: number;
  onNavigate: (url: string) => void;
  onReload: () => void;
  readerMode?: boolean;
  onToggleReaderMode?: () => void;
  isDesktop?: boolean;
  onToggleDesktop?: () => void;
  isFullView?: boolean;
  onToggleFullView?: () => void;
  currentProfile: BrowserProfile;
  onOpenProfilePicker: () => void;
  vpnConnected: boolean;
  tabs?: Tab[];
  activeTabId?: string;
  onSelectTab?: (id: string) => void;
  onCloseTab?: (id: string) => void;
  onNewTab?: () => void;
  canGoBack?: boolean;
  canGoForward?: boolean;
  onGoBack?: () => void;
  onGoForward?: () => void;
  onOpenMenu?: () => void;
  onOpenSidebar?: () => void;
  onOpenTabs?: () => void;
  onOpenExtensions?: () => void;
}

export const BrowserAddressBar: React.FC<BrowserAddressBarProps> = ({
  currentUrl,
  isLoading,
  loadingProgress = 0,
  onNavigate,
  onReload,
  readerMode,
  onToggleReaderMode,
  isDesktop,
  onToggleDesktop,
  isFullView = false,
  onToggleFullView,
  currentProfile,
  onOpenProfilePicker,
  vpnConnected,
  tabs = [],
  activeTabId = '',
  onSelectTab,
  onCloseTab,
  onNewTab,
  canGoBack = false,
  canGoForward = false,
  onGoBack,
  onGoForward,
  onOpenMenu,
  onOpenSidebar,
  onOpenTabs,
  onOpenExtensions,
}) => {
  const [inputVal, setInputVal] = useState(currentUrl);
  const [isEditing, setIsEditing] = useState(false);

  useEffect(() => {
    if (!isEditing) {
      setInputVal(currentUrl);
    }
  }, [currentUrl, isEditing]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setIsEditing(false);
    const query = inputVal.trim();
    if (!query) return;

    if (query.startsWith('http://') || query.startsWith('https://') || query.startsWith('click://')) {
      onNavigate(query);
    } else if (query.includes('.') && !query.includes(' ')) {
      onNavigate(`https://${query}`);
    } else {
      onNavigate(`https://duckduckgo.com/?q=${encodeURIComponent(query)}`);
    }
  };

  const isHomePage = currentUrl === 'click://home';
  const isInternal = currentUrl.startsWith('click://');

  // Palette configurations with defensive fallbacks
  const modeStyles: Record<BrowserProfile, {
    barBg: string;
    chipActive: string;
    chipInactive: string;
    profileBadge: string;
    searchBg: string;
    surferOmniboxBg: string;
    accentGlow: string;
    textColor: string;
    surferBtnHover: string;
  }> = {
    power: {
      barBg: 'bg-[#faf6f0]/95 border-[#e8ded0] text-[#33261a]',
      chipActive: 'bg-[#faf6f0] border-[#d4c5b3] text-[#33261a] font-bold shadow-xs',
      chipInactive: 'bg-[#e4dacd] border-[#d8cbbe] text-[#786350] hover:text-[#33261a]',
      profileBadge: 'bg-[#e0cfb8] text-[#3b2a1a] border-[#cbb99f] font-bold',
      searchBg: 'bg-[#ede5da]/80 border-[#d8ccbe] text-[#33261a] focus-within:border-[#966b42]',
      surferOmniboxBg: 'bg-[#ede5da]/90 border-[#cfbe9e] text-[#2b1f14] shadow-xs focus-within:border-[#966b42] focus-within:ring-2 focus-within:ring-[#966b42]/20',
      accentGlow: 'from-amber-600 via-amber-500 to-amber-300',
      textColor: 'text-[#33261a]',
      surferBtnHover: 'hover:bg-[#e4dacd] text-[#422e1b]',
    },
    developer: {
      barBg: 'bg-[#0b101b]/95 border-emerald-900/40 text-neutral-100',
      chipActive: 'bg-[#1e293b] border-emerald-500/50 text-emerald-300 font-bold shadow-xs',
      chipInactive: 'bg-neutral-900 border-neutral-800 text-neutral-400 hover:text-neutral-200',
      profileBadge: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/50 font-bold',
      searchBg: 'bg-[#0f172a] border-emerald-800/40 text-neutral-100 focus-within:border-emerald-500',
      surferOmniboxBg: 'bg-[#0f172a]/95 border-emerald-700/50 text-emerald-100 shadow-xs focus-within:border-emerald-400 focus-within:ring-2 focus-within:ring-emerald-500/20',
      accentGlow: 'from-emerald-500 via-teal-400 to-cyan-400',
      textColor: 'text-neutral-100',
      surferBtnHover: 'hover:bg-neutral-800 text-neutral-200',
    },
    simple: {
      barBg: 'bg-white/95 border-neutral-200 text-neutral-800',
      chipActive: 'bg-white border-sky-400/60 text-sky-700 font-bold shadow-xs',
      chipInactive: 'bg-neutral-200/60 border-neutral-200 text-neutral-500 hover:text-neutral-800',
      profileBadge: 'bg-sky-500/15 text-sky-700 border-sky-400/50 font-bold',
      searchBg: 'bg-neutral-100 border-neutral-200 text-neutral-800 focus-within:border-sky-500',
      surferOmniboxBg: 'bg-neutral-100/90 border-neutral-300 text-neutral-900 shadow-xs focus-within:border-sky-500 focus-within:ring-2 focus-within:ring-sky-500/20',
      accentGlow: 'from-sky-500 via-blue-500 to-indigo-500',
      textColor: 'text-neutral-900',
      surferBtnHover: 'hover:bg-neutral-100 text-neutral-700',
    },
    advance: {
      barBg: 'bg-black/95 border-lime-500/30 text-lime-400',
      chipActive: 'bg-neutral-900 border-lime-500/60 text-lime-300 font-bold shadow-xs',
      chipInactive: 'bg-black border-neutral-800 text-neutral-500 hover:text-lime-400',
      profileBadge: 'bg-lime-500/20 text-lime-300 border-lime-400 font-bold',
      searchBg: 'bg-neutral-950 border-lime-500/40 text-lime-400 focus-within:border-lime-400',
      surferOmniboxBg: 'bg-black/95 border-lime-500/50 text-lime-300 shadow-xs focus-within:border-lime-400 focus-within:ring-2 focus-within:ring-lime-500/20',
      accentGlow: 'from-lime-400 via-emerald-400 to-green-500',
      textColor: 'text-lime-300',
      surferBtnHover: 'hover:bg-neutral-900 text-lime-400',
    },
  };

  const currentModeStyle = modeStyles[currentProfile] || modeStyles.power;

  // Format display domain and path for website surfers
  const parseUrlInfo = (url: string) => {
    try {
      if (url.startsWith('click://')) {
        return { domain: url, path: '', isSecure: true };
      }
      const parsed = new URL(url);
      const domain = parsed.hostname.replace(/^www\./, '');
      const path = parsed.pathname !== '/' ? parsed.pathname : '';
      return { domain, path, isSecure: parsed.protocol === 'https:' };
    } catch {
      return { domain: url, path: '', isSecure: false };
    }
  };

  const urlInfo = parseUrlInfo(currentUrl);

  // -------------------------------------------------------------
  // 1. WEBSITE SURFACE FULL-VIEW ADDRESS BAR (User Request 4 & 3)
  // "Website surface page KO full view do Srif address bar previous next button arrow do Baki : button hoon"
  // When browsing an external website, render ONLY [←] [→] [Omnibox] [:]
  // -------------------------------------------------------------
  if (!isHomePage) {
    return (
      <div className={`px-2.5 py-2 border-b backdrop-blur-md flex items-center justify-between gap-2 z-20 flex-shrink-0 relative transition-colors ${currentModeStyle.barBg}`}>
        {/* Previous Button (Back Arrow) */}
        <button
          onClick={onGoBack}
          disabled={!canGoBack}
          className={`w-9 h-9 rounded-full flex items-center justify-center disabled:opacity-25 transition active:scale-95 flex-shrink-0 ${currentModeStyle.surferBtnHover}`}
          title="Back (Previous)"
          aria-label="Back"
        >
          <ArrowLeft className="w-4 h-4 stroke-[2.2]" />
        </button>

        {/* Next Button (Forward Arrow) */}
        <button
          onClick={onGoForward}
          disabled={!canGoForward}
          className={`w-9 h-9 rounded-full flex items-center justify-center disabled:opacity-25 transition active:scale-95 flex-shrink-0 ${currentModeStyle.surferBtnHover}`}
          title="Forward (Next)"
          aria-label="Forward"
        >
          <ArrowRight className="w-4 h-4 stroke-[2.2]" />
        </button>

        {/* Ultra-Premium URL Omnibox Pill */}
        <form onSubmit={handleSubmit} className="flex-1 min-w-0">
          <div className={`w-full h-10 flex items-center rounded-full border px-3 transition-all ${currentModeStyle.surferOmniboxBg}`}>
            {/* Verified Security Lock Indicator */}
            <div className="mr-2 flex items-center gap-1 flex-shrink-0 cursor-default" title="TLS 1.3 End-to-End Encrypted">
              {isInternal ? (
                <ShieldCheck className="w-3.5 h-3.5 text-indigo-500" />
              ) : (
                <div className="flex items-center gap-1">
                  <Lock className={`w-3.5 h-3.5 ${vpnConnected ? 'text-indigo-400' : 'text-emerald-500'}`} />
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                </div>
              )}
            </div>

            {/* Editing mode vs Display domain mode */}
            {isEditing ? (
              <input
                type="text"
                value={inputVal}
                autoFocus
                onBlur={() => setIsEditing(false)}
                onChange={(e) => setInputVal(e.target.value)}
                placeholder="Search or enter URL"
                className="flex-1 bg-transparent border-0 text-xs focus:outline-none placeholder:opacity-50 font-medium"
              />
            ) : (
              <div 
                onClick={() => {
                  setIsEditing(true);
                  setInputVal(currentUrl);
                }}
                className="flex-1 flex items-center truncate text-xs cursor-text font-medium select-none"
              >
                <span className="font-bold text-inherit truncate">{urlInfo.domain}</span>
                {urlInfo.path && (
                  <span className="opacity-50 text-[11px] truncate">{urlInfo.path}</span>
                )}
              </div>
            )}

            {/* Quick Action: Clear (when typing) or Reload (when surfing) */}
            {isEditing && inputVal ? (
              <button
                type="button"
                onClick={() => setInputVal('')}
                className="p-1 opacity-60 hover:opacity-100 flex-shrink-0"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            ) : (
              <button
                type="button"
                onClick={onReload}
                className={`p-1 opacity-60 hover:opacity-100 flex-shrink-0 transition active:scale-90 ${isLoading ? 'animate-spin text-amber-500 opacity-100' : ''}`}
                title="Reload"
              >
                <RotateCw className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </form>

        {/* 3-Dots Menu Button (:) */}
        <button
          onClick={onOpenMenu || onOpenSidebar}
          className={`w-9 h-9 rounded-full flex items-center justify-center transition active:scale-95 flex-shrink-0 ${currentModeStyle.surferBtnHover}`}
          title="Browser Options & Settings (:)"
          aria-label="Menu"
        >
          <MoreVertical className="w-4 h-4 stroke-[2.2]" />
        </button>

        {/* Smooth Progressive Loading Bar Animation */}
        {isLoading && (
          <div className="absolute bottom-0 left-0 right-0 h-[2.5px] bg-black/10 dark:bg-white/10 overflow-hidden">
            <div 
              className={`h-full bg-gradient-to-r ${currentModeStyle.accentGlow} transition-all duration-300 ease-out shadow-xs`}
              style={{ width: `${Math.max(18, loadingProgress)}%` }}
            />
          </div>
        )}
      </div>
    );
  }

  // -------------------------------------------------------------
  // 2. CLICK HOME PAGE ADDRESS BAR (Matches User Screenshot Exact UI)
  // Includes Tab Strip: [1] [New Tab ✕] [+], Navigation Bar & Brave Omnibox
  // -------------------------------------------------------------
  return (
    <div className={`border-b flex flex-col select-none z-20 flex-shrink-0 relative transition-colors ${currentModeStyle.barBg}`}>
      {/* 1. Top Horizontal Tab Strip (Exact match with user screenshot: [1] [New Tab ✕] [+]) */}
      <div className="px-2 pt-1.5 pb-1 flex items-center gap-1.5 overflow-x-auto no-scrollbar border-b border-black/5 dark:border-white/5">
        <button
          onClick={onOpenTabs}
          className="w-7 h-7 rounded-xl bg-black/5 dark:bg-white/5 border border-black/10 dark:border-white/10 flex items-center justify-center text-[10px] font-bold flex-shrink-0 active:scale-95 transition"
          title="Tabs Overview"
        >
          {tabs.length || 1}
        </button>

        {/* Tab Chips */}
        {tabs.slice(0, 3).map((t) => {
          const isActive = t.id === activeTabId;
          return (
            <div
              key={t.id}
              onClick={() => onSelectTab && onSelectTab(t.id)}
              className={`px-3 py-1.5 rounded-2xl flex items-center gap-2 max-w-[140px] text-xs cursor-pointer transition active:scale-[0.98] border ${
                isActive ? currentModeStyle.chipActive : currentModeStyle.chipInactive
              }`}
            >
              <span className="truncate text-[11px]">{t.title || 'New Tab'}</span>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onCloseTab && onCloseTab(t.id);
                }}
                className="hover:opacity-75 p-0.5"
              >
                <X className="w-3 h-3 opacity-60 hover:opacity-100" />
              </button>
            </div>
          );
        })}

        {/* New Tab Button */}
        <button
          onClick={onNewTab}
          className="w-7 h-7 rounded-full bg-amber-500/20 border border-amber-600/30 text-amber-800 dark:text-amber-300 hover:bg-amber-500/30 flex items-center justify-center flex-shrink-0 active:scale-95 transition"
          title="Open New Tab"
        >
          <Plus className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* 2. Navigation App Bar (Hamburger, Back, Forward, Profile Pill, Reload, Extensions, Tabs, 3-dots) */}
      <div className="px-2.5 py-1.5 flex items-center justify-between gap-1">
        <button
          onClick={onOpenSidebar || onOpenMenu}
          className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 transition active:scale-95"
          title="Sidebar & All Settings"
        >
          <Menu className="w-4 h-4" />
        </button>

        <div className="flex items-center gap-0.5">
          <button
            onClick={onGoBack}
            disabled={!canGoBack}
            className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 disabled:opacity-30 transition active:scale-95"
            title="Back"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <button
            onClick={onGoForward}
            disabled={!canGoForward}
            className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 disabled:opacity-30 transition active:scale-95"
            title="Forward"
          >
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>

        {/* Active Profile Pill (POWER / DEVELOPER / SIMPLE / ADVANCE) */}
        <button
          onClick={onOpenProfilePicker}
          className={`px-3 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase border transition flex items-center gap-1 active:scale-95 ${currentModeStyle.profileBadge}`}
          title="Switch Browser Profile"
        >
          <span>{currentProfile}</span>
        </button>

        <button
          onClick={onReload}
          className={`p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 transition active:scale-95 ${isLoading ? 'animate-spin text-amber-500' : ''}`}
          title="Reload Page"
        >
          <RotateCw className="w-4 h-4" />
        </button>

        {/* Extensions Hub Button */}
        <button
          onClick={onOpenExtensions}
          className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 transition active:scale-95 text-amber-700 dark:text-amber-400"
          title="Extensions Hub"
        >
          <Puzzle className="w-4 h-4" />
        </button>

        {/* Tabs Icon with Badge */}
        <button
          onClick={onOpenTabs}
          className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 relative transition active:scale-95"
          title="Tabs"
        >
          <div className="w-5 h-5 rounded border border-current flex items-center justify-center font-bold text-[10px]">
            {tabs.length || 1}
          </div>
        </button>

        {/* 3-dots Menu Button (:) */}
        <button
          onClick={onOpenMenu || onOpenSidebar}
          className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 transition active:scale-95"
          title="More options (:)"
        >
          <MoreVertical className="w-4 h-4" />
        </button>
      </div>

      {/* 3. Address Bar / Omnibox (With SSL lock & Brave / Search Icon) */}
      <div className="px-2.5 pb-2 relative flex items-center">
        <form onSubmit={handleSubmit} className="w-full flex items-center">
          <div className={`w-full flex items-center rounded-2xl border px-3 py-2 transition shadow-inner ${currentModeStyle.searchBg}`}>
            <span className="p-0.5 mr-1.5 opacity-70 hover:opacity-100 transition">
              {isInternal ? (
                <ShieldCheck className="w-3.5 h-3.5 text-indigo-500" />
              ) : (
                <Lock className={`w-3.5 h-3.5 ${vpnConnected ? 'text-indigo-500' : 'text-emerald-500'}`} />
              )}
            </span>

            <span className="px-1.5 py-0.5 rounded-md bg-amber-500/20 text-amber-800 dark:text-amber-300 font-bold text-[10px] mr-2 flex-shrink-0 border border-amber-500/30">
              B Brave
            </span>

            <input
              type="text"
              value={inputVal}
              onFocus={() => setIsEditing(true)}
              onBlur={() => setIsEditing(false)}
              onChange={(e) => setInputVal(e.target.value)}
              placeholder="Search or type URL"
              className="flex-1 bg-transparent border-0 text-xs focus:outline-none placeholder:opacity-50 truncate"
            />

            {inputVal && isEditing && (
              <button
                type="button"
                onClick={() => setInputVal('')}
                className="p-1 text-neutral-400 hover:text-neutral-200"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </form>
      </div>

      {/* 4. Smooth Loading Progress Bar Animation (Chrome/Brave style) */}
      {isLoading && (
        <div className="absolute bottom-0 left-0 right-0 h-[2.5px] bg-black/10 dark:bg-white/10 overflow-hidden">
          <div 
            className={`h-full bg-gradient-to-r ${currentModeStyle.accentGlow} transition-all duration-300 ease-out shadow-xs`}
            style={{ width: `${Math.max(15, loadingProgress)}%` }}
          />
        </div>
      )}
    </div>
  );
};
