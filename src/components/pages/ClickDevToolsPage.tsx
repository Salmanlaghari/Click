import React, { useState } from 'react';
import { 
  Terminal, 
  Code, 
  Network, 
  Database, 
  Trash2, 
  Play, 
  Search, 
  AlertCircle, 
  Info, 
  AlertTriangle,
  ChevronRight,
  ChevronDown
} from 'lucide-react';
import { ConsoleLog, NetworkRequest } from '../../types/browser';

interface ClickDevToolsPageProps {
  currentUrl: string;
}

export const ClickDevToolsPage: React.FC<ClickDevToolsPageProps> = ({ currentUrl }) => {
  const [activeTab, setActiveTab] = useState<'console' | 'elements' | 'network' | 'storage'>('console');
  const [consoleInput, setConsoleInput] = useState('');
  const [logFilter, setLogFilter] = useState<'all' | 'error' | 'warn' | 'info'>('all');

  const [logs, setLogs] = useState<ConsoleLog[]>([
    { id: '1', type: 'info', message: '[Click DevTools v1.3.9] Bridge initialized on WebView data directory.', source: 'ClickEngine.kt:142', time: '10:04:12' },
    { id: '2', type: 'log', message: `Navigating session context to: ${currentUrl}`, source: 'BrowserUrlInput.kt:88', time: '10:04:13' },
    { id: '3', type: 'info', message: 'Content Security Policy (CSP): Strict-Dynamic enabled. Inline scripts sandboxed.', source: 'security.js:29', time: '10:04:14' },
    { id: '4', type: 'warn', message: 'Tracking cookie "_ga_session" blocked by Click AdBlock Engine.', source: 'adblock_filter.ts:401', time: '10:04:15' },
    { id: '5', type: 'log', message: 'WebView RenderProcessReady: hardware accelerated canvas enabled.', source: 'AndroidWebView:101', time: '10:04:16' },
  ]);

  const networkRequests: NetworkRequest[] = [
    { id: 'r1', url: currentUrl || 'https://clickbrowser.org', method: 'GET', status: 200, type: 'document', size: '24.8 KB', timeMs: 42, timestamp: '10:04:13' },
    { id: 'r2', url: '/static/js/bundle.esm.js', method: 'GET', status: 200, type: 'script', size: '142.3 KB', timeMs: 88, timestamp: '10:04:13' },
    { id: 'r3', url: '/static/css/theme-dark.css', method: 'GET', status: 200, type: 'stylesheet', size: '18.4 KB', timeMs: 25, timestamp: '10:04:13' },
    { id: 'r4', url: '/api/v1/telemetry/beacon', method: 'POST', status: 0, type: 'xhr', size: '0 B (Blocked)', timeMs: 4, timestamp: '10:04:14' },
    { id: 'r5', url: '/assets/fonts/inter-latin.woff2', method: 'GET', status: 304, type: 'font', size: '48.1 KB', timeMs: 15, timestamp: '10:04:14' },
    { id: 'r6', url: '/api/feed?page=1&limit=20', method: 'GET', status: 200, type: 'xhr', size: '6.2 KB', timeMs: 64, timestamp: '10:04:15' },
  ];

  const handleConsoleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!consoleInput.trim()) return;
    const input = consoleInput.trim();
    
    let resultMessage = '';
    let resultType: 'log' | 'info' | 'warn' | 'error' = 'log';

    try {
      if (input === 'navigator.userAgent') {
        resultMessage = '"Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 ClickBrowser/1.3.9"';
      } else if (input === 'document.title') {
        resultMessage = `"${document.title}"`;
      } else if (input === 'location.href') {
        resultMessage = `"${currentUrl}"`;
      } else if (input.startsWith('window.') || input.startsWith('console.')) {
        resultMessage = 'ƒ () { [native code] }';
      } else {
        // Safe arithmetic or evaluation simulation
        const evalVal = Function(`"use strict"; return (${input});`)();
        resultMessage = typeof evalVal === 'object' ? JSON.stringify(evalVal) : String(evalVal);
      }
    } catch (err: any) {
      resultMessage = `Uncaught ${err.name || 'Error'}: ${err.message || 'SyntaxError'}`;
      resultType = 'error';
    }

    setLogs((prev) => [
      ...prev,
      {
        id: String(Date.now()),
        type: 'log',
        message: `> ${input}`,
        source: 'ConsoleIn:1',
        time: new Date().toLocaleTimeString().split(' ')[0],
      },
      {
        id: String(Date.now() + 1),
        type: resultType,
        message: `< ${resultMessage}`,
        source: 'ConsoleOut:1',
        time: new Date().toLocaleTimeString().split(' ')[0],
      },
    ]);
    setConsoleInput('');
  };

  const filteredLogs = logs.filter((log) => {
    if (logFilter === 'all') return true;
    return log.type === logFilter;
  });

  return (
    <div className="h-full flex flex-col bg-neutral-950 text-neutral-100 font-mono text-xs">
      {/* DevTools Top Bar */}
      <div className="flex items-center justify-between px-3 py-2 bg-neutral-900 border-b border-neutral-800 flex-shrink-0">
        <div className="flex items-center gap-1 overflow-x-auto no-scrollbar">
          <button
            onClick={() => setActiveTab('console')}
            className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition flex items-center gap-1.5 ${
              activeTab === 'console'
                ? 'bg-neutral-800 text-emerald-400 border border-neutral-700'
                : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <Terminal className="w-3.5 h-3.5" /> Console
            <span className="text-[9px] px-1 bg-neutral-800 rounded-full text-neutral-400 font-mono">
              {logs.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('elements')}
            className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition flex items-center gap-1.5 ${
              activeTab === 'elements'
                ? 'bg-neutral-800 text-emerald-400 border border-neutral-700'
                : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <Code className="w-3.5 h-3.5" /> Elements
          </button>

          <button
            onClick={() => setActiveTab('network')}
            className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition flex items-center gap-1.5 ${
              activeTab === 'network'
                ? 'bg-neutral-800 text-emerald-400 border border-neutral-700'
                : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <Network className="w-3.5 h-3.5" /> Network
            <span className="text-[9px] px-1 bg-neutral-800 rounded-full text-neutral-400 font-mono">
              {networkRequests.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('storage')}
            className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition flex items-center gap-1.5 ${
              activeTab === 'storage'
                ? 'bg-neutral-800 text-emerald-400 border border-neutral-700'
                : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <Database className="w-3.5 h-3.5" /> Storage
          </button>
        </div>

        {activeTab === 'console' && (
          <button
            onClick={() => setLogs([])}
            className="p-1 text-neutral-400 hover:text-red-400 transition"
            title="Clear Console"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* Tab Content */}
      <div className="flex-1 overflow-y-auto p-3">
        {activeTab === 'console' && (
          <div className="space-y-2">
            {/* Filter pills */}
            <div className="flex items-center gap-1 pb-1 border-b border-neutral-900 text-[10px]">
              {(['all', 'info', 'warn', 'error'] as const).map((filter) => (
                <button
                  key={filter}
                  onClick={() => setLogFilter(filter)}
                  className={`px-2 py-0.5 rounded capitalize transition ${
                    logFilter === filter
                      ? 'bg-neutral-800 text-neutral-100 font-bold'
                      : 'text-neutral-500 hover:text-neutral-300'
                  }`}
                >
                  {filter}
                </button>
              ))}
            </div>

            {/* Logs List */}
            <div className="space-y-1.5">
              {filteredLogs.map((log) => {
                let badge = <Info className="w-3 h-3 text-blue-400 flex-shrink-0 mt-0.5" />;
                let textClass = 'text-neutral-300';
                if (log.type === 'warn') {
                  badge = <AlertTriangle className="w-3 h-3 text-amber-400 flex-shrink-0 mt-0.5" />;
                  textClass = 'text-amber-300';
                } else if (log.type === 'error') {
                  badge = <AlertCircle className="w-3 h-3 text-red-400 flex-shrink-0 mt-0.5" />;
                  textClass = 'text-red-400';
                }
                return (
                  <div
                    key={log.id}
                    className="flex items-start gap-2 p-1.5 rounded bg-neutral-900/50 hover:bg-neutral-900 border border-neutral-800/60 leading-tight"
                  >
                    {badge}
                    <div className="flex-1 overflow-hidden">
                      <div className={`break-words ${textClass}`}>{log.message}</div>
                      <div className="text-[9px] text-neutral-500 mt-0.5 flex items-center justify-between">
                        <span>{log.source}</span>
                        <span>{log.time}</span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {activeTab === 'elements' && (
          <div className="space-y-2 text-[11px] leading-relaxed text-neutral-300">
            <div className="text-neutral-500">&lt;!DOCTYPE html&gt;</div>
            <div className="pl-2">
              <span className="text-purple-400">&lt;html</span> <span className="text-amber-400">lang</span>=<span className="text-emerald-400">"en"</span><span className="text-purple-400">&gt;</span>
              <div className="pl-4">
                <span className="text-purple-400">&lt;head&gt;</span>
                <div className="pl-4 text-neutral-400">
                  <div>&lt;meta charset="UTF-8" /&gt;</div>
                  <div>&lt;title&gt;{currentUrl || 'Click Browser'}&lt;/title&gt;</div>
                  <div>&lt;meta name="viewport" content="width=device-width, initial-scale=1.0" /&gt;</div>
                </div>
                <span className="text-purple-400">&lt;/head&gt;</span>
              </div>
              <div className="pl-4">
                <span className="text-purple-400">&lt;body</span> <span className="text-amber-400">class</span>=<span className="text-emerald-400">"click-browser-webview dark"</span><span className="text-purple-400">&gt;</span>
                <div className="pl-4">
                  <div className="p-2 rounded bg-neutral-900 border border-neutral-800 my-1">
                    <span className="text-blue-400">&lt;div</span> <span className="text-amber-400">id</span>=<span className="text-emerald-400">"root"</span><span className="text-blue-400">&gt;</span>
                    <div className="pl-4 text-neutral-400 text-[10px]">
                      // WebView viewport active · Hardware layer
                    </div>
                    <span className="text-blue-400">&lt;/div&gt;</span>
                  </div>
                </div>
                <span className="text-purple-400">&lt;/body&gt;</span>
              </div>
              <span className="text-purple-400">&lt;/html&gt;</span>
            </div>
          </div>
        )}

        {activeTab === 'network' && (
          <div className="space-y-1">
            <div className="grid grid-cols-12 text-[10px] text-neutral-500 font-semibold px-2 py-1 border-b border-neutral-800">
              <div className="col-span-5">Name</div>
              <div className="col-span-2">Status</div>
              <div className="col-span-2">Type</div>
              <div className="col-span-3 text-right">Time</div>
            </div>
            {networkRequests.map((req) => (
              <div
                key={req.id}
                className="grid grid-cols-12 text-[10px] px-2 py-1.5 rounded hover:bg-neutral-900 border-b border-neutral-900/60 items-center font-mono"
              >
                <div className="col-span-5 truncate text-neutral-200" title={req.url}>
                  <span className="text-neutral-500 mr-1">{req.method}</span>
                  {req.url.replace(/^https?:\/\/[^/]+/, '') || '/'}
                </div>
                <div className="col-span-2">
                  <span className={`px-1.5 py-0.5 rounded text-[9px] ${
                    req.status === 200 ? 'bg-emerald-500/10 text-emerald-400' :
                    req.status === 304 ? 'bg-blue-500/10 text-blue-400' :
                    'bg-red-500/10 text-red-400'
                  }`}>
                    {req.status === 0 ? 'Blocked' : req.status}
                  </span>
                </div>
                <div className="col-span-2 text-neutral-400">{req.type}</div>
                <div className="col-span-3 text-right text-neutral-400 font-mono">{req.timeMs}ms</div>
              </div>
            ))}
          </div>
        )}

        {activeTab === 'storage' && (
          <div className="space-y-3">
            <div className="p-2.5 rounded-xl bg-neutral-900 border border-neutral-800">
              <div className="text-[11px] font-bold text-neutral-300 mb-1 flex items-center justify-between">
                <span>Profile Partition Storage</span>
                <span className="text-[9px] text-emerald-400 font-mono">Isolated</span>
              </div>
              <p className="text-[10px] text-neutral-400">
                Cookies & cache are isolated within the active profile's WebView data directory.
              </p>
            </div>

            <div className="space-y-1">
              <div className="text-[10px] font-bold text-neutral-400 uppercase tracking-wider">Cookies (Active Profile)</div>
              <div className="p-2 rounded bg-neutral-900 border border-neutral-800 text-[10px] space-y-1">
                <div className="flex justify-between text-neutral-300">
                  <span className="font-semibold text-emerald-400">session_id</span>
                  <span className="font-mono text-neutral-500">Secure; HttpOnly; SameSite=Lax</span>
                </div>
                <div className="flex justify-between text-neutral-300">
                  <span className="font-semibold text-emerald-400">click_theme</span>
                  <span className="font-mono text-neutral-500">"dark-mode"</span>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Console Input Footer */}
      {activeTab === 'console' && (
        <form onSubmit={handleConsoleSubmit} className="p-2 bg-neutral-900 border-t border-neutral-800 flex items-center gap-1.5 flex-shrink-0">
          <span className="text-emerald-400 font-bold">&gt;</span>
          <input
            type="text"
            value={consoleInput}
            onChange={(e) => setConsoleInput(e.target.value)}
            placeholder="Type JS expression (e.g. navigator.userAgent, 2+2)..."
            className="flex-1 bg-transparent border-0 focus:outline-none text-[11px] text-neutral-100 placeholder:text-neutral-600 font-mono"
          />
          <button
            type="submit"
            className="p-1 rounded bg-neutral-800 hover:bg-neutral-700 text-neutral-300 transition"
          >
            <Play className="w-3 h-3" />
          </button>
        </form>
      )}
    </div>
  );
};
