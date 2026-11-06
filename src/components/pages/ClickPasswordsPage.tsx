import React, { useState } from 'react';
import { Key, Lock, Eye, EyeOff, Plus, Shield, Search, Copy, Check } from 'lucide-react';
import { SavedPassword } from '../../types/browser';

export const ClickPasswordsPage: React.FC = () => {
  const [unlocked, setUnlocked] = useState(false);
  const [pin, setPin] = useState('');
  const [pinError, setPinError] = useState(false);
  const [visiblePwId, setVisiblePwId] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const [passwords, setPasswords] = useState<SavedPassword[]>([
    { id: '1', site: 'GitHub', domain: 'github.com', username: 'laghariprince2@gmail.com', passwordMasked: 'ghp_xK8912b39Vlk', lastUsed: 'Yesterday' },
    { id: '2', site: 'Google Account', domain: 'accounts.google.com', username: 'laghariprince2@gmail.com', passwordMasked: 'G00gl3#P@ss2026', lastUsed: '3 days ago' },
    { id: '3', site: 'Hacker News', domain: 'news.ycombinator.com', username: 'prince_dev', passwordMasked: 'Hn_sec!9942', lastUsed: '5 days ago' },
    { id: '4', site: 'DuckDuckGo Cloud', domain: 'duckduckgo.com', username: 'prince_shield', passwordMasked: 'Dck!Privacy77', lastUsed: '1 week ago' },
  ]);

  const handleUnlock = (e: React.FormEvent) => {
    e.preventDefault();
    if (pin === '1234' || pin === '0000' || pin.length >= 4) {
      setUnlocked(true);
      setPinError(false);
    } else {
      setPinError(true);
    }
  };

  const copyPassword = (id: string, pwd: string) => {
    navigator.clipboard.writeText(pwd);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  const filtered = passwords.filter(
    (p) => p.site.toLowerCase().includes(search.toLowerCase()) || p.username.toLowerCase().includes(search.toLowerCase())
  );

  if (!unlocked) {
    return (
      <div className="h-full flex flex-col items-center justify-center p-6 bg-neutral-950 text-neutral-100 text-center">
        <div className="w-14 h-14 rounded-full bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 mb-3">
          <Lock className="w-7 h-7" />
        </div>
        <h3 className="text-base font-bold">Encrypted Password Vault</h3>
        <p className="text-xs text-neutral-400 mt-1 max-w-xs">
          Encrypted with Android MasterKey. Enter your 4-digit device PIN (Default: 1234)
        </p>

        <form onSubmit={handleUnlock} className="mt-5 w-full max-w-[200px] space-y-3">
          <input
            type="password"
            maxLength={6}
            value={pin}
            onChange={(e) => {
              setPin(e.target.value);
              setPinError(false);
            }}
            placeholder="PIN (1234)"
            className="w-full text-center tracking-widest text-lg py-2.5 rounded-xl bg-neutral-900 border border-neutral-700 focus:border-cyan-400 focus:outline-none"
            autoFocus
          />
          {pinError && <p className="text-[11px] text-red-400">Incorrect PIN. Try 1234</p>}
          <button
            type="submit"
            className="w-full py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-semibold text-xs transition active:scale-95 shadow-md"
          >
            Unlock Vault
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto p-4 space-y-4 bg-neutral-950 text-neutral-100 text-xs">
      <div className="flex items-center justify-between border-b border-neutral-800 pb-3">
        <div>
          <h2 className="text-base font-bold flex items-center gap-2">
            <Key className="w-4 h-4 text-cyan-400" /> Password Manager
          </h2>
          <p className="text-[11px] text-neutral-400">EncryptedSharedPreferences Vault</p>
        </div>
        <button
          onClick={() => setUnlocked(false)}
          className="px-2.5 py-1 rounded-lg bg-neutral-900 border border-neutral-800 text-neutral-400 hover:text-neutral-200 text-[11px]"
        >
          Lock
        </button>
      </div>

      {/* Search */}
      <div className="relative">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search saved logins..."
          className="w-full pl-8 pr-3 py-2 rounded-xl bg-neutral-900 border border-neutral-800 focus:border-neutral-700 text-xs focus:outline-none placeholder:text-neutral-500"
        />
        <Search className="w-3.5 h-3.5 text-neutral-500 absolute left-2.5 top-1/2 -translate-y-1/2" />
      </div>

      {/* Passwords list */}
      <div className="space-y-2">
        {filtered.map((item) => {
          const isVisible = visiblePwId === item.id;
          return (
            <div key={item.id} className="p-3 rounded-2xl bg-neutral-900/70 border border-neutral-800/80 space-y-2">
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-semibold text-neutral-200">{item.site}</div>
                  <div className="text-[10px] text-neutral-400 font-mono">{item.domain}</div>
                </div>
                <span className="text-[9px] text-neutral-500 font-mono">Used {item.lastUsed}</span>
              </div>

              <div className="p-2 rounded-xl bg-neutral-950 border border-neutral-900 flex items-center justify-between text-[11px]">
                <div className="truncate pr-2">
                  <div className="text-neutral-400 text-[10px]">{item.username}</div>
                  <div className="font-mono text-neutral-200 mt-0.5">
                    {isVisible ? item.passwordMasked : '••••••••••••'}
                  </div>
                </div>

                <div className="flex items-center gap-1.5 flex-shrink-0">
                  <button
                    onClick={() => setVisiblePwId(isVisible ? null : item.id)}
                    className="p-1 rounded text-neutral-400 hover:text-neutral-200"
                  >
                    {isVisible ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                  </button>
                  <button
                    onClick={() => copyPassword(item.id, item.passwordMasked)}
                    className="p-1 rounded text-neutral-400 hover:text-cyan-400"
                  >
                    {copiedId === item.id ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
