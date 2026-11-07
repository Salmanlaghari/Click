import React, { useState, useEffect } from 'react';
import { 
  Lock, 
  RotateCw, 
  X, 
  BookOpen, 
  Monitor, 
  ShieldCheck, 
  Sliders, 
  Plus, 
  ArrowLeft, 
  ArrowRight, 
  Menu, 
  MoreVertical, 
  Layers, 
  Search,
  Maximize2,
  Minimize2,
  Puzzle,
  Share2
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
  const [showSecurityInfo, setShowSecurityInfo] = useState(false);

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

  const isInternal = currentUrl.startsWith('click://');

  // Distinct styling palettes for each of the 4 modes
  const modeStyles: Record<BrowserProfile, {
    barBg: string;
    chipActive: string;
    chipInactive: string;
    profileBadge: string;
    searchBg: string;
    accentGlow: string;
  }> = {
    power: {
      barBg: 'bg-[#ede5da] text-[#3b2a1a] border-[#d8ccbe]',
      chipActive: 'bg-[#faf6f0] border-[#d4c5b3] text-[#33261a] font-bold shadow-xs',
      chipInactive: 'bg-[#e4dacd] border-[#d8cbbe] text-[#786350] hover:text-[#33261a]',
      profileBadge: 'bg-[#e0cfb8] text-[#3b2a1a] border-[#cbb99f] font-bold',
      searchBg: 'bg-[#faf6f0] border-[#d8ccbe] text-[#33261a] focus-within:border-[#966b42]',
      accentGlow: 'from-amber-600 to-amber-400',
    },
    developer: {
      barBg: 'bg-[#0f172a] text-neutral-100 border-neutral-800',
      chipActive: 'bg-[#1e293b] border-emerald-500/50 text-emerald-300 font-bold shadow-xs',
      chipInactive: 'bg-neutral-900 border-neutral-800 text-neutral-400 hover:text-neutral-200',
      profileBadge: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/50 font-bold',
      searchBg: 'bg-neutral-950 border-neutral-800 text-neutral-100 focus-within:border-emerald-500',
      accentGlow: 'from-emerald-500 to-cyan-400',
    },
    simple: {
      barBg: 'bg-[#f1f5f9] text-neutral-800 border-neutral-200',
      chipActive: 'bg-white border-sky-400/60 text-sky-700 font-bold shadow-xs',
      chipInactive: 'bg-neutral-200/60 border-neutral-250 text-neutral-500 hover:text-neutral-800',
      profileBadge: 'bg-sky-500/15 text-sky-700 border-sky-400/50 font-bold',
      searchBg: 'bg-white border-neutral-200 text-neutral-800 focus-within:border-sky-500',
      accentGlow: 'from-sky-500 to-blue-500',
    },
    advance: {
      barBg: 'bg-black text-lime-400 border-lime-500/30',
      chipActive: 'bg-neutral-900 border-lime-500/60 text-lime-300 font-bold shadow-xs',
      chipInactive: 'bg-black border-neutral-800 text-neutral-500 hover:text-lime-400',
      profileBadge: 'bg-lime-500/20 text-lime-300 border-lime-400 font-bold',
      searchBg: 'bg-neutral-950 border-lime-500/40 text-lime-400 focus-within:border-lime-400',
      accentGlow: 'from-lime-400 to-emerald-400',
    },
  };

  const currentModeStyle = modeStyles[currentProfile] || modeStyles.power;

  // If in Immersive Full View, show ultra-compact minimal top bar
  if (isFullView) {
    return (
      <div className={`px-2 py-1 border-b flex items-center justify-between z-20 flex-shrink-0 transition-all ${currentModeStyle.barBg}`}>
        <div className="flex items-center gap-1.5 truncate max-w-[70%]">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse flex-shrink-0" />
          <span className="text-[11px] font-mono truncate font-medium">{currentUrl}</span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={onReload}
            className="p-1 rounded-lg hover:bg-black/10 dark:hover:bg-white/10"
            title="Reload"
          >
            <RotateCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={onToggleFullView}
            className="px-2 py-0.5 rounded-lg bg-amber-500/20 text-amber-800 dark:text-amber-300 border border-amber-500/40 text-[10px] font-bold flex items-center gap-1 active:scale-95"
            title="Exit Full View"
          >
            <Minimize2 className="w-3 h-3" />
            <span>Exit Full View</span>
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={`border-b flex flex-col select-none z-20 flex-shrink-0 relative transition-colors ${currentModeStyle.barBg}`}>
      {/* 1. Top Horizontal Tab Strip (Exact matches screenshot: [1] [New Tab ✕] [+]) */}
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

      {/* 2. Navigation App Bar (Hamburger, Back, Forward, Profile Pill, Reload, Tabs, 3-dots) */}
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

        {/* Full View Immersive Toggle for Web Surfers */}
        <button
          onClick={onToggleFullView}
          className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 transition active:scale-95 text-amber-700 dark:text-amber-400"
          title="Full View / Immersive Web Surfer"
        >
          <Maximize2 className="w-4 h-4" />
        </button>

        {/* Extensions Hub Button */}
        <button
          onClick={onOpenExtensions}
          className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 transition active:scale-95"
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

        <button
          onClick={onOpenMenu}
          className="p-1.5 rounded-lg hover:bg-black/10 dark:hover:bg-white/10 transition active:scale-95"
          title="More options"
        >
          <MoreVertical className="w-4 h-4" />
        </button>
      </div>

      {/* 3. Address Bar / Omnibox (With SSL lock & Brave / Search Icon) */}
      <div className="px-2.5 pb-2 relative flex items-center">
        <form onSubmit={handleSubmit} className="w-full flex items-center">
          <div className={`w-full flex items-center rounded-2xl border px-3 py-2 transition shadow-inner ${currentModeStyle.searchBg}`}>
            <button
              type="button"
              onClick={() => setShowSecurityInfo(!showSecurityInfo)}
              className="p-0.5 mr-1.5 opacity-70 hover:opacity-100 transition"
              title="Connection security"
            >
              {isInternal ? (
                <ShieldCheck className="w-3.5 h-3.5 text-indigo-500" />
              ) : (
                <Lock className={`w-3.5 h-3.5 ${vpnConnected ? 'text-indigo-500' : 'text-emerald-500'}`} />
              )}
            </button>

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
