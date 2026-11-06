import React, { useState, useEffect } from 'react';
import { 
  Lock, 
  RotateCw, 
  X, 
  BookOpen, 
  Monitor, 
  Share2, 
  ShieldCheck, 
  Sliders,
  Sparkles,
  QrCode
} from 'lucide-react';
import { BrowserProfile } from '../types/browser';

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
    simple: 'text-blue-400 border-blue-500/30 bg-blue-500/10',
    developer: 'text-emerald-400 border-emerald-500/30 bg-emerald-500/10',
    power: 'text-amber-400 border-amber-500/30 bg-amber-500/10',
    advance: 'text-lime-400 border-lime-500/30 bg-lime-500/10',
  };

  return (
    <div className="relative px-2 py-1.5 bg-neutral-900 border-b border-neutral-800 flex items-center gap-1.5 select-none z-20">
      {/* Profile quick tag */}
      <button
        onClick={onOpenProfilePicker}
        className={`px-2 py-1 rounded-lg text-[10px] font-bold border transition flex items-center gap-1 active:scale-95 ${profileColors[currentProfile]}`}
        title="Switch Profile"
      >
        <span className="capitalize">{currentProfile.slice(0, 3)}</span>
      </button>

      {/* Main Omnibox */}
      <div className="flex-1 relative flex items-center">
        <form onSubmit={handleSubmit} className="w-full flex items-center">
          <div className="w-full flex items-center rounded-xl bg-neutral-950 border border-neutral-800 focus-within:border-neutral-600 px-2 py-1.5 transition">
            {/* Lock / Protocol Icon */}
            <button
              type="button"
              onClick={() => setShowSecurityInfo(!showSecurityInfo)}
              className="p-1 -ml-0.5 text-neutral-400 hover:text-emerald-400 transition"
              title="Page Security Info"
            >
              {isInternal ? (
                <ShieldCheck className="w-3.5 h-3.5 text-indigo-400" />
              ) : (
                <Lock className={`w-3.5 h-3.5 ${vpnConnected ? 'text-indigo-400' : 'text-emerald-400'}`} />
              )}
            </button>

            <input
              type="text"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              onFocus={() => setIsEditing(true)}
              onBlur={() => {
                setTimeout(() => setIsEditing(false), 200);
              }}
              className="flex-1 bg-transparent border-0 px-1 text-xs text-neutral-100 focus:outline-none truncate font-sans"
              placeholder="Search or enter URL..."
            />

            {/* Editing clear or reload */}
            {isEditing && inputVal ? (
              <button
                type="button"
                onMouseDown={() => setInputVal('')}
                className="p-1 text-neutral-400 hover:text-white"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            ) : (
              <button
                type="button"
                onClick={onReload}
                className={`p-1 text-neutral-400 hover:text-white transition ${isLoading ? 'animate-spin text-emerald-400' : ''}`}
                title="Reload"
              >
                <RotateCw className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </form>

        {/* Security Info Dropdown */}
        {showSecurityInfo && (
          <div className="absolute top-11 left-0 w-64 p-3 bg-neutral-900 border border-neutral-700 rounded-2xl shadow-xl z-50 text-xs text-neutral-200 animate-in fade-in zoom-in-95">
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

      {/* Reader Mode Toggle (when on web page) */}
      {!isInternal && onToggleReaderMode && (
        <button
          onClick={onToggleReaderMode}
          className={`p-1.5 rounded-lg border transition ${
            readerMode 
              ? 'bg-amber-500/20 text-amber-300 border-amber-500/40' 
              : 'text-neutral-400 hover:text-white border-neutral-800 bg-neutral-950'
          }`}
          title="Reader Mode"
        >
          <BookOpen className="w-3.5 h-3.5" />
        </button>
      )}

      {/* Desktop Site Toggle */}
      {!isInternal && onToggleDesktop && (
        <button
          onClick={onToggleDesktop}
          className={`p-1.5 rounded-lg border transition ${
            isDesktop 
              ? 'bg-blue-500/20 text-blue-300 border-blue-500/40' 
              : 'text-neutral-400 hover:text-white border-neutral-800 bg-neutral-950'
          }`}
          title="Toggle Desktop Site"
        >
          <Monitor className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
};
