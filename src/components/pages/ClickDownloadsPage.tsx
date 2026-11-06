import React from 'react';
import { Download, FileText, CheckCircle2, Play, FileCode, Archive } from 'lucide-react';
import { DownloadItem } from '../../types/browser';

export const ClickDownloadsPage: React.FC = () => {
  const downloads: DownloadItem[] = [
    { id: '1', name: 'app-release-v1.3.9.apk', size: '28.4 MB', url: 'https://clickbrowser.org/download', progress: 100, status: 'completed', mimeType: 'application/vnd.android.package-archive', downloadedAt: Date.now() - 3600000 },
    { id: '2', name: 'features_specification.pdf', size: '1.2 MB', url: 'https://clickbrowser.org/docs/features.pdf', progress: 100, status: 'completed', mimeType: 'application/pdf', downloadedAt: Date.now() - 7200000 },
    { id: '3', name: 'singbox_core_config.json', size: '14.2 KB', url: 'https://sagernet.org/config.json', progress: 100, status: 'completed', mimeType: 'application/json', downloadedAt: Date.now() - 86400000 },
  ];

  return (
    <div className="h-full overflow-y-auto p-4 space-y-4 bg-neutral-950 text-neutral-100 text-xs">
      <div className="flex items-center justify-between border-b border-neutral-800 pb-3">
        <div>
          <h2 className="text-base font-bold flex items-center gap-2">
            <Download className="w-4 h-4 text-amber-400" /> Downloads Manager
          </h2>
          <p className="text-[11px] text-neutral-400">Standard Android User-Initiated Downloads</p>
        </div>
        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-neutral-900 border border-neutral-800 text-neutral-400">
          {downloads.length} files
        </span>
      </div>

      <div className="space-y-2">
        {downloads.map((item) => (
          <div key={item.id} className="p-3 rounded-2xl bg-neutral-900/70 border border-neutral-800/80 flex items-center justify-between">
            <div className="flex items-center gap-3 truncate">
              <div className="p-2.5 rounded-xl bg-amber-500/10 text-amber-400 border border-amber-500/20 flex-shrink-0">
                {item.name.endsWith('.pdf') ? <FileText className="w-4 h-4" /> :
                 item.name.endsWith('.json') ? <FileCode className="w-4 h-4" /> :
                 <Archive className="w-4 h-4" />}
              </div>
              <div className="truncate">
                <div className="font-semibold text-neutral-200 truncate">{item.name}</div>
                <div className="text-[10px] text-neutral-400 font-mono mt-0.5">{item.size} · Completed</div>
              </div>
            </div>

            <button className="px-3 py-1.5 rounded-xl bg-neutral-800 hover:bg-neutral-700 text-neutral-200 text-[11px] font-medium transition active:scale-95 flex-shrink-0 ml-2">
              Open
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};
