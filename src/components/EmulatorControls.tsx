import React from 'react';
import { 
  RotateCw, 
  Smartphone, 
  Terminal, 
  ShieldCheck, 
  Trash2, 
  Sparkles, 
  Info,
  Maximize2,
  Sliders,
  Check
} from 'lucide-react';
import { BrowserProfile } from '../types/browser';

interface EmulatorControlsProps {
  isLandscape: boolean;
  onToggleOrientation: () => void;
  showFrame: boolean;
  onToggleFrame: () => void;
  currentProfile: BrowserProfile;
  onSelectProfile: (profile: BrowserProfile) => void;
  onOpenDevTools: () => void;
  onOpenVpn: () => void;
  onResetEmulator: () => void;
  vpnConnected: boolean;
}

export const EmulatorControls: React.FC<EmulatorControlsProps> = ({
  isLandscape,
  onToggleOrientation,
  showFrame,
  onToggleFrame,
  currentProfile,
  onSelectProfile,
  onOpenDevTools,
  onOpenVpn,
  onResetEmulator,
  vpnConnected,
}) => {
  return (
    <div className="w-full max-w-5xl mx-auto px-4 py-2 flex flex-wrap items-center justify-between gap-3 text-xs bg-neutral-900/90 border-b border-neutral-800 text-neutral-300 backdrop-blur-md select-none z-40">
      {/* Left: Emulator Badge & Profile Selector */}
      <div className="flex items-center gap-2 flex-wrap">
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-xl bg-neutral-800/80 border border-neutral-700/80 font-mono text-[11px] text-neutral-200">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          <span className="font-bold">Android 15 Emulator</span>
          <span className="text-neutral-500">|</span>
          <span className="text-neutral-400">Click v1.3.9</span>
        </div>

        {/* Profile pills */}
        <div className="flex items-center gap-1 bg-neutral-950 p-0.5 rounded-xl border border-neutral-800">
          {(['simple', 'developer', 'power', 'advance'] as const).map((prof) => (
            <button
              key={prof}
              onClick={() => onSelectProfile(prof)}
              className={`px-2 py-0.5 rounded-lg text-[10px] font-semibold capitalize transition ${
                currentProfile === prof
                  ? 'bg-neutral-800 text-white shadow-xs'
                  : 'text-neutral-500 hover:text-neutral-300'
              }`}
            >
              {prof}
            </button>
          ))}
        </div>
      </div>

      {/* Right: Hardware & Action Controls */}
      <div className="flex items-center gap-2 flex-wrap">
        {/* Rotate Button */}
        <button
          onClick={onToggleOrientation}
          className="p-1.5 rounded-xl bg-neutral-800 hover:bg-neutral-700 text-neutral-300 flex items-center gap-1.5 transition active:scale-95 text-[11px]"
          title="Rotate Emulator"
        >
          <RotateCw className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">{isLandscape ? 'Portrait' : 'Landscape'}</span>
        </button>

        {/* Frame Toggle */}
        <button
          onClick={onToggleFrame}
          className={`p-1.5 rounded-xl border transition active:scale-95 text-[11px] flex items-center gap-1.5 ${
            showFrame
              ? 'bg-neutral-800 border-neutral-700 text-neutral-200'
              : 'bg-neutral-900 border-neutral-800 text-neutral-500'
          }`}
          title="Toggle Device Frame"
        >
          <Smartphone className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Phone Frame</span>
        </button>

        {/* Quick DevTools */}
        <button
          onClick={onOpenDevTools}
          className="p-1.5 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center gap-1.5 transition active:scale-95 text-[11px]"
        >
          <Terminal className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">DevTools</span>
        </button>

        {/* Quick VPN */}
        <button
          onClick={onOpenVpn}
          className={`p-1.5 rounded-xl border flex items-center gap-1.5 transition active:scale-95 text-[11px] ${
            vpnConnected
              ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/50'
              : 'bg-neutral-800 hover:bg-neutral-700 text-neutral-300 border-neutral-700'
          }`}
        >
          <ShieldCheck className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">{vpnConnected ? 'VPN ON' : 'V9 Shield'}</span>
        </button>

        {/* Reset Emulator */}
        <button
          onClick={onResetEmulator}
          className="p-1.5 rounded-xl bg-neutral-800 hover:bg-red-500/20 text-neutral-400 hover:text-red-400 transition active:scale-95"
          title="Reset Emulator State"
        >
          <Trash2 className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
};
