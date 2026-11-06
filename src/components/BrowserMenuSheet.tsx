import React from 'react';
import { 
  Plus, 
  EyeOff, 
  Bookmark, 
  Clock, 
  Download, 
  Terminal, 
  ShieldCheck, 
  Key, 
  Monitor, 
  BookOpen, 
  Languages, 
  Settings, 
  X,
  Share2,
  HelpCircle,
  Gamepad2,
  Bot
} from 'lucide-react';

interface BrowserMenuSheetProps {
  isOpen: boolean;
  onClose: () => void;
  onNewTab: (incognito?: boolean) => void;
  onNavigate: (url: string) => void;
  isDesktop: boolean;
  onToggleDesktop: () => void;
  isReaderMode: boolean;
  onToggleReaderMode: () => void;
}

export const BrowserMenuSheet: React.FC<BrowserMenuSheetProps> = ({
  isOpen,
  onClose,
  onNewTab,
  onNavigate,
  isDesktop,
  onToggleDesktop,
  isReaderMode,
  onToggleReaderMode,
}) => {
  if (!isOpen) return null;

  const handleAction = (callback: () => void) => {
    callback();
    onClose();
  };

  return (
    <div className="absolute inset-0 z-50 bg-black/60 backdrop-blur-xs flex flex-col justify-end animate-in fade-in select-none">
      <div 
        className="w-full bg-neutral-900 border-t border-neutral-800 rounded-t-3xl p-4 max-h-[85%] overflow-y-auto space-y-3 animate-in slide-in-from-bottom duration-200"
      >
        {/* Top Handle / Close */}
        <div className="flex items-center justify-between pb-1 border-b border-neutral-800/80">
          <span className="text-xs font-bold text-neutral-300">Click Browser Menu</span>
          <button onClick={onClose} className="p-1 text-neutral-400 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Quick Row Buttons */}
        <div className="grid grid-cols-4 gap-2 text-center text-[10px]">
          <button
            onClick={() => handleAction(() => onNewTab(false))}
            className="p-2.5 rounded-2xl bg-neutral-800/90 hover:bg-neutral-700 flex flex-col items-center gap-1 text-neutral-200 active:scale-95 transition"
          >
            <Plus className="w-4 h-4 text-blue-400" />
            <span>New Tab</span>
          </button>

          <button
            onClick={() => handleAction(() => onNewTab(true))}
            className="p-2.5 rounded-2xl bg-neutral-800/90 hover:bg-neutral-700 flex flex-col items-center gap-1 text-neutral-200 active:scale-95 transition"
          >
            <EyeOff className="w-4 h-4 text-purple-400" />
            <span>Private Tab</span>
          </button>

          <button
            onClick={() => handleAction(() => onNavigate('click://vpn'))}
            className="p-2.5 rounded-2xl bg-neutral-800/90 hover:bg-neutral-700 flex flex-col items-center gap-1 text-neutral-200 active:scale-95 transition"
          >
            <ShieldCheck className="w-4 h-4 text-indigo-400" />
            <span>V9 Shield</span>
          </button>

          <button
            onClick={() => handleAction(() => onNavigate('click://ai'))}
            className="p-2.5 rounded-2xl bg-neutral-800/90 hover:bg-neutral-700 flex flex-col items-center gap-1 text-neutral-200 active:scale-95 transition"
          >
            <Bot className="w-4 h-4 text-pink-400" />
            <span>AI Copilot</span>
          </button>
        </div>

        {/* Menu Items List */}
        <div className="divide-y divide-neutral-800/60 rounded-2xl bg-neutral-950/60 border border-neutral-800/80 text-xs">
          <button
            onClick={() => handleAction(() => onNavigate('click://bookmarks'))}
            className="w-full px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition"
          >
            <span className="flex items-center gap-3">
              <Bookmark className="w-4 h-4 text-neutral-400" /> Bookmarks
            </span>
          </button>

          <button
            onClick={() => handleAction(() => onNavigate('click://history'))}
            className="w-full px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition"
          >
            <span className="flex items-center gap-3">
              <Clock className="w-4 h-4 text-neutral-400" /> Browsing History
            </span>
          </button>

          <button
            onClick={() => handleAction(() => onNavigate('click://downloads'))}
            className="w-full px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition"
          >
            <span className="flex items-center gap-3">
              <Download className="w-4 h-4 text-neutral-400" /> Downloads
            </span>
          </button>

          <button
            onClick={() => handleAction(() => onNavigate('click://passwords'))}
            className="w-full px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition"
          >
            <span className="flex items-center gap-3">
              <Key className="w-4 h-4 text-neutral-400" /> Password Vault
            </span>
          </button>

          <button
            onClick={() => handleAction(() => onNavigate('click://devtools'))}
            className="w-full px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition"
          >
            <span className="flex items-center gap-3">
              <Terminal className="w-4 h-4 text-emerald-400" /> Developer Tools
            </span>
            <span className="text-[10px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full">Console/DOM</span>
          </button>

          <button
            onClick={() => handleAction(() => onNavigate('click://games'))}
            className="w-full px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition"
          >
            <span className="flex items-center gap-3">
              <Gamepad2 className="w-4 h-4 text-green-400" /> Matrix Runner Arcade
            </span>
          </button>

          {/* Desktop site checkbox */}
          <div
            onClick={onToggleDesktop}
            className="px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition cursor-pointer"
          >
            <span className="flex items-center gap-3">
              <Monitor className="w-4 h-4 text-neutral-400" /> Desktop site
            </span>
            <input
              type="checkbox"
              checked={isDesktop}
              readOnly
              className="accent-blue-500 w-4 h-4 pointer-events-none"
            />
          </div>

          {/* Reader mode toggle */}
          <div
            onClick={onToggleReaderMode}
            className="px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition cursor-pointer"
          >
            <span className="flex items-center gap-3">
              <BookOpen className="w-4 h-4 text-neutral-400" /> Reader View
            </span>
            <input
              type="checkbox"
              checked={isReaderMode}
              readOnly
              className="accent-amber-500 w-4 h-4 pointer-events-none"
            />
          </div>

          <button
            onClick={() => handleAction(() => onNavigate('click://settings'))}
            className="w-full px-4 py-3 flex items-center justify-between text-neutral-200 hover:bg-neutral-800/50 transition"
          >
            <span className="flex items-center gap-3">
              <Settings className="w-4 h-4 text-neutral-400" /> Settings
            </span>
          </button>
        </div>
      </div>
    </div>
  );
};
