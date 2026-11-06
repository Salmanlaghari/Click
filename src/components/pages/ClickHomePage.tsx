import React, { useState } from 'react';
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
  Key
} from 'lucide-react';

interface ClickHomePageProps {
  currentProfile: BrowserProfile;
  vpnConnected: boolean;
  onNavigate: (url: string) => void;
  onOpenProfilePicker: () => void;
  bookmarks: Bookmark[];
}

export const ClickHomePage: React.FC<ClickHomePageProps> = ({
  currentProfile,
  vpnConnected,
  onNavigate,
  onOpenProfilePicker,
  bookmarks,
}) => {
  const [searchQuery, setSearchQuery] = useState('');

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

  const profileConfigs = {
    simple: {
      name: 'Simple Profile',
      tagline: 'Clean, fast & distraction-free browsing',
      badgeClass: 'bg-blue-500/15 text-blue-400 border-blue-500/30',
      icon: Globe,
      accentBorder: 'border-blue-500/40',
      glow: 'from-blue-600/20 to-transparent',
    },
    developer: {
      name: 'Developer Profile',
      tagline: 'DevTools, DOM inspector, network waterfall & console',
      badgeClass: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
      icon: Terminal,
      accentBorder: 'border-emerald-500/40',
      glow: 'from-emerald-600/20 to-transparent',
    },
    power: {
      name: 'Power Profile',
      tagline: 'High performance engine, turbo media & strict adblock',
      badgeClass: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
      icon: Flame,
      accentBorder: 'border-amber-500/40',
      glow: 'from-amber-600/20 to-transparent',
    },
    advance: {
      name: 'Advance Profile (Isolated)',
      tagline: 'Separate app-data partition, zero data leakage',
      badgeClass: 'bg-lime-500/15 text-lime-400 border-lime-500/30',
      icon: Zap,
      accentBorder: 'border-lime-500/40',
      glow: 'from-lime-600/20 to-transparent',
    },
  };

  const config = profileConfigs[currentProfile];
  const ProfileIcon = config.icon;

  const quickShortcuts = [
    { name: 'V9 Shield VPN', url: 'click://vpn', icon: ShieldCheck, color: 'text-indigo-400 bg-indigo-500/10' },
    { name: 'DevTools', url: 'click://devtools', icon: Code2, color: 'text-emerald-400 bg-emerald-500/10' },
    { name: 'AI Assistant', url: 'click://ai', icon: Zap, color: 'text-purple-400 bg-purple-500/10' },
    { name: 'Arcade Game', url: 'click://games', icon: Cpu, color: 'text-pink-400 bg-pink-500/10' },
    { name: 'Passwords', url: 'click://passwords', icon: Key, color: 'text-cyan-400 bg-cyan-500/10' },
    { name: 'Downloads', url: 'click://downloads', icon: Download, color: 'text-amber-400 bg-amber-500/10' },
  ];

  const popularSites = [
    { title: 'Wikipedia', url: 'https://en.wikipedia.org/wiki/Web_browser', icon: '🌐' },
    { title: 'GitHub', url: 'https://github.com/trending', icon: '🐙' },
    { title: 'Hacker News', url: 'https://news.ycombinator.com', icon: '🟧' },
    { title: 'DuckDuckGo', url: 'https://duckduckgo.com', icon: '🦆' },
  ];

  return (
    <div className={`h-full overflow-y-auto px-4 py-5 space-y-5 ${currentProfile === 'advance' ? 'matrix-scanline bg-black font-cyber text-green-400' : 'bg-neutral-950 text-neutral-100'}`}>
      {/* Profile Header Hero */}
      <div className={`p-4 rounded-2xl bg-gradient-to-b ${config.glow} bg-neutral-900/80 border ${config.accentBorder} shadow-lg relative overflow-hidden`}>
        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${config.badgeClass}`}>
                <ProfileIcon className="w-3.5 h-3.5" />
                {config.name}
              </span>
              {vpnConnected && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-mono bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                  <Lock className="w-2.5 h-2.5" /> VPN ON
                </span>
              )}
            </div>
            <h1 className="text-xl font-bold tracking-tight mt-1 flex items-center gap-2">
              Click Browser
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-neutral-800 text-neutral-400 font-normal">v1.3.9</span>
            </h1>
            <p className="text-xs text-neutral-400 max-w-[280px]">
              {config.tagline}
            </p>
          </div>
          <button 
            onClick={onOpenProfilePicker}
            className="p-2 rounded-xl bg-neutral-800/80 hover:bg-neutral-700/80 border border-neutral-700 text-xs flex items-center gap-1.5 transition active:scale-95"
            title="Switch Profile"
          >
            <Sliders className="w-3.5 h-3.5 text-neutral-300" />
            <span className="text-[11px] font-medium hidden sm:inline">Profiles</span>
          </button>
        </div>
      </div>

      {/* Main Search Bar */}
      <form onSubmit={handleSearchSubmit} className="relative">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search web or type click:// address..."
          className="w-full pl-10 pr-10 py-3.5 rounded-2xl bg-neutral-900/90 border border-neutral-800 focus:border-neutral-600 focus:outline-none text-sm placeholder:text-neutral-500 shadow-inner transition"
        />
        <Search className="w-4 h-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        {searchQuery && (
          <button
            type="submit"
            className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1.5 rounded-xl bg-neutral-800 hover:bg-neutral-700 text-neutral-200 transition"
          >
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        )}
      </form>

      {/* Speed Dial Grid */}
      <div>
        <div className="flex items-center justify-between mb-2.5 px-0.5">
          <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">Top Shortcuts</span>
          <span className="text-[10px] text-neutral-500 font-mono">click:// internal</span>
        </div>
        <div className="grid grid-cols-3 gap-2.5">
          {quickShortcuts.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.url}
                onClick={() => onNavigate(item.url)}
                className="flex flex-col items-center justify-center p-3 rounded-2xl bg-neutral-900/70 border border-neutral-800/80 hover:border-neutral-700 transition active:scale-95 group"
              >
                <div className={`p-2 rounded-xl mb-1.5 ${item.color} group-hover:scale-110 transition`}>
                  <Icon className="w-4 h-4" />
                </div>
                <span className="text-[11px] font-medium text-neutral-200 text-center truncate w-full">{item.name}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Popular Websites */}
      <div>
        <div className="flex items-center justify-between mb-2.5 px-0.5">
          <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider">Popular Dials</span>
          <span className="text-[10px] text-neutral-500">Live preview</span>
        </div>
        <div className="grid grid-cols-4 gap-2">
          {popularSites.map((site) => (
            <button
              key={site.url}
              onClick={() => onNavigate(site.url)}
              className="flex flex-col items-center p-2.5 rounded-xl bg-neutral-900/50 border border-neutral-800/60 hover:bg-neutral-800/50 transition active:scale-95"
            >
              <div className="w-8 h-8 rounded-full bg-neutral-800 border border-neutral-700 flex items-center justify-center text-sm mb-1">
                {site.icon}
              </div>
              <span className="text-[10px] text-neutral-300 font-medium truncate w-full text-center">{site.title}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Privacy & Shield Status Card */}
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
              {vpnConnected ? 'Encrypted tunnel active · IP Hidden' : 'Tap to enable full encrypted IP-tunnel'}
            </p>
          </div>
        </div>
        <ArrowRight className="w-4 h-4 text-neutral-500 group-hover:text-indigo-400 transition transform group-hover:translate-x-0.5" />
      </div>

      {/* Dev & Tech News Feed */}
      <div className="space-y-2">
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

        <div 
          onClick={() => onNavigate('https://en.wikipedia.org/wiki/Web_browser')}
          className="p-3 rounded-xl bg-neutral-900/60 border border-neutral-800 hover:border-neutral-700 transition cursor-pointer"
        >
          <div className="flex items-center justify-between text-[11px] text-neutral-400 mb-1">
            <span className="text-blue-400 font-medium">Browser Tech</span>
            <span>4h ago</span>
          </div>
          <h4 className="text-xs font-semibold text-neutral-200 line-clamp-1">
            Multi-profile WebView isolation: Cookie partitions and storage separation
          </h4>
          <p className="text-[11px] text-neutral-400 line-clamp-1 mt-0.5">
            How Click isolates cache, cookies, and localStorage across user profiles.
          </p>
        </div>
      </div>
    </div>
  );
};
