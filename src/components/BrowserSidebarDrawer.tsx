import React from 'react';
import { 
  X, 
  ShieldCheck, 
  Sliders, 
  Terminal, 
  Key, 
  Download, 
  Bookmark, 
  Clock, 
  Sparkles, 
  Gamepad2, 
  Laptop, 
  Maximize2, 
  Minimize2, 
  BookOpen, 
  Puzzle, 
  Trash2, 
  EyeOff, 
  Check, 
  ChevronRight, 
  Globe, 
  HardDrive, 
  Cookie, 
  Wrench, 
  Search, 
  Lock,
  Zap,
  Flame,
  UserCheck
} from 'lucide-react';
import { BrowserProfile } from '../types/browser';

interface BrowserSidebarDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  currentProfile: BrowserProfile;
  onSelectProfile: (profile: BrowserProfile) => void;
  vpnConnected: boolean;
  onToggleVpn: () => void;
  isFullView: boolean;
  onToggleFullView: () => void;
  isDesktop: boolean;
  onToggleDesktop: () => void;
  isReaderMode: boolean;
  onToggleReaderMode: () => void;
  onNavigate: (url: string) => void;
  onOpenExtensions: () => void;
  onClearData: () => void;
  activeExtensionsCount: number;
}

export const BrowserSidebarDrawer: React.FC<BrowserSidebarDrawerProps> = ({
  isOpen,
  onClose,
  currentProfile,
  onSelectProfile,
  vpnConnected,
  onToggleVpn,
  isFullView,
  onToggleFullView,
  isDesktop,
  onToggleDesktop,
  isReaderMode,
  onToggleReaderMode,
  onNavigate,
  onOpenExtensions,
  onClearData,
  activeExtensionsCount,
}) => {
  if (!isOpen) return null;

  const handleNav = (url: string) => {
    onNavigate(url);
    onClose();
  };

  const profileData: Record<BrowserProfile, { name: string; tag: string; badgeColor: string }> = {
    simple: { name: 'Simple Mode', tag: 'Fast, minimal & distraction-free', badgeColor: 'bg-blue-500/20 text-blue-300 border-blue-500/40' },
    developer: { name: 'Developer Mode', tag: 'DOM Inspector, Console & Network', badgeColor: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40' },
    power: { name: 'Power Mode', tag: 'Desktop-style browsing · Wide-page', badgeColor: 'bg-amber-500/20 text-amber-300 border-amber-500/40' },
    advance: { name: 'Advance Mode', tag: 'Military sandbox · Isolated DB storage', badgeColor: 'bg-lime-500/20 text-lime-300 border-lime-500/40' },
  };

  return (
    <div className="absolute inset-0 z-50 bg-black/70 backdrop-blur-xs flex animate-in fade-in select-none">
      {/* Sidebar Panel */}
      <div className="w-[85%] max-w-[320px] h-full bg-neutral-900 border-r border-neutral-800 flex flex-col shadow-2xl animate-in slide-in-from-left duration-250">
        {/* Header */}
        <div className="p-4 border-b border-neutral-800 bg-neutral-950/60 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-2xl bg-amber-500/20 border border-amber-500/40 text-amber-300 flex items-center justify-center font-bold text-sm shadow-sm">
              C
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-sm text-neutral-100">Click Browser</span>
                <span className="text-[10px] px-1.5 py-0.2 rounded-md bg-amber-500/20 text-amber-300 font-mono font-semibold">
                  v9.5
                </span>
              </div>
              <p className="text-[10px] text-neutral-400">by Salmanlaghari · Android 15</p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="p-1.5 rounded-xl text-neutral-400 hover:text-white hover:bg-neutral-800 transition"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-y-auto p-3 space-y-4 text-xs">
          {/* Active Profile Section */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between px-1">
              <span className="text-[10px] font-bold uppercase tracking-wider text-neutral-400">
                Active Profile
              </span>
              <span className={`text-[9px] px-2 py-0.5 rounded-full font-bold uppercase border ${profileData[currentProfile]?.badgeColor || profileData.power.badgeColor}`}>
                {currentProfile}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-1.5">
              {(['simple', 'developer', 'power', 'advance'] as const).map((prof) => {
                const isSelected = currentProfile === prof;
                return (
                  <button
                    key={prof}
                    onClick={() => onSelectProfile(prof)}
                    className={`p-2 rounded-xl text-left border transition active:scale-95 ${
                      isSelected
                        ? 'bg-amber-500/15 border-amber-500/50 text-amber-200 shadow-xs'
                        : 'bg-neutral-950/60 border-neutral-800 text-neutral-400 hover:text-neutral-200'
                    }`}
                  >
                    <div className="flex items-center justify-between font-semibold text-[11px] capitalize">
                      <span>{prof}</span>
                      {isSelected && <Check className="w-3 h-3 text-amber-300" />}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* V9 Shield & SingBox VPN Quick Tile */}
          <div 
            onClick={() => handleNav('click://vpn')}
            className={`p-3 rounded-2xl border transition cursor-pointer flex items-center justify-between ${
              vpnConnected
                ? 'bg-indigo-950/40 border-indigo-700/60 text-indigo-200'
                : 'bg-neutral-950/60 border-neutral-800 text-neutral-400'
            }`}
          >
            <div className="flex items-center gap-2.5">
              <div className={`p-2 rounded-xl ${vpnConnected ? 'bg-indigo-500/20 text-indigo-300' : 'bg-neutral-800 text-neutral-400'}`}>
                <ShieldCheck className="w-4 h-4" />
              </div>
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="font-bold text-xs text-neutral-200">V9 Shield VPN</span>
                  <span className={`w-1.5 h-1.5 rounded-full ${vpnConnected ? 'bg-emerald-400 animate-pulse' : 'bg-neutral-600'}`} />
                </div>
                <p className="text-[10px] text-neutral-400">
                  {vpnConnected ? 'SingBox TUN · Singapore Direct' : 'Disconnected · Tap to connect'}
                </p>
              </div>
            </div>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onToggleVpn();
              }}
              className={`px-2.5 py-1 rounded-lg text-[10px] font-bold transition ${
                vpnConnected ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40' : 'bg-neutral-800 text-neutral-300'
              }`}
            >
              {vpnConnected ? 'ON' : 'OFF'}
            </button>
          </div>

          {/* Web Surfer Controls (Full View / Desktop / Reader) */}
          <div className="space-y-1.5">
            <span className="text-[10px] font-bold uppercase tracking-wider text-neutral-400 px-1">
              Surfer & View Controls
            </span>

            <div className="space-y-1 rounded-2xl bg-neutral-950/60 border border-neutral-800 p-1.5">
              {/* Full View Toggle */}
              <button
                onClick={onToggleFullView}
                className="w-full p-2 rounded-xl hover:bg-neutral-800 flex items-center justify-between text-neutral-300 transition"
              >
                <div className="flex items-center gap-2">
                  <Maximize2 className={`w-4 h-4 ${isFullView ? 'text-amber-400' : 'text-neutral-400'}`} />
                  <span className="text-[11px] font-medium">Full View (Immersive)</span>
                </div>
                <span className={`text-[10px] px-1.5 py-0.2 rounded font-semibold ${
                  isFullView ? 'bg-amber-500/20 text-amber-300' : 'bg-neutral-800 text-neutral-400'
                }`}>
                  {isFullView ? 'Active' : 'Off'}
                </span>
              </button>

              {/* Desktop Site Toggle */}
              <button
                onClick={onToggleDesktop}
                className="w-full p-2 rounded-xl hover:bg-neutral-800 flex items-center justify-between text-neutral-300 transition"
              >
                <div className="flex items-center gap-2">
                  <Laptop className={`w-4 h-4 ${isDesktop ? 'text-amber-400' : 'text-neutral-400'}`} />
                  <span className="text-[11px] font-medium">Desktop Site</span>
                </div>
                <span className={`text-[10px] px-1.5 py-0.2 rounded font-semibold ${
                  isDesktop ? 'bg-amber-500/20 text-amber-300' : 'bg-neutral-800 text-neutral-400'
                }`}>
                  {isDesktop ? 'On' : 'Off'}
                </span>
              </button>

              {/* Reader Mode Toggle */}
              <button
                onClick={onToggleReaderMode}
                className="w-full p-2 rounded-xl hover:bg-neutral-800 flex items-center justify-between text-neutral-300 transition"
              >
                <div className="flex items-center gap-2">
                  <BookOpen className={`w-4 h-4 ${isReaderMode ? 'text-amber-400' : 'text-neutral-400'}`} />
                  <span className="text-[11px] font-medium">Reader Mode</span>
                </div>
                <span className={`text-[10px] px-1.5 py-0.2 rounded font-semibold ${
                  isReaderMode ? 'bg-amber-500/20 text-amber-300' : 'bg-neutral-800 text-neutral-400'
                }`}>
                  {isReaderMode ? 'On' : 'Off'}
                </span>
              </button>
            </div>
          </div>

          {/* Extensions Hub Tile */}
          <div 
            onClick={() => {
              onOpenExtensions();
              onClose();
            }}
            className="p-3 rounded-2xl bg-neutral-950/60 border border-neutral-800 hover:border-amber-500/40 transition cursor-pointer flex items-center justify-between group"
          >
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-xl bg-amber-500/15 text-amber-300">
                <Puzzle className="w-4 h-4" />
              </div>
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="font-bold text-xs text-neutral-200">Extensions Hub</span>
                  <span className="text-[9px] px-1.5 py-0.2 rounded-full bg-emerald-500/20 text-emerald-300 font-semibold">
                    {activeExtensionsCount} active
                  </span>
                </div>
                <p className="text-[10px] text-neutral-400">AdBlock · Dark Reader · Translator</p>
              </div>
            </div>
            <ChevronRight className="w-4 h-4 text-neutral-500 group-hover:text-amber-300 transition" />
          </div>

          {/* All Click Browser Navigation Tools (All Settings Preserved) */}
          <div className="space-y-1">
            <span className="text-[10px] font-bold uppercase tracking-wider text-neutral-400 px-1">
              Tools & Navigation
            </span>

            <div className="rounded-2xl bg-neutral-950/60 border border-neutral-800 divide-y divide-neutral-850 overflow-hidden">
              <button
                onClick={() => handleNav('click://home')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Globe className="w-4 h-4 text-blue-400" />
                <span className="text-[11px] font-medium">Home & 16-Tool Grid</span>
              </button>

              <button
                onClick={() => handleNav('click://history')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Clock className="w-4 h-4 text-amber-400" />
                <span className="text-[11px] font-medium">Browsing History</span>
              </button>

              <button
                onClick={() => handleNav('click://bookmarks')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Bookmark className="w-4 h-4 text-orange-400" />
                <span className="text-[11px] font-medium">Bookmarks & Stars</span>
              </button>

              <button
                onClick={() => handleNav('click://passwords')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Key className="w-4 h-4 text-emerald-400" />
                <span className="text-[11px] font-medium">Password Vault</span>
              </button>

              <button
                onClick={() => handleNav('click://downloads')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Download className="w-4 h-4 text-emerald-400" />
                <span className="text-[11px] font-medium">Downloads Manager</span>
              </button>

              <button
                onClick={() => handleNav('click://devtools')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Terminal className="w-4 h-4 text-cyan-400" />
                <span className="text-[11px] font-medium">DevTools & JS Console</span>
              </button>

              <button
                onClick={() => handleNav('click://ai')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Sparkles className="w-4 h-4 text-pink-400" />
                <span className="text-[11px] font-medium">AI Copilot Chat</span>
              </button>

              <button
                onClick={() => handleNav('click://games')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Gamepad2 className="w-4 h-4 text-purple-400" />
                <span className="text-[11px] font-medium">Cyber Matrix Game</span>
              </button>

              <button
                onClick={() => handleNav('click://settings')}
                className="w-full p-2.5 flex items-center gap-2.5 text-neutral-300 hover:bg-neutral-800 transition"
              >
                <Sliders className="w-4 h-4 text-neutral-400" />
                <span className="text-[11px] font-medium">All Browser Settings</span>
              </button>
            </div>
          </div>

          {/* Clear Cache & Data */}
          <div className="pt-1">
            <button
              onClick={() => {
                onClearData();
                onClose();
              }}
              className="w-full p-2.5 rounded-xl bg-red-500/10 hover:bg-red-500/20 border border-red-500/30 text-red-400 font-semibold flex items-center justify-center gap-2 transition active:scale-95"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Clear Cache & Data</span>
            </button>
          </div>
        </div>

        {/* Footer info */}
        <div className="p-3 border-t border-neutral-800 bg-neutral-950/60 text-[10px] text-neutral-500 flex items-center justify-between">
          <span>Click Engine 15.0</span>
          <span className="text-emerald-400 font-mono">Protected</span>
        </div>
      </div>

      {/* Backdrop tap to close */}
      <div className="flex-1 h-full cursor-pointer" onClick={onClose} />
    </div>
  );
};
