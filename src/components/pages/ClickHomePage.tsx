import React, { useState, useEffect } from 'react';
import { 
  BrowserProfile, 
  Bookmark 
} from '../../types/browser';
import { 
  ShieldCheck, 
  Terminal, 
  Zap, 
  Flame, 
  Code2, 
  Globe, 
  Search, 
  TrendingUp, 
  Compass, 
  Cpu, 
  Lock, 
  ArrowRight,
  ExternalLink,
  Sliders,
  Bookmark as BookmarkIcon,
  Download,
  Key,
  Clock,
  Settings as SettingsIcon,
  HardDrive,
  Cookie,
  Wrench,
  Code,
  Sparkles,
  Languages,
  Laptop,
  Plus,
  EyeOff,
  Folder,
  RotateCcw,
  User,
  Check
} from 'lucide-react';

interface ClickHomePageProps {
  currentProfile: BrowserProfile;
  vpnConnected: boolean;
  onNavigate: (url: string) => void;
  onOpenProfilePicker: () => void;
  onSelectProfile?: (profile: BrowserProfile) => void;
  onNewTab?: (incognito?: boolean) => void;
  onOpenTabs?: () => void;
  onToggleDesktop?: () => void;
  isDesktop?: boolean;
  bookmarks: Bookmark[];
}

