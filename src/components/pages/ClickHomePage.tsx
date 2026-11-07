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
  Check,
  Maximize2,
  Share2,
  RefreshCw,
  Radio
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
  onOpenDevTools?: () => void;
  onOpenVpn?: () => void;
  onOpenAi?: () => void;
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
  onOpenDevTools,
  onOpenVpn,
  onOpenAi,
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

  // Specific themes, styles and taglines for each of the 4 Modes
  const profileConfigs: Record<BrowserProfile, {
    name: string;
    version: string;
    subtitle: string;
    tagline: string;
    containerClass: string;
    cardClass: string;
    searchClass: string;
    pillActiveClass: string;
    pillInactiveClass: string;
    iconCircleClass: string;
    taglineColor: string;
    textColor: string;
    subtextColor: string;
  }> = {
    power: {
      name: 'Power',
      version: 'v9.5-Power',
      subtitle: 'Wide pages and focused workflows.',
      tagline: 'Power — Desktop-style browsing · Wide-page layout',
      containerClass: 'bg-[#f4ede3] text-[#33261a]',
      cardClass: 'bg-[#faf6f0] border-[#e8ded0] text-[#33261a] shadow-md',
      searchClass: 'bg-[#faf6f0] border-[#e2d5c4] text-[#33261a] focus-within:border-[#966b42]',
      pillActiveClass: 'bg-[#e5d5be] text-[#3b2a1a] border-[#cfbe9e] shadow-xs font-bold',
      pillInactiveClass: 'bg-[#f0e7db] text-[#786350] border-[#e0d3c1]',
      iconCircleClass: 'bg-[#eee4d6] text-[#6d523b] border-[#ded0bc] hover:bg-[#e6d8c4]',
      taglineColor: 'text-[#875d34]',
      textColor: 'text-[#2b1f14]',
      subtextColor: 'text-[#7d6854]',
    },
    developer: {
      name: 'Developer',
      version: 'v9.5-Dev',
      subtitle: 'Code inspection, network analyzer and DOM tools.',
      tagline: 'Developer — DevTools · DOM inspector · JS Console · Network',
      containerClass: 'bg-[#0b101b] text-neutral-100',
      cardClass: 'bg-[#111827]/90 border-emerald-900/40 text-neutral-100 shadow-xl',
      searchClass: 'bg-[#0f172a] border-emerald-800/40 text-neutral-100 focus-within:border-emerald-500',
      pillActiveClass: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/50 shadow-xs font-bold',
      pillInactiveClass: 'bg-neutral-900 text-neutral-400 border-neutral-800',
      iconCircleClass: 'bg-emerald-950/40 text-emerald-400 border-emerald-800/50 hover:bg-emerald-900/40',
      taglineColor: 'text-emerald-400',
      textColor: 'text-neutral-100',
      subtextColor: 'text-neutral-400',
    },
    simple: {
      name: 'Simple',
      version: 'v9.5-Lite',
      subtitle: 'Fast, minimal and distraction-free reading.',
      tagline: 'Simple — Clean reading · Fast lightweight engine',
      containerClass: 'bg-[#f8fafc] text-neutral-800',
      cardClass: 'bg-white border-neutral-200 text-neutral-800 shadow-md',
      searchClass: 'bg-white border-neutral-200 text-neutral-800 focus-within:border-sky-500',
      pillActiveClass: 'bg-sky-500/15 text-sky-700 border-sky-400/60 shadow-xs font-bold',
      pillInactiveClass: 'bg-neutral-100 text-neutral-500 border-neutral-250',
      iconCircleClass: 'bg-sky-50 text-sky-600 border-sky-200 hover:bg-sky-100',
      taglineColor: 'text-sky-600',
      textColor: 'text-neutral-900',
      subtextColor: 'text-neutral-500',
    },
    advance: {
      name: 'Advance',
      version: 'v9.5-Matrix',
      subtitle: 'Strict sandbox, isolated app-data & matrix HUD.',
      tagline: 'Advance — Isolated DB store · Zero data leakage · Cyber UI',
      containerClass: 'bg-black text-lime-400 font-mono',
      cardClass: 'bg-neutral-950/95 border-lime-500/30 text-lime-400 shadow-xl ring-1 ring-lime-500/20',
      searchClass: 'bg-black border-lime-500/40 text-lime-400 focus-within:border-lime-400',
      pillActiveClass: 'bg-lime-500/20 text-lime-300 border-lime-400 shadow-xs font-bold',
      pillInactiveClass: 'bg-neutral-900 text-neutral-500 border-neutral-800',
      iconCircleClass: 'bg-neutral-900 text-lime-400 border-lime-500/30 hover:bg-lime-950/40',
      taglineColor: 'text-lime-400',
      textColor: 'text-lime-300',
      subtextColor: 'text-neutral-400',
    },
  };

  const activeCfg = profileConfigs[currentProfile] || profileConfigs.power;

  // The 16 Table tiles from the user's screenshot
  const gridTableTiles = [
    { label: 'History', icon: Clock, action: () => onNavigate('click://history') },
    { label: 'Settings', icon: SettingsIcon, action: () => onNavigate('click://settings') },
    { label: 'Storage', icon: HardDrive, action: () => onNavigate('click://devtools') },
    { label: 'Passwords', icon: Lock, action: () => onNavigate('click://passwords') },
    { label: 'Cookies', icon: Cookie, action: () => onNavigate('click://devtools') },
    { label: 'Tools', icon: Wrench, action: () => onNavigate('click://devtools') },
    { label: 'DevTools', icon: Code, action: () => onNavigate('click://devtools') },
    { label: 'Downloads', icon: Download, action: () => onNavigate('click://downloads') },
    { label: 'Bookmarks', icon: BookmarkIcon, action: () => onNavigate('click://bookmarks') },
    { label: 'AI Chat', icon: Sparkles, action: () => onNavigate('click://ai') },
    { label: 'Translate', icon: Languages, action: () => onNavigate('click://devtools') },
    { 
      label: 'Wide layout', 
      icon: Laptop, 
      action: () => onToggleDesktop && onToggleDesktop(), 
      badge: isDesktop 
    },
    { label: 'New Tab', icon: Plus, action: () => onNewTab && onNewTab(false) },
    { label: 'Private', icon: EyeOff, action: () => onNewTab && onNewTab(true) },
    { label: 'Tabs', icon: Folder, action: () => onOpenTabs && onOpenTabs() },
    { label: 'Reopen', icon: RotateCcw, action: () => onNavigate('https://en.wikipedia.org/wiki/Web_browser') },
  ];

  // Fast Access Speed Dial Shortcuts (As shown under the 16-tool box in the screenshot)
  const speedDialShortcuts = [
    { name: 'Google', url: 'https://google.com', initial: 'G', color: 'bg-blue-600 text-white' },
    { name: 'DuckDuckGo', url: 'https://duckduckgo.com', initial: 'D', color: 'bg-orange-500 text-white' },
    { name: 'GitHub', url: 'https://github.com/trending', initial: 'gh', color: 'bg-neutral-800 text-white' },
    { name: 'Wikipedia', url: 'https://en.wikipedia.org/wiki/Web_browser', initial: 'W', color: 'bg-neutral-600 text-white' },
    { name: 'MDN', url: 'https://developer.mozilla.org', initial: 'M', color: 'bg-indigo-600 text-white' },
  ];

  return (
    <div className={`h-full overflow-y-auto px-4 py-3 space-y-3.5 transition-colors duration-250 select-none ${activeCfg.containerClass}`}>
      {/* Top Greeting Header (Exact layout from user's screenshot) */}
      <div className="flex items-start justify-between pt-1">
        <div>
          <h1 className={`text-2xl font-bold tracking-tight ${activeCfg.textColor}`}>
            {greeting}
          </h1>
          <p className={`text-xs mt-0.5 ${activeCfg.subtextColor}`}>
            {activeCfg.subtitle}
          </p>
        </div>
        <button 
          onClick={onOpenProfilePicker}
          className="w-10 h-10 rounded-full bg-amber-500/20 border border-amber-600/30 flex items-center justify-center text-amber-700 dark:text-amber-300 shadow-xs active:scale-95 transition"
          title="Account / Profiles"
        >
          <User className="w-5 h-5" />
        </button>
      </div>

      {/* Main Search Bar (Exact Omnibox with Brave Badge from Screenshot) */}
      <form onSubmit={handleSearchSubmit} className="relative">
        <div className={`w-full flex items-center rounded-2xl border px-3 py-2.5 transition shadow-sm ${activeCfg.searchClass}`}>
          <Search className="w-4 h-4 text-neutral-400 mr-2 flex-shrink-0" />
          <span className="px-1.5 py-0.5 rounded-md bg-amber-500/20 text-amber-800 dark:text-amber-300 font-bold text-[10px] mr-2 flex-shrink-0 border border-amber-500/30">
            B Brave
          </span>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search or type URL"
            className="flex-1 bg-transparent border-0 text-xs focus:outline-none placeholder:text-neutral-400 truncate"
          />
          {searchQuery && (
            <button
              type="submit"
              className="p-1 rounded-lg bg-amber-600 text-white hover:bg-amber-700 transition"
            >
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </form>

      {/* THE ICONIC 16-TOOL ACTION CARD (Retained Permanently From User Screenshot!) */}
      <div className={`p-4 rounded-3xl border transition-all duration-200 ${activeCfg.cardClass}`}>
        {/* Profile Switcher Pills */}
        <div className="grid grid-cols-4 gap-1.5">
          {(['simple', 'developer', 'power', 'advance'] as const).map((prof) => {
            const isSelected = currentProfile === prof;
            return (
              <button
                key={prof}
                onClick={() => onSelectProfile && onSelectProfile(prof)}
                className={`py-2 px-1 rounded-xl text-[11px] font-semibold capitalize transition active:scale-95 text-center border ${
                  isSelected ? activeCfg.pillActiveClass : activeCfg.pillInactiveClass
                }`}
              >
                {prof}
              </button>
            );
          })}
        </div>

        {/* Dynamic Mode Tagline Description */}
        <div className={`text-[11px] font-medium px-1 pt-1.5 pb-1 flex items-center justify-between ${activeCfg.taglineColor}`}>
          <span>{activeCfg.tagline}</span>
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
                <div className={`w-12 h-12 rounded-2xl flex items-center justify-center mb-1.5 border shadow-xs relative transition group-hover:scale-105 ${activeCfg.iconCircleClass}`}>
                  <Icon className="w-5 h-5" />
                  {tile.badge && (
                    <span className="w-2.5 h-2.5 rounded-full bg-amber-500 absolute top-1.5 right-1.5 ring-2 ring-white dark:ring-neutral-900" />
                  )}
                </div>
                <span className={`text-[10px] font-medium text-center truncate w-full ${activeCfg.textColor}`}>
                  {tile.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Fast Access Speed Dial Row (As peeking in user's screenshot) */}
      <div className="space-y-1.5">
        <div className="flex items-center justify-between px-1">
          <span className={`text-[10px] font-bold uppercase tracking-wider ${activeCfg.subtextColor}`}>
            Fast Access Speed Dial
          </span>
          <span className="text-[10px] text-amber-600 dark:text-amber-400 font-mono">1-Tap Surf</span>
        </div>

        <div className="grid grid-cols-5 gap-2">
          {speedDialShortcuts.map((item, idx) => (
            <button
              key={idx}
              onClick={() => onNavigate(item.url)}
              className="flex flex-col items-center gap-1 group active:scale-95 transition"
            >
              <div className={`w-11 h-11 rounded-2xl flex items-center justify-center font-bold text-xs shadow-xs group-hover:scale-105 transition border border-black/10 ${item.color}`}>
                {item.initial}
              </div>
              <span className={`text-[10px] truncate w-full text-center font-medium ${activeCfg.textColor}`}>
                {item.name}
              </span>
            </button>
          ))}
        </div>
      </div>

      {/* V9 Shield & SingBox VPN Status Card (Preserved Without Removal) */}
      <div 
        onClick={() => onNavigate('click://vpn')}
        className={`p-3.5 rounded-2xl border transition cursor-pointer flex items-center justify-between group ${
          currentProfile === 'advance'
            ? 'bg-neutral-950 border-lime-500/40 text-lime-400'
            : currentProfile === 'developer'
            ? 'bg-neutral-900/90 border-emerald-900/40 text-neutral-100'
            : currentProfile === 'simple'
            ? 'bg-white border-neutral-200 text-neutral-800'
            : 'bg-[#faf6f0] border-[#e8ded0] text-[#33261a]'
        }`}
      >
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-xl ${
            vpnConnected 
              ? 'bg-emerald-500/20 text-emerald-600 dark:text-emerald-400' 
              : 'bg-neutral-200 dark:bg-neutral-800 text-neutral-500'
          }`}>
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-bold">V9 Shield & SingBox VPN</span>
              <span className={`w-1.5 h-1.5 rounded-full ${vpnConnected ? 'bg-emerald-500 animate-ping' : 'bg-neutral-400'}`} />
            </div>
            <p className={`text-[11px] ${activeCfg.subtextColor}`}>
              {vpnConnected ? 'Encrypted tunnel active · IP Hidden (0.0.0.0/0)' : 'Tap to enable full encrypted IP-tunnel'}
            </p>
          </div>
        </div>
        <ArrowRight className="w-4 h-4 text-neutral-400 group-hover:translate-x-0.5 transition" />
      </div>

      {/* Developer Live Mini-Console Bar (If Developer Mode Active) */}
      {currentProfile === 'developer' && (
        <div 
          onClick={onOpenDevTools}
          className="p-3 rounded-2xl bg-neutral-900 border border-emerald-500/30 text-emerald-400 font-mono text-xs flex items-center justify-between cursor-pointer hover:border-emerald-500/60 transition"
        >
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4 text-emerald-400" />
            <span className="text-[11px] text-neutral-200">DevTools: DOM Tree & Console Ready</span>
          </div>
          <span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 text-[10px]">Open Dock</span>
        </div>
      )}

      {/* Advance Matrix HUD Card (If Advance Mode Active) */}
      {currentProfile === 'advance' && (
        <div className="p-3 rounded-2xl bg-black border border-lime-500/40 text-lime-400 font-mono text-[11px] space-y-1">
          <div className="flex items-center justify-between">
            <span>[PARTITION]: SQLite_Cipher_AES256</span>
            <span className="text-lime-300 font-bold">LOCKED</span>
          </div>
          <div className="flex items-center justify-between text-neutral-400">
            <span>TOR_CIRCUIT: 3-hop relay</span>
            <span className="text-lime-400">ONLINE</span>
          </div>
        </div>
      )}
    </div>
  );
};
