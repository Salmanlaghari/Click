import React from 'react';
import { 
  ArrowLeft, 
  ArrowRight, 
  Home, 
  Layers, 
  MoreVertical,
  ShieldCheck,
  Terminal
} from 'lucide-react';
import { BrowserProfile } from '../types/browser';

interface BrowserToolbarProps {
  canGoBack: boolean;
  canGoForward: boolean;
  onGoBack: () => void;
  onGoForward: () => void;
  onGoHome: () => void;
  tabCount: number;
  onOpenTabs: () => void;
  onOpenMenu: () => void;
  currentProfile: BrowserProfile;
  onQuickDevTools?: () => void;
}

export const BrowserToolbar: React.FC<BrowserToolbarProps> = ({
  canGoBack,
  canGoForward,
  onGoBack,
  onGoForward,
  onGoHome,
  tabCount,
  onOpenTabs,
  onOpenMenu,
  currentProfile,
  onQuickDevTools,
}) => {
  return (
    <div className="h-12 bg-neutral-900 border-t border-neutral-800 flex items-center justify-around px-2 text-neutral-300 select-none flex-shrink-0 z-20">
      {/* Back button */}
      <button
        onClick={onGoBack}
        disabled={!canGoBack}
        className="p-2 rounded-xl hover:bg-neutral-800 disabled:opacity-30 disabled:hover:bg-transparent transition active:scale-95 text-neutral-300"
        title="Go Back"
      >
        <ArrowLeft className="w-4 h-4" />
      </button>

      {/* Forward button */}
      <button
        onClick={onGoForward}
        disabled={!canGoForward}
        className="p-2 rounded-xl hover:bg-neutral-800 disabled:opacity-30 disabled:hover:bg-transparent transition active:scale-95 text-neutral-300"
        title="Go Forward"
      >
        <ArrowRight className="w-4 h-4" />
      </button>

      {/* Home button */}
      <button
        onClick={onGoHome}
        className="p-2 rounded-xl hover:bg-neutral-800 transition active:scale-95 text-neutral-300"
        title="Click Home"
      >
        <Home className="w-4 h-4" />
      </button>

      {/* Developer Profile quick DevTools icon */}
      {currentProfile === 'developer' && onQuickDevTools && (
        <button
          onClick={onQuickDevTools}
          className="p-2 rounded-xl bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20 border border-emerald-500/30 transition active:scale-95"
          title="Open DevTools"
        >
          <Terminal className="w-4 h-4" />
        </button>
      )}

      {/* Tab Switcher Button with count badge */}
      <button
        onClick={onOpenTabs}
        className="p-1.5 rounded-xl hover:bg-neutral-800 transition active:scale-95 relative"
        title="Tabs"
      >
        <div className="w-6 h-6 rounded-md border-2 border-neutral-400 flex items-center justify-center font-bold text-[11px] text-neutral-200">
          {tabCount}
        </div>
      </button>

      {/* 3-dots Menu Button */}
      <button
        onClick={onOpenMenu}
        className="p-2 rounded-xl hover:bg-neutral-800 transition active:scale-95 text-neutral-300"
        title="Browser Menu"
      >
        <MoreVertical className="w-4 h-4" />
      </button>
    </div>
  );
};
