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
  Search
} from 'lucide-react';
import { BrowserProfile, Tab } from '../types/browser';

interface BrowserAddressBarProps {
  currentUrl: string;
  isLoading: boolean;
  onNavigate: (url: string) => void;
  onReload: () => void;
  readerMode?: boolean;
  onToggleReaderMode?: () => void;
  isDesktop?: boolean;
  onToggleDesktop?: () => void;
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
  onOpenTabs?: () => void;
}

export const BrowserAddressBar: React.FC<BrowserAddressBarProps> = ({
  currentUrl,
  isLoading,
  onNavigate,
  onReload,
  readerMode,
  onToggleReaderMode,
  isDesktop,
  onToggleDesktop,
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
  onOpenTabs,
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

  const profileColors: Record<BrowserProfile, string> = {
    simple: 'text-blue-400 border-blue-500/40 bg-blue-500/10',
    developer: 'text-emerald-400 border-emerald-500/40 bg-emerald-500/10',
    power: 'text-amber-300 border-amber-500/40 bg-amber-500/20',
    advance: 'text-lime-400 border-lime-500/40 bg-lime-500/10',
  };

  return (
    <div className="bg-neutral-900 border-b border-neutral-800 flex flex-col select-none z-20 flex-shrink-0">
      {/* 1. Top Horizontal Tab Strip (Exact as in Screenshot) */}
      <div className="px-2 pt-1.5 pb-1 flex items-center gap-1.5 overflow-x-auto no-scrollbar border-b border-neutral-850">
        <button
          onClick={onOpenTabs}
          className="w-7 h-7 rounded-xl bg-neutral-950 border border-neutral-800 flex items-center justify-center text-[10px] font-bold text-neutral-300 flex-shrink-0 active:scale-95"
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
                isActive
                  ? 'bg-amber-500/15 border-amber-500/40 text-amber-200 font-semibold shadow-xs'
                  : 'bg-neutral-950/60 border-neutral-800 text-neutral-400 hover:text-neutral-200'
              }`}
            >
              <span className="truncate text-[11px]">{t.title || 'New Tab'}</span>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onCloseTab && onCloseTab(t.id);
                }}
                className="hover:text-white p-0.5"
              >
                <X className="w-3 h-3 text-neutral-400 hover:text-white" />
              </button>
            </div>
          );
        })}

        {/* New Tab Button */}
        <button
          onClick={onNewTab}
          className="w-7 h-7 rounded-full bg-amber-500/15 border border-amber-500/30 text-amber-300 hover:bg-amber-500/25 flex items-center justify-center flex-shrink-0 active:scale-95 transition"
          title="Open New Tab"
        >
          <Plus className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* 2. Navigation App Bar (Hamburger, Back, Forward, Profile Pill, Reload, Tabs, 3-dots) */}
      <div className="px-2.5 py-1.5 flex items-center justify-between gap-1 text-neutral-300">
        <button
          onClick={onOpenMenu}
          className="p-1.5 rounded-lg hover:bg-neutral-800 transition active:scale-95"
          title="Menu"
        >
          <Menu className="w-4 h-4" />
        </button>

        <div className="flex items-center gap-0.5">
          <button
            onClick={onGoBack}
            disabled={!canGoBack}
            className="p-1.5 rounded-lg hover:bg-neutral-800 disabled:opacity-30 transition active:scale-95"
            title="Back"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <button
            onClick={onGoForward}
            disabled={!canGoForward}
            className="p-1.5 rounded-lg hover:bg-neutral-800 disabled:opacity-30 transition active:scale-95"
            title="Forward"
          >
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>

        {/* Active Profile Pill (POWER / DEVELOPER / SIMPLE / ADVANCE) */}
        <button
          onClick={onOpenProfilePicker}
          className={`px-3 py-1 rounded-full text-[10px] font-bold tracking-wider uppercase border transition flex items-center gap-1 active:scale-95 ${profileColors[currentProfile]}`}
          title="Switch Browser Profile"
        >
          <span>{currentProfile}</span>
        </button>

        <button
          onClick={onReload}
          className={`p-1.5 rounded-lg hover:bg-neutral-800 transition active:scale-95 ${isLoading ? 'animate-spin text-amber-400' : ''}`}
          title="Reload Page"
        >
          <RotateCw className="w-4 h-4" />
        </button>

        {/* Tabs Icon with Badge */}
        <button
          onClick={onOpenTabs}
          className="p-1.5 rounded-lg hover:bg-neutral-800 relative transition active:scale-95"
          title="Tabs"
        >
          <div className="w-5 h-5 rounded border border-neutral-400 flex items-center justify-center font-bold text-[10px] text-neutral-200">
            {tabs.length || 1}
          </div>
        </button>

        <button
          onClick={onOpenMenu}
          className="p-1.5 rounded-lg hover:bg-neutral-800 transition active:scale-95"
          title="More options"
        >
          <MoreVertical className="w-4 h-4" />
        </button>
      </div>

      {/* 3. Address Bar / Omnibox (With SSL lock & Brave / Search Icon) */}
      <div className="px-2.5 pb-2 relative flex items-center">
        <form onSubmit={handleSubmit} className="w-full flex items-center">
          <div className="w-full flex items-center rounded-2xl bg-neutral-950 border border-neutral-800 focus-within:border-amber-500/50 px-3 py-2 transition shadow-inner">
            <button
              type="button"
              onClick={() => setShowSecurityInfo(!showSecurityInfo)}
              className="p-0.5 mr-1.5 text-neutral-400 hover:text-emerald-400 transition"
              title="Connection security"
            >
              {isInternal ? (
                <ShieldCheck className="w-3.5 h-3.5 text-indigo-400" />
              ) : (
                <Lock className={`w-3.5 h-3.5 ${vpnConnected ? 'text-indigo-400' : 'text-emerald-400'}`} />
              )}
            </button>

            <span className="px-1.5 py-0.5 rounded-md bg-amber-500/20 text-amber-300 font-bold text-[10px] mr-2 flex-shrink-0 border border-amber-500/30">
              B Brave
            </span>

            <input
              type="text"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              onFocus={() => setIsEditing(true)}
              onBlur={() => setTimeout(() => setIsEditing(false), 200)}
              className="flex-1 bg-transparent border-0 text-xs text-neutral-100 focus:outline-none truncate font-sans"
              placeholder="Search or enter URL"
            />

            {isEditing && inputVal ? (
              <button
                type="button"
                onMouseDown={() => setInputVal('')}
                className="p-1 text-neutral-400 hover:text-white"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            ) : null}
          </div>
        </form>

        {/* Security Info Dropdown */}
        {showSecurityInfo && (
          <div className="absolute top-12 left-3 w-64 p-3 bg-neutral-900 border border-neutral-700 rounded-2xl shadow-2xl z-50 text-xs text-neutral-200 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-neutral-800 pb-2 mb-2 font-bold">
              <span className="flex items-center gap-1.5 text-emerald-400">
                <Lock className="w-3.5 h-3.5" /> Connection is Secure
              </span>
              <button onClick={() => setShowSecurityInfo(false)} className="text-neutral-500 hover:text-white">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
            <div className="space-y-1.5 text-[11px] text-neutral-300">
              <div><strong>Cipher:</strong> TLS 1.3 / ChaCha20</div>
              <div><strong>Certificate:</strong> Valid Android System Trust</div>
              <div><strong>Active Profile:</strong> {currentProfile.toUpperCase()}</div>
              <div><strong>Cookies:</strong> Profile-isolated data partition</div>
              {vpnConnected && <div className="text-indigo-400 font-semibold">✓ Routed through V9 SingBox tunnel</div>}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
