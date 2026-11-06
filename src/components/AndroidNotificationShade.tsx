import React from 'react';
import { 
  Wifi, 
  Bluetooth, 
  ShieldCheck, 
  Moon, 
  Flashlight, 
  Sliders, 
  Power, 
  ChevronUp, 
  X, 
  CheckCircle2, 
  Download,
  Settings
} from 'lucide-react';
import { BrowserProfile } from '../types/browser';

interface AndroidNotificationShadeProps {
  isOpen: boolean;
  onClose: () => void;
  vpnConnected: boolean;
  onToggleVpn: () => void;
  currentProfile: BrowserProfile;
  onOpenProfilePicker: () => void;
  onOpenSettings: () => void;
}

export const AndroidNotificationShade: React.FC<AndroidNotificationShadeProps> = ({
  isOpen,
  onClose,
  vpnConnected,
  onToggleVpn,
  currentProfile,
  onOpenProfilePicker,
  onOpenSettings,
}) => {
  if (!isOpen) return null;

  const currentTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  return (
    <div 
      className="absolute inset-0 z-50 bg-neutral-950/95 backdrop-blur-md flex flex-col text-neutral-100 animate-in slide-in-from-top duration-200 select-none"
    >
      {/* Top Header */}
      <div className="flex items-center justify-between px-5 pt-3 pb-2 text-xs">
        <span className="font-semibold text-sm">{currentTime}</span>
        <div className="flex items-center gap-3">
          <button 
            onClick={onOpenSettings} 
            className="p-1 text-neutral-400 hover:text-white transition"
          >
            <Settings className="w-4 h-4" />
          </button>
          <button 
            onClick={onClose} 
            className="p-1 text-neutral-400 hover:text-white transition"
          >
            <ChevronUp className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Quick Settings Tiles */}
      <div className="p-4 grid grid-cols-4 gap-2.5 border-b border-neutral-800/80">
        <div className="flex flex-col items-center gap-1">
          <button className="w-12 h-12 rounded-2xl bg-blue-600 text-white flex items-center justify-center shadow-md active:scale-95 transition">
            <Wifi className="w-5 h-5" />
          </button>
          <span className="text-[10px] text-neutral-300 font-medium">Wi-Fi</span>
        </div>

        <div className="flex flex-col items-center gap-1">
          <button 
            onClick={onToggleVpn}
            className={`w-12 h-12 rounded-2xl flex items-center justify-center shadow-md active:scale-95 transition ${
              vpnConnected 
                ? 'bg-indigo-600 text-white shadow-indigo-500/30' 
                : 'bg-neutral-800 text-neutral-400'
            }`}
          >
            <ShieldCheck className="w-5 h-5" />
          </button>
          <span className="text-[10px] text-neutral-300 font-medium">Click VPN</span>
        </div>

        <div className="flex flex-col items-center gap-1">
          <button 
            onClick={onOpenProfilePicker}
            className="w-12 h-12 rounded-2xl bg-neutral-800 hover:bg-neutral-700 text-neutral-300 flex items-center justify-center shadow-md active:scale-95 transition"
          >
            <Sliders className="w-5 h-5 text-emerald-400" />
          </button>
          <span className="text-[10px] text-neutral-300 font-medium capitalize">{currentProfile}</span>
        </div>

        <div className="flex flex-col items-center gap-1">
          <button className="w-12 h-12 rounded-2xl bg-neutral-800 text-neutral-400 flex items-center justify-center shadow-md active:scale-95 transition">
            <Moon className="w-5 h-5" />
          </button>
          <span className="text-[10px] text-neutral-300 font-medium">DND</span>
        </div>
      </div>

      {/* Active Notifications List */}
      <div className="flex-1 overflow-y-auto p-4 space-y-2.5">
        <div className="flex items-center justify-between text-[11px] text-neutral-400 font-medium px-1">
          <span>Notifications</span>
          <span className="text-[10px] text-neutral-500 font-mono">Android System</span>
        </div>

        {/* VPN Persistent Notification (Real Click VPN spec) */}
        {vpnConnected ? (
          <div className="p-3.5 rounded-2xl bg-neutral-900 border border-indigo-900/40 space-y-2 shadow-sm">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center">
                  <ShieldCheck className="w-3.5 h-3.5" />
                </div>
                <div>
                  <div className="text-xs font-bold text-neutral-200">Click VPN · Active Tunnel</div>
                  <div className="text-[10px] text-neutral-400">Tunnel active · US East (New York)</div>
                </div>
              </div>
              <span className="text-[9px] font-mono text-neutral-500">now</span>
            </div>

            <div className="flex items-center justify-end gap-2 pt-1 border-t border-neutral-800/60">
              <button
                onClick={onToggleVpn}
                className="px-3 py-1 rounded-lg bg-neutral-800 hover:bg-neutral-700 text-red-400 text-[10px] font-semibold transition active:scale-95"
              >
                Disconnect
              </button>
            </div>
          </div>
        ) : (
          <div className="p-3 rounded-2xl bg-neutral-900/60 border border-neutral-800 text-xs text-neutral-400 flex items-center justify-between">
            <span>Click Browser is running in background</span>
            <span className="text-[10px] font-mono text-neutral-500">Idle</span>
          </div>
        )}

        {/* Download notification */}
        <div className="p-3.5 rounded-2xl bg-neutral-900 border border-neutral-800/80 space-y-1.5 shadow-sm">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center">
                <Download className="w-3.5 h-3.5" />
              </div>
              <div>
                <div className="text-xs font-semibold text-neutral-200">Download Complete</div>
                <div className="text-[10px] text-neutral-400">app-release-v1.3.9.apk (28.4 MB)</div>
              </div>
            </div>
            <span className="text-[9px] font-mono text-neutral-500">12m ago</span>
          </div>
        </div>
      </div>

      {/* Bottom Dismiss Handle */}
      <div 
        onClick={onClose}
        className="py-3 flex flex-col items-center justify-center cursor-pointer border-t border-neutral-900 hover:bg-neutral-900/50 transition"
      >
        <div className="w-12 h-1 rounded-full bg-neutral-700" />
        <span className="text-[9px] text-neutral-500 mt-1 uppercase tracking-wider font-mono">Swipe or tap to close</span>
      </div>
    </div>
  );
};