export const ClickHomePage: React.FC<ClickHomePageProps> = ({
  currentProfile,
  vpnConnected,
  onNavigate,
  onOpenProfilePicker,
  onSelectProfile,
  onNewTab,
  onOpenTabs,
  onToggleDesktop,
  isDesktop = false,
  bookmarks,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [greeting, setGreeting] = useState('Good afternoon');

  useEffect(() => {
    const hour = new Date().getHours();
    if (hour < 12) setGreeting('Good morning');
    else if (hour < 18) setGreeting('Good afternoon');
    else setGreeting('Good evening');
  }, []);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    const query = searchQuery.trim();
    if (query.startsWith('http://') || query.startsWith('https://') || query.startsWith('click://')) {
      onNavigate(query);
    } else if (query.includes('.') && !query.includes(' ')) {
      onNavigate(`https://${query}`);
    } else {
      onNavigate(`https://duckduckgo.com/?q=${encodeURIComponent(query)}`);
    }
  };

  const profileDescriptions: Record<BrowserProfile, { name: string; subtitle: string; tagline: string; themeColor: string }> = {
    simple: {
      name: 'Simple',
      subtitle: 'Fast, minimal and distraction-free browsing.',
      tagline: 'Simple — Clean reading · Fast lightweight engine',
      themeColor: 'bg-blue-500/15 text-blue-400 border-blue-500/40'
    },
    developer: {
      name: 'Developer',
      subtitle: 'Code inspection, network analyzer and DOM tools.',
      tagline: 'Developer — DevTools · DOM inspector · JS Console · Network',
      themeColor: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/40'
    },
    power: {
      name: 'Power',
      subtitle: 'Wide pages and focused workflows.',
      tagline: 'Power — Desktop-style browsing · Wide-page layout',
      themeColor: 'bg-amber-500/20 text-amber-300 border-amber-500/50'
    },
    advance: {
      name: 'Advance',
      subtitle: 'Strict sandbox, isolated app-data & matrix HUD.',
      tagline: 'Advance — Isolated DB store · Zero data leakage · Cyber UI',
      themeColor: 'bg-lime-500/20 text-lime-300 border-lime-500/50'
    }
  };

  const activeProfileInfo = profileDescriptions[currentProfile];

  // The 16 Table tiles from the screenshot
  const gridTableTiles = [
    { label: 'History', icon: Clock, action: () => onNavigate('click://history'), color: 'text-amber-400 bg-amber-500/10' },
    { label: 'Settings', icon: SettingsIcon, action: () => onNavigate('click://settings'), color: 'text-neutral-300 bg-neutral-800' },
    { label: 'Storage', icon: HardDrive, action: () => onNavigate('click://devtools'), color: 'text-cyan-400 bg-cyan-500/10' },
    { label: 'Passwords', icon: Lock, action: () => onNavigate('click://passwords'), color: 'text-emerald-400 bg-emerald-500/10' },
    { label: 'Cookies', icon: Cookie, action: () => onNavigate('click://devtools'), color: 'text-amber-400 bg-amber-500/10' },
    { label: 'Tools', icon: Wrench, action: () => onNavigate('click://devtools'), color: 'text-purple-400 bg-purple-500/10' },
    { label: 'DevTools', icon: Code, action: () => onNavigate('click://devtools'), color: 'text-blue-400 bg-blue-500/10' },
    { label: 'Downloads', icon: Download, action: () => onNavigate('click://downloads'), color: 'text-emerald-400 bg-emerald-500/10' },
    { label: 'Bookmarks', icon: BookmarkIcon, action: () => onNavigate('click://bookmarks'), color: 'text-orange-400 bg-orange-500/10' },
    { label: 'AI Chat', icon: Sparkles, action: () => onNavigate('click://ai'), color: 'text-pink-400 bg-pink-500/10' },
    { label: 'Translate', icon: Languages, action: () => onNavigate('click://devtools'), color: 'text-indigo-400 bg-indigo-500/10' },
    { 
      label: 'Wide layout', 
      icon: Laptop, 
      action: () => onToggleDesktop && onToggleDesktop(), 
      color: isDesktop ? 'text-amber-300 bg-amber-500/20 ring-1 ring-amber-400' : 'text-neutral-300 bg-neutral-800',
      badge: true 
    },
    { label: 'New Tab', icon: Plus, action: () => onNewTab && onNewTab(false), color: 'text-blue-400 bg-blue-500/10' },
    { label: 'Private', icon: EyeOff, action: () => onNewTab && onNewTab(true), color: 'text-purple-400 bg-purple-500/10' },
    { label: 'Tabs', icon: Folder, action: () => onOpenTabs && onOpenTabs(), color: 'text-teal-400 bg-teal-500/10' },
    { label: 'Reopen', icon: RotateCcw, action: () => onNavigate('https://en.wikipedia.org/wiki/Web_browser'), color: 'text-rose-400 bg-rose-500/10' },
  ];

  return (
    <div className={`h-full overflow-y-auto px-4 py-4 space-y-4 ${
      currentProfile === 'advance' 
        ? 'matrix-scanline bg-black font-cyber text-green-400' 
        : currentProfile === 'power'
        ? 'bg-[#15120e] text-[#f7e6d0]'
        : 'bg-neutral-950 text-neutral-100'
    }`}>
      {/* Top Greeting Header (From Screenshot) */}
      <div className="flex items-start justify-between pt-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-neutral-100">
            {greeting}
          </h1>
          <p className="text-xs text-neutral-400 mt-0.5">
            {activeProfileInfo.subtitle}
          </p>
        </div>
        <button 
          onClick={onOpenProfilePicker}
          className="w-10 h-10 rounded-full bg-amber-500/20 border border-amber-500/30 flex items-center justify-center text-amber-300 shadow-sm active:scale-95 transition"
          title="Account / Profiles"
        >
          <User className="w-5 h-5" />
        </button>
      </div>

      {/* Main Search Bar (Matches Screenshot Omnibox Style) */}
      <form onSubmit={handleSearchSubmit} className="relative">
        <div className="w-full flex items-center rounded-2xl bg-neutral-900/90 border border-neutral-800 focus-within:border-amber-500/60 shadow-inner px-3 py-3 transition">
          <Search className="w-4 h-4 text-neutral-400 mr-2 flex-shrink-0" />
          <span className="px-1.5 py-0.5 rounded-md bg-amber-500/20 text-amber-300 font-bold text-[10px] mr-2 flex-shrink-0 border border-amber-500/30">
            B Brave
          </span>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search or type URL"
            className="flex-1 bg-transparent border-0 text-xs focus:outline-none placeholder:text-neutral-500 truncate"
          />
          {searchQuery && (
            <button
              type="submit"
              className="p-1 rounded-lg bg-neutral-800 text-neutral-300 hover:text-white"
            >
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </form>

      {/* THE FEATURE TABLE / GRID SHEET (Matches User Screenshot Exactly!) */}
      <div className="p-4 rounded-3xl bg-neutral-900/90 border border-neutral-800/90 shadow-xl space-y-3.5">
        {/* Profile Switcher Pills */}
        <div className="grid grid-cols-4 gap-1.5">
          {(['simple', 'developer', 'power', 'advance'] as const).map((prof) => {
            const isSelected = currentProfile === prof;
            return (
              <button
                key={prof}
                onClick={() => onSelectProfile && onSelectProfile(prof)}
                className={`py-2 px-1 rounded-xl text-[11px] font-semibold capitalize transition active:scale-95 text-center border ${
                  isSelected
                    ? 'bg-amber-500/20 text-amber-300 border-amber-500/50 shadow-sm ring-1 ring-amber-500/20'
                    : 'bg-neutral-850 hover:bg-neutral-800 text-neutral-400 border-neutral-800'
                }`}
              >
                {prof}
              </button>
            );
          })}
        </div>

        {/* Dynamic Profile Tagline Description */}
        <div className="text-[11px] text-amber-300/90 font-medium px-1 flex items-center justify-between">
          <span>{activeProfileInfo.tagline}</span>
        </div>

        {/* 4x4 Grid of 16 Tools Table */}
        <div className="grid grid-cols-4 gap-y-3.5 gap-x-2 pt-1">
          {gridTableTiles.map((tile, idx) => {
            const Icon = tile.icon;
            return (
              <button
                key={idx}
                onClick={tile.action}
                className="flex flex-col items-center justify-center group active:scale-95 transition"
              >
                <div className={`w-12 h-12 rounded-2xl flex items-center justify-center mb-1.5 border border-neutral-700/60 shadow-sm relative transition group-hover:scale-105 ${tile.color}`}>
                  <Icon className="w-5 h-5" />
                  {tile.badge && (
                    <span className="w-2 h-2 rounded-full bg-amber-400 absolute top-1.5 right-1.5" />
                  )}
                </div>
                <span className="text-[10px] font-medium text-neutral-300 text-center truncate w-full">
                  {tile.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* V9 Shield & SingBox VPN Status Card (Preserved without removal) */}
      <div 
        onClick={() => onNavigate('click://vpn')}
        className="p-3.5 rounded-2xl bg-gradient-to-r from-indigo-950/40 to-neutral-900 border border-indigo-900/40 hover:border-indigo-700/60 transition cursor-pointer flex items-center justify-between group"
      >
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-xl ${vpnConnected ? 'bg-emerald-500/20 text-emerald-400' : 'bg-neutral-800 text-neutral-400'}`}>
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-bold text-neutral-200">V9 Shield & SingBox VPN</span>
              <span className={`w-1.5 h-1.5 rounded-full ${vpnConnected ? 'bg-emerald-400 animate-ping' : 'bg-neutral-600'}`} />
            </div>
            <p className="text-[11px] text-neutral-400">
              {vpnConnected ? 'Encrypted tunnel active · IP Hidden (0.0.0.0/0)' : 'Tap to enable full encrypted IP-tunnel'}
            </p>
          </div>
        </div>
        <ArrowRight className="w-4 h-4 text-neutral-500 group-hover:text-indigo-400 transition transform group-hover:translate-x-0.5" />
      </div>

      {/* Curated Tech News Feed (Preserved) */}
      <div className="space-y-2 pb-2">
        <div className="flex items-center justify-between px-0.5">
          <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider flex items-center gap-1.5">
            <TrendingUp className="w-3.5 h-3.5 text-emerald-400" />
            Curated Tech Feed
          </span>
          <span className="text-[10px] text-neutral-500 font-mono">No tracking ads</span>
        </div>

        <div 
          onClick={() => onNavigate('https://github.com/trending')}
          className="p-3 rounded-xl bg-neutral-900/60 border border-neutral-800 hover:border-neutral-700 transition cursor-pointer"
        >
          <div className="flex items-center justify-between text-[11px] text-neutral-400 mb-1">
            <span className="text-emerald-400 font-medium">Open Source</span>
            <span>2h ago</span>
          </div>
          <h4 className="text-xs font-semibold text-neutral-200 line-clamp-1">
            sing-box: The universal proxy platform with gVisor TUN stack
          </h4>
          <p className="text-[11px] text-neutral-400 line-clamp-1 mt-0.5">
            Full IPv4/IPv6 dual stack support integrated into Click Browser VPN engine.
          </p>
        </div>
      </div>
    </div>
  );
};
