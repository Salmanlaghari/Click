import React, { useState } from 'react';
import { 
  Puzzle, 
  ShieldCheck, 
  Moon, 
  Languages, 
  EyeOff, 
  Lock, 
  X, 
  Check, 
  ToggleLeft, 
  ToggleRight, 
  Settings, 
  Play
} from 'lucide-react';
import { BrowserExtension } from '../types/browser';
import { EXTENSION_TABS, isApplyExtensionActionAvailable } from './extensionUiPolicy.js';

interface ClickExtensionsModalProps {
  isOpen: boolean;
  onClose: () => void;
  extensions: BrowserExtension[];
  onToggleExtension: (id: string) => void;
  currentUrl?: string;
  onApplyExtensionAction?: (extId: string, actionPayload?: Record<string, unknown>) => void;
}

export const ClickExtensionsModal: React.FC<ClickExtensionsModalProps> = ({
  isOpen,
  onClose,
  extensions,
  onToggleExtension,
  currentUrl = '',
  onApplyExtensionAction,
}) => {
  const [activeTab, setActiveTab] = useState<(typeof EXTENSION_TABS)[number]>(EXTENSION_TABS[0]);
  const [filterCategory, setFilterCategory] = useState<string>('all');
  const [expandedExtId, setExpandedExtId] = useState<string | null>(null);
  const extensionActionAvailable = isApplyExtensionActionAvailable(onApplyExtensionAction);

  // Dark Reader settings
  const [darkIntensity, setDarkIntensity] = useState<number>(90);
  const [contrastBoost, setContrastBoost] = useState<number>(100);

  // Translator settings
  const [targetLang, setTargetLang] = useState<string>('English');
  const [autoTranslateForeign, setAutoTranslateForeign] = useState<boolean>(true);

  // AdBlock Shield settings
  const [aggressiveMode, setAggressiveMode] = useState<boolean>(true);
  const [whitelisted, setWhitelisted] = useState<boolean>(false);

  // Detected Trackers (Privacy Badger Radar)
  const [detectedTrackers, setDetectedTrackers] = useState([
    { domain: 'google-analytics.com', type: 'Telemetry & Analytics', blocked: true, hits: 14 },
    { domain: 'connect.facebook.net', type: 'Social Beacon', blocked: true, hits: 6 },
    { domain: 'doubleclick.net', type: 'Behavioral Ad Network', blocked: true, hits: 28 },
    { domain: 'criteo.com', type: 'Retargeting Pixel', blocked: true, hits: 9 },
    { domain: 'hotjar.com', type: 'Heatmap Session Recorder', blocked: true, hits: 3 },
  ]);

  if (!isOpen) return null;

  const getIcon = (id: string) => {
    switch (id) {
      case 'ext-adblock':
        return ShieldCheck;
      case 'ext-darkreader':
        return Moon;
      case 'ext-translate':
        return Languages;
      case 'ext-privacybadger':
        return EyeOff;
      case 'ext-https':
        return Lock;
      default:
        return Puzzle;
    }
  };

  const handleToggleTracker = (domain: string) => {
    setDetectedTrackers((prev) =>
      prev.map((t) => (t.domain === domain ? { ...t, blocked: !t.blocked } : t))
    );
  };

  const filteredExtensions = filterCategory === 'all' 
    ? extensions 
    : extensions.filter((e) => e.category === filterCategory);

  return (
    <div className="absolute inset-0 z-50 bg-black/80 backdrop-blur-xs flex flex-col justify-end animate-in fade-in select-none">
      <div className="w-full bg-neutral-900 border-t border-neutral-800 rounded-t-3xl p-4 max-h-[90%] flex flex-col space-y-3 animate-in slide-in-from-bottom duration-200">
        {/* Header */}
        <div className="flex items-center justify-between pb-2 border-b border-neutral-800">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-amber-500/20 text-amber-300 flex items-center justify-center border border-amber-500/30">
              <Puzzle className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-neutral-100 flex items-center gap-1.5">
                Extensions preview
                <span className="px-1.5 py-0.2 rounded-full bg-neutral-800 text-neutral-300 text-[10px] font-semibold border border-neutral-700">
                  Preview only
                </span>
              </h3>
              <p className="text-[10px] text-neutral-400">No browser extension runtime is connected in this preview.</p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="p-1.5 rounded-lg text-neutral-400 hover:text-white hover:bg-neutral-800 transition"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Only preview panels are offered; user-supplied script injection is not supported. */}
        <div className="grid grid-cols-2 gap-1 p-1 bg-neutral-950 rounded-xl border border-neutral-800 text-[11px] font-semibold">
          {EXTENSION_TABS.map((tab) => {
            const label = tab === 'extensions'
              ? `Example extensions (${extensions.length})`
              : `Radar (${detectedTrackers.length} sample)`;
            return (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`py-1.5 rounded-lg transition ${
                  activeTab === tab
                    ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40 shadow-xs'
                    : 'text-neutral-400 hover:text-neutral-200'
                }`}
              >
                {label}
              </button>
            );
          })}
        </div>

        {/* 1. EXTENSIONS TAB CONTENT */}
        {activeTab === 'extensions' && (
          <div className="space-y-2.5 overflow-hidden flex flex-col flex-1">
            {/* This React preview is not connected to the Android extension runtime. */}
            <div className="p-2.5 rounded-xl bg-neutral-950/80 border border-neutral-800 flex items-start gap-2 text-[10px] text-neutral-400">
              <ShieldCheck className="w-3.5 h-3.5 text-neutral-400 flex-shrink-0 mt-0.5" />
              <div className="leading-tight">
                <span className="font-semibold text-neutral-200">Preview only: </span>
                <span>These example entries do not install add-ons, modify pages, block requests, or inject scripts.</span>
              </div>
            </div>

            {/* Category Filter Pills */}
            <div className="flex items-center gap-1 overflow-x-auto no-scrollbar py-0.5">
              {['all', 'security', 'appearance', 'productivity', 'developer'].map((cat) => (
                <button
                  key={cat}
                  onClick={() => setFilterCategory(cat)}
                  className={`px-2.5 py-0.5 rounded-full text-[10px] font-medium capitalize whitespace-nowrap transition border ${
                    filterCategory === cat
                      ? 'bg-amber-500/20 text-amber-300 border-amber-500/50 font-bold'
                      : 'bg-neutral-850 text-neutral-400 border-neutral-800 hover:text-neutral-200'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>

            {/* Extensions List */}
            <div className="overflow-y-auto space-y-2 max-h-[300px] pr-0.5">
              {filteredExtensions.map((ext) => {
                const Icon = getIcon(ext.id);
                const isExpanded = expandedExtId === ext.id;

                return (
                  <div
                    key={ext.id}
                    className={`rounded-2xl border transition ${
                      ext.enabled
                        ? 'bg-neutral-850/90 border-amber-500/30 shadow-xs'
                        : 'bg-neutral-950/50 border-neutral-850 opacity-70'
                    }`}
                  >
                    <div className="p-3 flex items-start justify-between gap-2">
                      <div className="flex items-start gap-2.5 flex-1 min-w-0">
                        <div className={`w-8 h-8 rounded-xl flex items-center justify-center flex-shrink-0 border ${
                          ext.enabled 
                            ? 'bg-amber-500/15 text-amber-300 border-amber-500/30' 
                            : 'bg-neutral-800 text-neutral-400 border-neutral-700'
                        }`}>
                          <Icon className="w-4 h-4" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span className="text-xs font-bold text-neutral-200 truncate">{ext.name}</span>
                            <span className="text-[9px] text-neutral-400 font-mono">v{ext.version}</span>
                          </div>
                          <p className="text-[10px] text-neutral-400 mt-0.5 leading-snug">
                            {ext.description}
                          </p>
                          {ext.stats && ext.enabled && (
                            <div className="mt-1 inline-flex items-center gap-1 text-[9px] text-neutral-400 font-medium">
                              <Check className="w-2.5 h-2.5 text-neutral-500" />
                              <span>Example only: {ext.stats}</span>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Right controls: Config Gear + Toggle */}
                      <div className="flex items-center gap-1 flex-shrink-0">
                        <button
                          onClick={() => setExpandedExtId(isExpanded ? null : ext.id)}
                          className="p-1 rounded-lg text-neutral-400 hover:text-white hover:bg-neutral-700 transition"
                          title="Example extension settings"
                        >
                          <Settings className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => onToggleExtension(ext.id)}
                          className="p-1 text-neutral-300 hover:text-white transition active:scale-90"
                          title="Toggle example state only"
                        >
                          {ext.enabled ? (
                            <ToggleRight className="w-6 h-6 text-amber-400" />
                          ) : (
                            <ToggleLeft className="w-6 h-6 text-neutral-600" />
                          )}
                        </button>
                      </div>
                    </div>

                    {/* Expandable Configuration Drawer for each extension */}
                    {isExpanded && (
                      <div className="px-3 pb-3 pt-1 border-t border-neutral-800/80 bg-neutral-900/60 rounded-b-2xl text-[11px] space-y-2">
                        {/* Dark Reader Controls */}
                        {ext.id === 'ext-darkreader' && (
                          <div className="space-y-1.5">
                            <div className="flex items-center justify-between text-neutral-300">
                              <span>Dark Invert Level:</span>
                              <span className="font-mono text-amber-300">{darkIntensity}%</span>
                            </div>
                            <input
                              type="range"
                              min="50"
                              max="100"
                              value={darkIntensity}
                              onChange={(e) => setDarkIntensity(Number(e.target.value))}
                              className="w-full accent-amber-500 h-1 bg-neutral-700 rounded-lg cursor-pointer"
                            />
                            <div className="flex items-center justify-between pt-1">
                              <span className="text-neutral-400">Smart Image Invert Guard:</span>
                              <span className="text-neutral-400 font-semibold">Example active</span>
                            </div>
                          </div>
                        )}

                        {/* Translator Controls */}
                        {ext.id === 'ext-translate' && (
                          <div className="space-y-1.5">
                            <div className="flex items-center justify-between">
                              <span className="text-neutral-300">Translate to:</span>
                              <select
                                value={targetLang}
                                onChange={(e) => setTargetLang(e.target.value)}
                                className="bg-neutral-800 border border-neutral-700 rounded-lg px-2 py-0.5 text-neutral-200 text-xs focus:outline-none"
                              >
                                {['English', 'Urdu', 'Hindi', 'Spanish', 'Arabic', 'German', 'French', 'Chinese', 'Japanese'].map((l) => (
                                  <option key={l} value={l}>{l}</option>
                                ))}
                              </select>
                            </div>
                            <div className="flex items-center justify-between">
                              <span className="text-neutral-400">Auto-detect foreign page:</span>
                              <input
                                type="checkbox"
                                checked={autoTranslateForeign}
                                onChange={(e) => setAutoTranslateForeign(e.target.checked)}
                                className="accent-amber-500 w-3.5 h-3.5"
                              />
                            </div>
                          </div>
                        )}

                        {/* AdBlock Controls */}
                        {ext.id === 'ext-adblock' && (
                          <div className="space-y-1.5">
                            <div className="flex items-center justify-between">
                              <span className="text-neutral-300">Aggressive Cosmetic Filter:</span>
                              <input
                                type="checkbox"
                                checked={aggressiveMode}
                                onChange={(e) => setAggressiveMode(e.target.checked)}
                                className="accent-amber-500 w-3.5 h-3.5"
                              />
                            </div>
                            <div className="flex items-center justify-between">
                              <span className="text-neutral-300">Shields for this site:</span>
                              <button
                                onClick={() => setWhitelisted(!whitelisted)}
                                className={`px-2 py-0.5 rounded text-[10px] font-semibold border ${
                                  whitelisted
                                    ? 'bg-red-500/20 text-red-300 border-red-500/40'
                                    : 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                                }`}
                              >
                                {whitelisted ? 'Example paused' : 'Example enabled'}
                              </button>
                            </div>
                          </div>
                        )}

                        {/* Privacy Badger Controls */}
                        {ext.id === 'ext-privacybadger' && (
                          <div className="space-y-1">
                            <div className="flex items-center justify-between text-neutral-300">
                              <span>Canvas Fingerprint Noise:</span>
                              <span className="text-neutral-400 font-semibold">Example enabled</span>
                            </div>
                            <div className="flex items-center justify-between text-neutral-300">
                              <span>AudioContext Salt:</span>
                              <span className="text-neutral-400 font-semibold">Example enabled</span>
                            </div>
                          </div>
                        )}

                        {/* HTTPS Everywhere Controls */}
                        {ext.id === 'ext-https' && (
                          <div className="space-y-1">
                            <div className="flex items-center justify-between text-neutral-300">
                              <span>TLS status:</span>
                              <span className="text-neutral-400 font-semibold">Example only · not enforced</span>
                            </div>
                            <div className="flex items-center justify-between text-neutral-300">
                              <span>HSTS status:</span>
                              <span className="text-neutral-400 font-semibold">Example only · not verified</span>
                            </div>
                          </div>
                        )}

                        {/* Direct action button */}
                        <div className="pt-1 flex items-center justify-between">
                          <span className="text-[10px] text-neutral-400 font-mono">
                            Example permissions: {ext.permissions.join(', ')}
                          </span>
                          <button
                            disabled={!extensionActionAvailable}
                            aria-disabled={!extensionActionAvailable}
                            title={extensionActionAvailable ? 'Apply to Tab' : 'Unavailable: no extension runtime is connected'}
                            onClick={() => {
                              if (!onApplyExtensionAction) return;
                              onApplyExtensionAction(ext.id, { darkIntensity, targetLang, aggressiveMode });
                              onClose();
                            }}
                            className={`px-2.5 py-1 rounded-lg border text-[10px] font-semibold flex items-center gap-1 transition ${
                              extensionActionAvailable
                                ? 'bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border-amber-500/40 active:scale-95'
                                : 'bg-neutral-800 text-neutral-500 border-neutral-700 cursor-not-allowed'
                            }`}
                          >
                            <Play className="w-2.5 h-2.5" />
                            <span>{extensionActionAvailable ? 'Apply to Tab' : 'Unavailable'}</span>
                          </button>
                        </div>
                        {!extensionActionAvailable && (
                          <p className="text-[9px] text-neutral-500">No extension runtime is connected; this preview does not modify the page.</p>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 3. TRACKER RADAR TAB */}
        {activeTab === 'trackers' && (
          <div className="space-y-2 overflow-hidden flex flex-col flex-1">
            <div className="p-2.5 rounded-xl bg-neutral-950 border border-neutral-800 text-[10px] text-neutral-400 flex items-center justify-between">
              <span>Host: <strong className="text-neutral-200">{currentUrl || 'Current Tab'}</strong></span>
              <span className="text-amber-300 font-mono font-bold">Sample data · no live blocking</span>
            </div>

            <div className="overflow-y-auto space-y-1.5 max-h-[300px] pr-0.5">
              {detectedTrackers.map((tracker) => (
                <div
                  key={tracker.domain}
                  className="p-2.5 rounded-xl bg-neutral-850 border border-neutral-800 flex items-center justify-between text-[11px]"
                >
                  <div>
                    <div className="font-bold text-neutral-200 font-mono text-xs">{tracker.domain}</div>
                    <div className="text-[10px] text-neutral-400">{tracker.type} · {tracker.hits} attempts</div>
                  </div>
                  <button
                    onClick={() => handleToggleTracker(tracker.domain)}
                    className={`px-2 py-0.5 rounded-lg text-[10px] font-bold border transition ${
                      tracker.blocked
                        ? 'bg-red-500/20 text-red-300 border-red-500/40'
                        : 'bg-neutral-800 text-neutral-400 border-neutral-700'
                    }`}
                  >
                    {tracker.blocked ? 'Example blocked' : 'Example allowed'}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Footer */}
        <div className="pt-2 flex items-center justify-between text-[10px] text-neutral-400 border-t border-neutral-800/80">
          <span>Preview target (unchanged): <code className="text-neutral-300 font-mono">{currentUrl || 'Active Tab'}</code></span>
          <button 
            onClick={onClose}
            className="px-4 py-1.5 rounded-xl bg-amber-500 text-black font-bold hover:bg-amber-400 transition active:scale-95 text-xs"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
