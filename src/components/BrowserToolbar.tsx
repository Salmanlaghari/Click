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
  isFullView?: boolean;
}

export const BrowserToolbar: React.FC<BrowserToolbarProps> = ({
  tabCount,
  onOpenTabs,
  onOpenMenu,
  onOpenBookmarks,
  onOpenAi,
  onQuickAction,
  currentProfile,
  isFullView = false,
}) => {
  // Mode-based bar styling
  const toolbarStyles: Record<BrowserProfile, {
    bg: string;
    text: string;
    accentButton: string;
    aiGlow: string;
  }> = {
    power: {
      bg: 'bg-[#faf6f0]/95 border-[#e2d5c4] text-[#473422]',
      text: 'text-[#5e4732] hover:text-[#2a1c0f]',
      accentButton: 'from-[#8a5b32] to-[#613c1c] text-[#f7ede1]',
      aiGlow: 'bg-[#b88657]/30 from-[#8a5b32] via-[#6e4421] to-[#452710] text-[#f7ede1]',
    },
    developer: {
      bg: 'bg-[#0f172a]/95 border-neutral-800 text-neutral-300',
      text: 'text-neutral-400 hover:text-emerald-400',
      accentButton: 'from-emerald-700 to-emerald-900 text-emerald-100',
      aiGlow: 'bg-emerald-500/25 from-emerald-600 via-emerald-800 to-neutral-950 text-emerald-200',
    },
    simple: {
      bg: 'bg-white/95 border-neutral-200 text-neutral-700',
      text: 'text-neutral-500 hover:text-sky-600',
      accentButton: 'from-sky-600 to-blue-700 text-white',
      aiGlow: 'bg-sky-500/25 from-sky-500 via-blue-600 to-indigo-900 text-white',
    },
    advance: {
      bg: 'bg-black/95 border-lime-500/30 text-lime-400',
      text: 'text-neutral-500 hover:text-lime-300',
      accentButton: 'from-lime-600 to-emerald-950 text-lime-300',
      aiGlow: 'bg-lime-500/30 from-lime-500 via-emerald-700 to-black text-lime-200',
    },
  };

  const style = toolbarStyles[currentProfile];

  // If in full view mode, toolbar is subtly translucent
  return (
    <div className={`h-16 px-4 border-t backdrop-blur-md flex items-center justify-between select-none flex-shrink-0 z-20 transition-colors ${style.bg}`}>
      {/* 1. Left Action Button (Textured Circle with 3 diagonal stripes from user screenshot) */}
      <button
        onClick={onQuickAction}
        className={`w-11 h-11 rounded-full bg-gradient-to-br ${style.accentButton} flex items-center justify-center shadow-lg active:scale-95 transition relative overflow-hidden`}
        title="Quick Action / Fast Access"
      >
        {/* 3 diagonal slashes like screenshot */}
        <div className="flex items-center gap-0.5 transform -rotate-45">
          <span className="w-1 h-5 bg-white/70 rounded-full" />
          <span className="w-1 h-5 bg-white/90 rounded-full" />
          <span className="w-1 h-5 bg-white/70 rounded-full" />
        </div>
      </button>

      {/* 2. Tabs Button with Badge */}
      <button
        onClick={onOpenTabs}
        className={`flex flex-col items-center justify-center gap-0.5 transition active:scale-95 px-2 ${style.text}`}
        title="Tabs Overview"
      >
        <div className="relative">
          <Layers className="w-5 h-5" />
          <span className="absolute -top-1 -right-1.5 w-3.5 h-3.5 rounded-full bg-amber-600 text-white font-bold text-[8px] flex items-center justify-center shadow-xs">
            {tabCount}
          </span>
        </div>
        <span className="text-[10px] font-medium">Tabs</span>
      </button>

      {/* 3. Center Glowing AI Copilot Button (Prominent Circular Button) */}
      <div className="relative -top-2 flex flex-col items-center">
        <div className="absolute -inset-1.5 rounded-full bg-amber-500/20 blur-md animate-pulse" />
        <button
          onClick={onOpenAi}
          className={`w-13 h-13 rounded-full bg-gradient-to-b border border-amber-400/40 flex flex-col items-center justify-center shadow-xl active:scale-95 transition relative z-10 ${style.aiGlow}`}
          title="Click AI Copilot"
        >
          <Sparkles className="w-5 h-5" />
          <span className="text-[9px] font-bold tracking-wider mt-0.5">AI</span>
        </button>
      </div>

      {/* 4. Bookmarks Button */}
      <button
        onClick={onOpenBookmarks}
        className={`flex flex-col items-center justify-center gap-0.5 transition active:scale-95 px-2 ${style.text}`}
        title="Bookmarks"
      >
        <Bookmark className="w-5 h-5" />
        <span className="text-[10px] font-medium">Bookmarks</span>
      </button>

      {/* 5. Menu / Sidebar Button */}
      <button
        onClick={onOpenMenu}
        className={`flex flex-col items-center justify-center gap-0.5 transition active:scale-95 px-2 ${style.text}`}
        title="Menu & Settings"
      >
        <Menu className="w-5 h-5" />
        <span className="text-[10px] font-medium">Menu</span>
      </button>
    </div>
  );
};
