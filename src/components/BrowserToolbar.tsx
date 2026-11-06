import React from 'react';
import { 
  Sparkles, 
  Bookmark, 
  Menu, 
  Sliders, 
  Command,
  Layers,
  Bot
} from 'lucide-react';
import { BrowserProfile } from '../types/browser';

interface BrowserToolbarProps {
  tabCount: number;
  onOpenTabs: () => void;
  onOpenMenu: () => void;
  onOpenBookmarks: () => void;
  onOpenAi: () => void;
  onQuickAction: () => void;
  currentProfile: BrowserProfile;
}

export const BrowserToolbar: React.FC<BrowserToolbarProps> = ({
  tabCount,
  onOpenTabs,
  onOpenMenu,
  onOpenBookmarks,
  onOpenAi,
  onQuickAction,
  currentProfile,
}) => {
  return (
    <div className="h-16 px-4 bg-neutral-950/95 border-t border-neutral-800/80 backdrop-blur-md flex items-center justify-between text-neutral-300 select-none flex-shrink-0 z-20">
      {/* 1. Left Action Button (Textured Circle from screenshot) */}
      <button
        onClick={onQuickAction}
        className="w-11 h-11 rounded-full bg-gradient-to-br from-amber-600 to-amber-800 text-amber-100 flex items-center justify-center shadow-lg shadow-amber-950/40 active:scale-95 transition"
        title="Quick Action"
      >
        <Command className="w-5 h-5" />
      </button>

      {/* 2. Tabs Button with Badge */}
      <button
        onClick={onOpenTabs}
        className="flex flex-col items-center justify-center gap-0.5 text-neutral-400 hover:text-neutral-200 transition active:scale-95 px-2"
        title="Tabs"
      >
        <div className="relative">
          <Layers className="w-5 h-5" />
          <span className="absolute -top-1 -right-1.5 w-3.5 h-3.5 rounded-full bg-amber-500 text-black font-bold text-[8px] flex items-center justify-center">
            {tabCount}
          </span>
        </div>
        <span className="text-[10px] font-medium">Tabs</span>
      </button>

      {/* 3. Center Glowing AI Copilot Button (Prominent Circular Button) */}
      <div className="relative -top-2 flex flex-col items-center">
        <div className="absolute -inset-1.5 rounded-full bg-amber-500/25 blur-md animate-pulse" />
        <button
          onClick={onOpenAi}
          className="w-13 h-13 rounded-full bg-gradient-to-b from-amber-700 via-amber-800 to-amber-950 border border-amber-400/40 text-amber-200 flex flex-col items-center justify-center shadow-xl shadow-amber-900/50 active:scale-95 transition relative z-10"
          title="Click AI Copilot"
        >
          <Sparkles className="w-5 h-5 text-amber-200" />
          <span className="text-[9px] font-bold tracking-wider mt-0.5">AI</span>
        </button>
      </div>

      {/* 4. Bookmarks Button */}
      <button
        onClick={onOpenBookmarks}
        className="flex flex-col items-center justify-center gap-0.5 text-neutral-400 hover:text-neutral-200 transition active:scale-95 px-2"
        title="Bookmarks"
      >
        <Bookmark className="w-5 h-5" />
        <span className="text-[10px] font-medium">Bookmarks</span>
      </button>

      {/* 5. Menu Button */}
      <button
        onClick={onOpenMenu}
        className="flex flex-col items-center justify-center gap-0.5 text-neutral-400 hover:text-neutral-200 transition active:scale-95 px-2"
        title="Menu"
      >
        <Menu className="w-5 h-5" />
        <span className="text-[10px] font-medium">Menu</span>
      </button>
    </div>
  );
};
