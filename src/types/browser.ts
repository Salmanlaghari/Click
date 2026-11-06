export type BrowserProfile = 'simple' | 'developer' | 'power' | 'advance';

export interface Tab {
  id: string;
  url: string;
  title: string;
  favicon?: string;
  isIncognito: boolean;
  history: string[];
  historyIndex: number;
  isLoading?: boolean;
  readerMode?: boolean;
  isDesktop?: boolean;
  profile: BrowserProfile;
  lastActive: number;
}

export interface Bookmark {
  id: string;
  title: string;
  url: string;
  favicon?: string;
  category: 'Popular' | 'Dev' | 'Tools' | 'News';
  createdAt: number;
}

export interface HistoryItem {
  id: string;
  title: string;
  url: string;
  visitedAt: number;
  profile: BrowserProfile;
  isIncognito: boolean;
}

export interface DownloadItem {
  id: string;
  name: string;
  size: string;
  url: string;
  progress: number;
  status: 'downloading' | 'completed' | 'paused';
  mimeType: string;
  downloadedAt: number;
}

export interface NetworkRequest {
  id: string;
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  status: number;
  type: 'document' | 'script' | 'stylesheet' | 'xhr' | 'image' | 'font';
  size: string;
  timeMs: number;
  timestamp: string;
}

export interface ConsoleLog {
  id: string;
  type: 'log' | 'info' | 'warn' | 'error';
  message: string;
  source: string;
  time: string;
}

export interface VpnServer {
  id: string;
  name: string;
  city: string;
  country: string;
  flag: string;
  pingMs: number;
  loadPercent: number;
  ip: string;
}

export interface SavedPassword {
  id: string;
  site: string;
  domain: string;
  username: string;
  passwordMasked: string;
  lastUsed: string;
}
