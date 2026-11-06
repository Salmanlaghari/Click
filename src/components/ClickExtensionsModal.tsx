import React, { useState } from 'react';
import { 
  Puzzle, 
  ShieldCheck, 
  Moon, 
  Languages, 
  Code, 
  EyeOff, 
  Lock, 
  X, 
  Check, 
  Info, 
  ExternalLink,
  Sparkles,
  ToggleLeft,
  ToggleRight,
  Settings
} from 'lucide-react';
import { BrowserExtension } from '../types/browser';

interface ClickExtensionsModalProps {
  isOpen: boolean;
  onClose: () => void;
  extensions: BrowserExtension[];
  onToggleExtension: (id: string) => void;
  currentUrl?: string;
}

export const ClickExtensionsModal: React.FC<ClickExtensionsModalProps> = ({
  isOpen,
  onClose,
  extensions,
  onToggleExtension,
  currentUrl = '',
}) => {
  const [filterCategory, setFilterCategory] = useState<string>('all');

  if (!isOpen) return null;

  const getIcon = (id: string) => {
    switch (id) {
      case 'ext-adblock':
        return ShieldCheck;
      case 'ext-darkreader':
        return Moon;
      case 'ext-translate':
        return Languages;
      case 'ext-tampermonkey':
        return Code;
      case 'ext-privacybadger':
        return EyeOff;
      case 'ext-https':
        return Lock;
      default:
        return Puzzle;
    }
  };

  const filtered = filterCategory === 'all' 
    ? extensions 
    : extensions.filter((e) => e.category === filterCategory);

  const activeCount = extensions.filter((e) => e.enabled).length;

  return (
    <div className="absolute inset-0 z-50 bg-black/75 backdrop-blur-xs flex flex-col justify-end animate-in fade-in select-none">
      <div className="w-full bg-neutral-900 border-t border-neutral-800 rounded-t-3xl p-4 max-h-[88%] flex flex-col space-y-3 animate-in slide-in-from-bottom duration-200">
        {/* Header */}
        <div className="flex items-center justify-between pb-2 border-b border-neutral-800">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-amber-500/20 text-amber-300 flex items-center justify-center border border-amber-500/30">
              <Puzzle className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-neutral-100 flex items-center gap-1.5">
                Extensions Hub
                <span className="px-1.5 py-0.2 rounded-full bg-emerald-500/20 text-emerald-300 text-[10px] font-semibold border border-emerald-500/30">
                  {activeCount} active
                </span>
              </h3>
              <p className="text-[10px] text-neutral-400">Play Store Safe Policy Compliant</p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="p-1.5 rounded-lg text-neutral-400 hover:text-white hover:bg-neutral-800 transition"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Play Store Compliance Banner */}
        <div className="p-2.5 rounded-2xl bg-neutral-950/80 border border-neutral-800 flex items-start gap-2.5 text-[11px] text-neutral-400">
          <ShieldCheck className="w-4 h-4 text-emerald-400 flex-shrink-0 mt-0.5" />
          <div className="leading-tight">
            <span className="font-semibold text-neutral-200">Google Play Safe Policy: </span>
            <span>All extensions run as declarative content scripts inside the Android WebView sandbox. No remote binary execution.</span>
          </div>
        </div>

        {/* Category Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
          {['all', 'security', 'appearance', 'productivity', 'developer'].map((cat) => (
            <button
              key={cat}
              onClick={() => setFilterCategory(cat)}
              className={`px-3 py-1 rounded-full text-[11px] font-medium capitalize whitespace-nowrap transition active:scale-95 border ${
                filterCategory === cat
                  ? 'bg-amber-500/20 text-amber-300 border-amber-500/50'
                  : 'bg-neutral-850 text-neutral-400 border-neutral-800 hover:text-neutral-200'
              }`}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Extensions List */}
        <div className="overflow-y-auto space-y-2 max-h-[340px] pr-0.5">
          {filtered.map((ext) => {
            const Icon = getIcon(ext.id);
            return (
              <div
                key={ext.id}
                className={`p-3 rounded-2xl border transition ${
                  ext.enabled
                    ? 'bg-neutral-850/90 border-amber-500/30 shadow-xs'
                    : 'bg-neutral-950/50 border-neutral-850 opacity-70'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-start gap-2.5">
                    <div className={`w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 border ${
                      ext.enabled 
                        ? 'bg-amber-500/15 text-amber-300 border-amber-500/30' 
                        : 'bg-neutral-800 text-neutral-400 border-neutral-700'
                    }`}>
                      <Icon className="w-4 h-4" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-neutral-200">{ext.name}</span>
                        <span className="text-[9px] text-neutral-400 font-mono">v{ext.version}</span>
                      </div>
                      <p className="text-[11px] text-neutral-400 mt-0.5 leading-snug">
                        {ext.description}
                      </p>
                      {ext.stats && ext.enabled && (
                        <div className="mt-1 inline-flex items-center gap-1 text-[10px] text-emerald-400 font-medium">
                          <Check className="w-3 h-3" />
                          <span>{ext.stats}</span>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Toggle Button */}
                  <button
                    onClick={() => onToggleExtension(ext.id)}
                    className="p-1 text-neutral-300 hover:text-white transition active:scale-90"
                    title={ext.enabled ? 'Disable extension' : 'Enable extension'}
                  >
                    {ext.enabled ? (
                      <ToggleRight className="w-7 h-7 text-amber-400" />
                    ) : (
                      <ToggleLeft className="w-7 h-7 text-neutral-600" />
                    )}
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer info */}
        <div className="pt-1 flex items-center justify-between text-[10px] text-neutral-400 border-t border-neutral-800/80">
          <span>Targeting: <code className="text-neutral-300 font-mono">{currentUrl || 'Current Tab'}</code></span>
          <button 
            onClick={onClose}
            className="px-3 py-1 rounded-xl bg-amber-500 text-black font-bold hover:bg-amber-400 transition"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
