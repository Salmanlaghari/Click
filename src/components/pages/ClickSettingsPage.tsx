import React, { useState } from 'react';
import { 
  BrowserProfile 
} from '../../types/browser';
import { 
  Shield, 
  Lock, 
  Sliders, 
  Trash2, 
  Globe, 
  EyeOff, 
  Layers, 
  Smartphone, 
  Check, 
  Cpu, 
  FileCode,
  Info
} from 'lucide-react';

interface ClickSettingsPageProps {
  currentProfile: BrowserProfile;
  onSelectProfile: (profile: BrowserProfile) => void;
  onClearData: () => void;
}

export const ClickSettingsPage: React.FC<ClickSettingsPageProps> = ({
  currentProfile,
  onSelectProfile,
  onClearData,
}) => {
  const [adblockEnabled, setAdblockEnabled] = useState(true);
  const [httpsOnly, setHttpsOnly] = useState(true);
  const [gpcSignal, setGpcSignal] = useState(true);
  const [desktopModeDefault, setDesktopModeDefault] = useState(false);
  const [clearedNotice, setClearedNotice] = useState(false);

  const handleClear = () => {
    onClearData();
    setClearedNotice(true);
    setTimeout(() => setClearedNotice(false), 2000);
  };

  return (
    <div className="h-full overflow-y-auto p-4 space-y-4 bg-neutral-950 text-neutral-100 text-xs">
      <div className="border-b border-neutral-800 pb-3">
        <h2 className="text-base font-bold flex items-center gap-2">
          <Sliders className="w-4 h-4 text-neutral-400" /> Click Browser Settings
        </h2>
        <p className="text-[11px] text-neutral-400">Android System WebView & Profile Management</p>
      </div>

      {/* Profile Selector Section */}
      <div className="space-y-2">
        <span className="font-semibold text-neutral-400 uppercase tracking-wider text-[10px]">
          Active Profile Partition
        </span>
        
        <div className="grid grid-cols-2 gap-2">
          {[
            { id: 'simple' as const, name: 'Simple', desc: 'Clean, light, shared data', color: 'border-blue-500/50 bg-blue-500/10' },
            { id: 'developer' as const, name: 'Developer', desc: 'DevTools, console, DOM inspector', color: 'border-emerald-500/50 bg-emerald-500/10' },
            { id: 'power' as const, name: 'Power', desc: 'Turbo stream, aggressive adblock', color: 'border-amber-500/50 bg-amber-500/10' },
            { id: 'advance' as const, name: 'Advance', desc: 'Isolated DB store & cyber UI', color: 'border-lime-500/50 bg-lime-500/10' },
          ].map((prof) => (
            <button
              key={prof.id}
              onClick={() => onSelectProfile(prof.id)}
              className={`p-3 rounded-2xl border text-left transition active:scale-95 ${
                currentProfile === prof.id
                  ? `${prof.color} ring-1 ring-white/20`
                  : 'bg-neutral-900 border-neutral-800 hover:border-neutral-700'
              }`}
            >
              <div className="flex items-center justify-between font-bold text-xs">
                <span>{prof.name}</span>
                {currentProfile === prof.id && <Check className="w-3.5 h-3.5 text-white" />}
              </div>
              <div className="text-[10px] text-neutral-400 mt-1">{prof.desc}</div>
            </button>
          ))}
        </div>
      </div>

      {/* Privacy & Guards */}
      <div className="space-y-2">
        <span className="font-semibold text-neutral-400 uppercase tracking-wider text-[10px]">
          Privacy Guards & AdBlock
        </span>

        <div className="p-3.5 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <div className="font-semibold text-neutral-200">Ad & Tracker Blocker</div>
              <div className="text-[10px] text-neutral-400">EasyList & Peter Lowe filter active (54,210 rules)</div>
            </div>
            <input
              type="checkbox"
              checked={adblockEnabled}
              onChange={(e) => setAdblockEnabled(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">HTTPS-Only Enforcement</div>
              <div className="text-[10px] text-neutral-400">Automatically upgrade HTTP requests to secure HTTPS</div>
            </div>
            <input
              type="checkbox"
              checked={httpsOnly}
              onChange={(e) => setHttpsOnly(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">Global Privacy Control (GPC)</div>
              <div className="text-[10px] text-neutral-400">Send voluntary Sec-GPC: 1 anti-tracking header</div>
            </div>
            <input
              type="checkbox"
              checked={gpcSignal}
              onChange={(e) => setGpcSignal(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>
        </div>
      </div>

      {/* Browsing Experience */}
      <div className="space-y-2">
        <span className="font-semibold text-neutral-400 uppercase tracking-wider text-[10px]">
          Display & WebView
        </span>

        <div className="p-3.5 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <div className="font-semibold text-neutral-200">Default to Desktop Site</div>
              <div className="text-[10px] text-neutral-400">Request desktop user-agent for all new tabs</div>
            </div>
            <input
              type="checkbox"
              checked={desktopModeDefault}
              onChange={(e) => setDesktopModeDefault(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>
        </div>
      </div>

      {/* Clear Cache & History */}
      <div className="pt-2">
        <button
          onClick={handleClear}
          className="w-full p-3 rounded-2xl bg-red-500/10 hover:bg-red-500/20 border border-red-500/30 text-red-400 font-semibold flex items-center justify-center gap-2 transition active:scale-95"
        >
          <Trash2 className="w-4 h-4" />
          <span>{clearedNotice ? '✓ Data Cleared for Active Profile' : 'Clear Cookies, Cache & History'}</span>
        </button>
      </div>

      {/* App Build Info */}
      <div className="p-3 rounded-xl bg-neutral-900/40 border border-neutral-800 text-[10px] text-neutral-400 space-y-1">
        <div className="flex justify-between">
          <span>Click Browser Build:</span>
          <span className="font-mono text-neutral-300">v1.3.9-release</span>
        </div>
        <div className="flex justify-between">
          <span>Target SDK:</span>
          <span className="font-mono text-neutral-300">Android 36 (UpsideDownCake)</span>
        </div>
        <div className="flex justify-between">
          <span>VPN Engine:</span>
          <span className="font-mono text-neutral-300">sing-box v1.14.3 gVisor</span>
        </div>
      </div>
    </div>
  );
};
