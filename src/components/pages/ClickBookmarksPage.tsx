import React, { useState } from 'react';
import { Bookmark, Clock, Trash2, ExternalLink, Search } from 'lucide-react';
import { Bookmark as BookmarkType, HistoryItem } from '../../types/browser';

interface ClickBookmarksPageProps {
  initialTab?: 'bookmarks' | 'history';
  bookmarks: BookmarkType[];
  history: HistoryItem[];
  onNavigate: (url: string) => void;
  onClearHistory: () => void;
}

export const ClickBookmarksPage: React.FC<ClickBookmarksPageProps> = ({
  initialTab = 'bookmarks',
  bookmarks,
  history,
  onNavigate,
  onClearHistory,
}) => {
  const [activeTab, setActiveTab] = useState<'bookmarks' | 'history'>(initialTab);
  const [search, setSearch] = useState('');

  const filteredBookmarks = bookmarks.filter((b) =>
    b.title.toLowerCase().includes(search.toLowerCase()) || b.url.toLowerCase().includes(search.toLowerCase())
  );

  const filteredHistory = history.filter((h) =>
    h.title.toLowerCase().includes(search.toLowerCase()) || h.url.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="h-full flex flex-col bg-neutral-950 text-neutral-100 text-xs">
      {/* Top switch bar */}
      <div className="p-3 bg-neutral-900 border-b border-neutral-800 flex items-center justify-between flex-shrink-0">
        <div className="flex bg-neutral-950 p-0.5 rounded-xl border border-neutral-800">
          <button
            onClick={() => setActiveTab('bookmarks')}
            className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
              activeTab === 'bookmarks' ? 'bg-neutral-800 text-white shadow-xs' : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            Bookmarks
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
              activeTab === 'history' ? 'bg-neutral-800 text-white shadow-xs' : 'text-neutral-400 hover:text-neutral-200'
            }`}
          >
            History
          </button>
        </div>

        {activeTab === 'history' && history.length > 0 && (
          <button
            onClick={onClearHistory}
            className="p-1.5 text-neutral-400 hover:text-red-400 transition"
            title="Clear History"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* Search Input */}
      <div className="p-3 pb-0 flex-shrink-0">
        <div className="relative">
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={activeTab === 'bookmarks' ? "Search bookmarks..." : "Search browsing history..."}
            className="w-full pl-8 pr-3 py-2 rounded-xl bg-neutral-900 border border-neutral-800 focus:border-neutral-700 text-xs focus:outline-none placeholder:text-neutral-500"
          />
          <Search className="w-3.5 h-3.5 text-neutral-500 absolute left-2.5 top-1/2 -translate-y-1/2" />
        </div>
      </div>

      {/* Content list */}
      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {activeTab === 'bookmarks' ? (
          <div className="space-y-1.5">
            {filteredBookmarks.map((bm) => (
              <button
                key={bm.id}
                onClick={() => onNavigate(bm.url)}
                className="w-full p-2.5 rounded-xl bg-neutral-900/60 hover:bg-neutral-800/80 border border-neutral-800 text-left transition flex items-center justify-between group active:scale-[0.99]"
              >
                <div className="truncate pr-2">
                  <div className="font-semibold text-neutral-200 truncate">{bm.title}</div>
                  <div className="text-[10px] text-neutral-400 font-mono truncate">{bm.url}</div>
                </div>
                <ExternalLink className="w-3.5 h-3.5 text-neutral-500 group-hover:text-blue-400 flex-shrink-0" />
              </button>
            ))}
          </div>
        ) : (
          <div className="space-y-1.5">
            {filteredHistory.map((item) => (
              <button
                key={item.id}
                onClick={() => onNavigate(item.url)}
                className="w-full p-2.5 rounded-xl bg-neutral-900/60 hover:bg-neutral-800/80 border border-neutral-800 text-left transition flex items-center justify-between group active:scale-[0.99]"
              >
                <div className="truncate pr-2">
                  <div className="font-semibold text-neutral-200 truncate">{item.title}</div>
                  <div className="text-[10px] text-neutral-400 font-mono truncate">{item.url}</div>
                </div>
                <div className="flex items-center gap-1.5 flex-shrink-0">
                  <span className="text-[9px] font-mono text-neutral-500">
                    {new Date(item.visitedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                  <ExternalLink className="w-3.5 h-3.5 text-neutral-500 group-hover:text-emerald-400" />
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
