import React, { useState } from 'react';
import { 
  X, 
  Plus, 
  EyeOff, 
  Globe, 
  Fingerprint, 
  ShieldCheck, 
  Trash2,
  Check
} from 'lucide-react';
import { Tab } from '../types/browser';

interface TabSwitcherModalProps {
  isOpen: boolean;
  onClose: () => void;
  tabs: Tab[];
  activeTabId: string;
  onSelectTab: (id: string) => void;
  onCloseTab: (id: string) => void;
  onNewTab: (incognito?: boolean) => void;
}

export const TabSwitcherModal: React.FC<TabSwitcherModalProps> = ({
  isOpen,
  onClose,
  tabs,
  activeTabId,
  onSelectTab,
  onCloseTab,
  onNewTab,
}) => {
  const [activeSegment, setActiveSegment] = useState<'standard' | 'incognito'>('standard');
  const [incognitoUnlocked, setIncognitoUnlocked] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  if (!isOpen) return null;

  const standardTabs = tabs.filter((t) => !t.isIncognito);
  const incognitoTabs = tabs.filter((t) => t.isIncognito);

  const handleFingerprintAuth = () => {
    setIsAuthenticating(true);
    setTimeout(() => {
      setIsAuthenticating(false);
      setIncognitoUnlocked(true);
    }, 700);
  };

  return (
    <div className="absolute inset-0 z-50 bg-neutral-950 flex flex-col text-neutral-100 select-none animate-in fade-in duration-150">
      {/* Top Segment Bar */}
      <div className="px-4 pt-3 pb-2 flex items-center justify-between border-b border-neutral-800 flex-shrink-0">
        <div className="flex bg-neutral-900 p-0.5 rounded-2xl border border-neutral-800">
          <button
            onClick={() => setActiveSegment('standard')}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition ${
              activeSegment === 'standard' ? 'bg-neutral-800 text-white shadow-xs' : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <Globe className="w-3.5 h-3.5 text-blue-400" />
            <span>Standard</span>
            <span className="text-[10px] font-mono px-1 rounded-full bg-neutral-950 text-neutral-400">
              {standardTabs.length}
            </span>
          </button>

          <button
            onClick={() => setActiveSegment('incognito')}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition ${
              activeSegment === 'incognito' ? 'bg-purple-950/60 text-purple-300 border border-purple-500/30' : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <EyeOff className="w-3.5 h-3.5 text-purple-400" />
            <span>Private</span>
            <span className="text-[10px] font-mono px-1 rounded-full bg-neutral-950 text-neutral-400">
              {incognitoTabs.length}
            </span>
          </button>
        </div>

        <button
          onClick={() => onNewTab(activeSegment === 'incognito')}
          className="p-2 rounded-xl bg-neutral-900 hover:bg-neutral-800 text-neutral-200 transition active:scale-95"
          title="New Tab"
        >
          <Plus className="w-4 h-4" />
        </button>
      </div>

      {/* Main Tab Grid */}
      <div className="flex-1 overflow-y-auto p-4">
        {activeSegment === 'incognito' && !incognitoUnlocked ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 space-y-4">
            <div className="w-16 h-16 rounded-3xl bg-purple-500/10 border border-purple-500/30 flex items-center justify-center text-purple-400 shadow-xl">
              <Fingerprint className={`w-9 h-9 ${isAuthenticating ? 'animate-pulse text-purple-300' : ''}`} />
            </div>
            <div>
              <h3 className="text-base font-bold text-neutral-100">Private Tabs Locked</h3>
              <p className="text-xs text-neutral-400 mt-1 max-w-xs">
                BiometricPrompt verification required to view private browsing sessions.
              </p>
            </div>

            <button
              onClick={handleFingerprintAuth}
              disabled={isAuthenticating}
              className="px-5 py-2.5 rounded-2xl bg-purple-600 hover:bg-purple-500 text-white font-semibold text-xs flex items-center gap-2 transition active:scale-95 shadow-lg shadow-purple-950/50"
            >
              <Fingerprint className="w-4 h-4" />
              <span>{isAuthenticating ? 'Scanning Biometrics…' : 'Unlock Private Tabs'}</span>
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {(activeSegment === 'standard' ? standardTabs : incognitoTabs).map((tab) => {
              const isActive = tab.id === activeTabId;
              return (
                <div
                  key={tab.id}
                  onClick={() => {
                    onSelectTab(tab.id);
                    onClose();
                  }}
                  className={`p-2.5 rounded-2xl border transition-all cursor-pointer flex flex-col justify-between aspect-[3/4] relative overflow-hidden group active:scale-[0.98] ${
                    isActive
                      ? 'bg-neutral-900 border-emerald-500 shadow-md ring-1 ring-emerald-500/40'
                      : 'bg-neutral-900/60 border-neutral-800 hover:border-neutral-700'
                  }`}
                >
                  {/* Tab header */}
                  <div className="flex items-center justify-between text-xs z-10">
                    <span className="font-semibold text-neutral-200 truncate pr-2 text-[11px] leading-tight">
                      {tab.title || 'New Tab'}
                    </span>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onCloseTab(tab.id);
                      }}
                      className="p-1 rounded-md text-neutral-500 hover:text-white hover:bg-neutral-800 transition"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  {/* Thumbnail Mockup */}
                  <div className="flex-1 my-2 rounded-xl bg-neutral-950 border border-neutral-900 p-2 overflow-hidden flex flex-col justify-between text-[9px] text-neutral-500 font-mono">
                    <div className="truncate text-neutral-400">{tab.url}</div>
                    <div className="space-y-1">
                      <div className="h-1.5 w-3/4 rounded bg-neutral-800" />
                      <div className="h-1.5 w-1/2 rounded bg-neutral-800/60" />
                    </div>
                  </div>

                  {/* Tab footer */}
                  <div className="flex items-center justify-between text-[10px] text-neutral-400 z-10">
                    <span className="uppercase font-mono text-[9px] text-neutral-500">{tab.profile}</span>
                    {isActive && (
                      <span className="text-emerald-400 font-semibold flex items-center gap-0.5 text-[10px]">
                        <Check className="w-3 h-3" /> Active
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Bottom Close bar */}
      <div className="p-3 border-t border-neutral-800/80 flex items-center justify-between flex-shrink-0">
        <button
          onClick={() => {
            const currentTabs = activeSegment === 'standard' ? standardTabs : incognitoTabs;
            currentTabs.forEach((t) => onCloseTab(t.id));
          }}
          className="text-xs text-neutral-400 hover:text-red-400 transition flex items-center gap-1.5 px-2"
        >
          <Trash2 className="w-3.5 h-3.5" /> Close All
        </button>

        <button
          onClick={onClose}
          className="px-4 py-2 rounded-xl bg-neutral-800 hover:bg-neutral-700 text-xs font-semibold text-neutral-200 transition active:scale-95"
        >
          Done
        </button>
      </div>
    </div>
  );
};
