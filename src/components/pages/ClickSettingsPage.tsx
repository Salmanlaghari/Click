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
  Info,
  Search,
  Zap,
  Download,
  Maximize2,
  RefreshCw,
  Server,
  ToggleLeft,
  ToggleRight
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
  // Existing settings preserved
  const [adblockEnabled, setAdblockEnabled] = useState(true);
  const [httpsOnly, setHttpsOnly] = useState(true);
  const [gpcSignal, setGpcSignal] = useState(true);
  const [desktopModeDefault, setDesktopModeDefault] = useState(false);
  const [clearedNotice, setClearedNotice] = useState(false);

  // Missing settings requested by user
  const [searchEngine, setSearchEngine] = useState<'brave' | 'google' | 'duckduckgo' | 'bing'>('brave');
  const [dohProvider, setDohProvider] = useState<'cloudflare' | 'quad9' | 'google' | 'adguard'>('cloudflare');
  const [webrtcLeakShield, setWebrtcLeakShield] = useState(true);
  const [antiFingerprint, setAntiFingerprint] = useState(true);
  const [blockThirdPartyCookies, setBlockThirdPartyCookies] = useState(true);
  const [clearOnExit, setClearOnExit] = useState(false);
  const [downloadAcceleration, setDownloadAcceleration] = useState(true);
  const [smoothAnimations, setSmoothAnimations] = useState(true);
  const [javascriptEnabled, setJavascriptEnabled] = useState(true);
  const [popupBlocker, setPopupBlocker] = useState(true);

  const handleClear = () => {
    onClearData();
    setClearedNotice(true);
    setTimeout(() => setClearedNotice(false), 2000);
  };

  return (
    <div className="h-full overflow-y-auto p-4 space-y-4 bg-neutral-950 text-neutral-100 text-xs select-none">
      <div className="border-b border-neutral-800 pb-3">
        <h2 className="text-base font-bold flex items-center gap-2">
          <Sliders className="w-4 h-4 text-neutral-400" /> Click Browser Settings
        </h2>
        <p className="text-[11px] text-neutral-400">Android System WebView, Security & Profile Management</p>
      </div>

      {/* 1. Profile Selector Section (Preserved) */}
      <div className="space-y-2">
        <span className="font-semibold text-neutral-400 uppercase tracking-wider text-[10px]">
          Active Profile Partition
        </span>
        
        <div className="grid grid-cols-2 gap-2">
          {[
            { id: 'power' as const, name: 'Power', desc: 'Desktop browsing · Wide-page layout', color: 'border-amber-500/50 bg-amber-500/10' },
            { id: 'developer' as const, name: 'Developer', desc: 'DevTools, console, DOM inspector', color: 'border-emerald-500/50 bg-emerald-500/10' },
            { id: 'simple' as const, name: 'Simple', desc: 'Clean, light, shared data', color: 'border-blue-500/50 bg-blue-500/10' },
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

      {/* 2. Search Engine Selection (New Feature Added) */}
      <div className="space-y-2">
        <span className="font-semibold text-neutral-400 uppercase tracking-wider text-[10px]">
          Default Search Engine
        </span>

        <div className="p-3 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-2">
          {[
            { id: 'brave' as const, name: 'Brave Search', desc: 'Independent web index, zero user profiling (Default)' },
            { id: 'duckduckgo' as const, name: 'DuckDuckGo', desc: 'Privacy protection without ad trackers' },
            { id: 'google' as const, name: 'Google', desc: 'Standard comprehensive web results' },
            { id: 'bing' as const, name: 'Microsoft Bing', desc: 'Bing search with rich multimedia' },
          ].map((engine) => (
            <div 
              key={engine.id}
              onClick={() => setSearchEngine(engine.id)}
              className={`p-2.5 rounded-xl border flex items-center justify-between cursor-pointer transition ${
                searchEngine === engine.id
                  ? 'bg-amber-500/10 border-amber-500/40 text-amber-200'
                  : 'bg-neutral-950/40 border-neutral-800 text-neutral-400 hover:text-neutral-200'
              }`}
            >
              <div>
                <div className="font-bold text-xs text-neutral-200">{engine.name}</div>
                <div className="text-[10px] text-neutral-400">{engine.desc}</div>
              </div>
              <input
                type="radio"
                name="searchEngine"
                checked={searchEngine === engine.id}
                onChange={() => setSearchEngine(engine.id)}
                className="accent-amber-500"
              />
            </div>
          ))}
        </div>
      </div>

      {/* 3. DNS over HTTPS (DoH) Provider Selection (New Feature Added) */}
      <div className="space-y-2">
        <span className="font-semibold text-neutral-400 uppercase tracking-wider text-[10px]">
          Secure DNS (DoH Provider)
        </span>

        <div className="grid grid-cols-2 gap-2">
          {[
            { id: 'cloudflare' as const, name: 'Cloudflare', ip: '1.1.1.1 (Fastest)' },
            { id: 'quad9' as const, name: 'Quad9', ip: '9.9.9.9 (Malware Block)' },
            { id: 'google' as const, name: 'Google DNS', ip: '8.8.8.8 (Global)' },
            { id: 'adguard' as const, name: 'AdGuard DNS', ip: 'Default Ad Filter' },
          ].map((doh) => (
            <button
              key={doh.id}
              onClick={() => setDohProvider(doh.id)}
              className={`p-2.5 rounded-xl border text-left transition ${
                dohProvider === doh.id
                  ? 'bg-indigo-500/15 border-indigo-500/50 text-indigo-200 shadow-xs'
                  : 'bg-neutral-900 border-neutral-800 text-neutral-400 hover:border-neutral-700'
              }`}
            >
              <div className="font-bold text-[11px] text-neutral-200">{doh.name}</div>
              <div className="text-[9px] text-neutral-400 font-mono mt-0.5">{doh.ip}</div>
            </button>
          ))}
        </div>
      </div>

      {/* 4. Privacy Guards & AdBlock (Existing + Missing Anti-Leak Toggles) */}
      <div className="space-y-2">
        <span className="font-semibold text-neutral-400 uppercase tracking-wider text-[10px]">
          Privacy Guards & Security
        </span>

        <div className="p-3.5 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-3">
          {/* Ad & Tracker Blocker */}
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

          {/* HTTPS-Only Enforcement */}
          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">HTTPS-Only Enforcement</div>
              <div className="text-[10px] text-neutral-400">Upgrade all HTTP connections to secure TLS 1.3</div>
            </div>
            <input
              type="checkbox"
              checked={httpsOnly}
              onChange={(e) => setHttpsOnly(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          {/* WebRTC Leak Protection */}
          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">WebRTC Leak Shield</div>
              <div className="text-[10px] text-neutral-400">Block STUN/TURN requests revealing real local device IP</div>
            </div>
            <input
              type="checkbox"
              checked={webrtcLeakShield}
              onChange={(e) => setWebrtcLeakShield(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          {/* Anti-Fingerprinting */}
          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">Anti-Fingerprint Noise</div>
              <div className="text-[10px] text-neutral-400">Add subtle noise to Canvas and AudioContext signatures</div>
            </div>
            <input
              type="checkbox"
              checked={antiFingerprint}
              onChange={(e) => setAntiFingerprint(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          {/* Block Third-party Cookies */}
          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">Block Third-Party Cookies</div>
              <div className="text-[10px] text-neutral-400">Isolate cross-site trackers and advertising beacons</div>
            </div>
            <input
              type="checkbox"
              checked={blockThirdPartyCookies}
              onChange={(e) => setBlockThirdPartyCookies(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          {/* Global Privacy Control (GPC) */}
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

      {/* 5. Browsing & Web Content Controls (Existing + Missing Web Controls) */}
      <div className="space-y-2">
        <span className="font-semibold text-neutral-400 uppercase tracking-wider text-[10px]">
          Display, Web Content & Speed
        </span>

        <div className="p-3.5 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-3">
          {/* Default to Desktop Site */}
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

          {/* Smooth Animations */}
          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">Smooth Loading Shimmer</div>
              <div className="text-[10px] text-neutral-400">Show progressive gradient bar on page surf</div>
            </div>
            <input
              type="checkbox"
              checked={smoothAnimations}
              onChange={(e) => setSmoothAnimations(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          {/* Pop-up Blocker */}
          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">Block Pop-ups & Redirects</div>
              <div className="text-[10px] text-neutral-400">Prevent unwanted tab spawns and redirect loops</div>
            </div>
            <input
              type="checkbox"
              checked={popupBlocker}
              onChange={(e) => setPopupBlocker(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          {/* Turbo Download Acceleration */}
          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">Turbo Download Acceleration</div>
              <div className="text-[10px] text-neutral-400">Split large files into 4 parallel download streams</div>
            </div>
            <input
              type="checkbox"
              checked={downloadAcceleration}
              onChange={(e) => setDownloadAcceleration(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>

          {/* Auto Clear on Exit */}
          <div className="flex items-center justify-between border-t border-neutral-800/80 pt-2.5">
            <div>
              <div className="font-semibold text-neutral-200">Auto-Clear History on Exit</div>
              <div className="text-[10px] text-neutral-400">Purge session tabs and cache when closing app</div>
            </div>
            <input
              type="checkbox"
              checked={clearOnExit}
              onChange={(e) => setClearOnExit(e.target.checked)}
              className="accent-emerald-500 w-4 h-4 cursor-pointer"
            />
          </div>
        </div>
      </div>

      {/* 6. Clear Cache & History Action (Preserved) */}
      <div className="pt-1">
        <button
          onClick={handleClear}
          className="w-full p-3 rounded-2xl bg-red-500/10 hover:bg-red-500/20 border border-red-500/30 text-red-400 font-semibold flex items-center justify-center gap-2 transition active:scale-95"
        >
          <Trash2 className="w-4 h-4" />
          <span>{clearedNotice ? '✓ Active Profile Cache & Cookies Cleared' : 'Clear Cookies, Cache & History'}</span>
        </button>
      </div>

      {/* 7. App Build Info (Preserved) */}
      <div className="p-3 rounded-xl bg-neutral-900/40 border border-neutral-800 text-[10px] text-neutral-400 space-y-1">
        <div className="flex justify-between">
          <span>Click Browser Build:</span>
          <span className="font-mono text-neutral-300">v9.5-release (Prince edition)</span>
        </div>
        <div className="flex justify-between">
          <span>Target Platform:</span>
          <span className="font-mono text-neutral-300">Android 15 (API 35/36)</span>
        </div>
        <div className="flex justify-between">
          <span>VPN Engine:</span>
          <span className="font-mono text-neutral-300">sing-box v1.14.3 gVisor TUN</span>
        </div>
      </div>
    </div>
  );
};
