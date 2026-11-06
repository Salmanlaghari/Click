export interface MockPage {
  title: string;
  url: string;
  favicon: string;
  content: string;
  category?: string;
  author?: string;
  date?: string;
}

export const MOCK_PAGES: Record<string, MockPage> = {
  'https://en.wikipedia.org/wiki/Web_browser': {
    title: 'Web browser - Wikipedia',
    url: 'https://en.wikipedia.org/wiki/Web_browser',
    favicon: '🌐',
    content: `
      <article class="p-6 space-y-4">
        <h1 class="text-2xl font-bold border-b border-neutral-700 pb-2">Web browser</h1>
        <p class="text-sm text-neutral-400">From Wikipedia, the free encyclopedia</p>
        <p class="leading-relaxed">
          A <strong>web browser</strong> (commonly referred to as a browser) is application software for accessing the World Wide Web or a local website. When a user requests a web page from a particular website, the browser retrieves its files from a web server and displays the page on the user's screen.
        </p>
        <div class="bg-neutral-800/60 p-4 rounded-xl border border-neutral-700 text-sm space-y-2">
          <h3 class="font-semibold text-emerald-400">Key Components of Modern Browsers:</h3>
          <ul class="list-disc list-inside space-y-1 text-neutral-300">
            <li><strong>User Interface:</strong> Address bar, back/forward buttons, bookmarking menu, tabs.</li>
            <li><strong>Browser Engine:</strong> Marshals actions between the UI and rendering engine.</li>
            <li><strong>Rendering Engine:</strong> Produces formatted visual output (Blink, Gecko, WebKit).</li>
            <li><strong>Networking:</strong> Handles HTTP/HTTPS, DNS-over-HTTPS, caching, and proxying.</li>
            <li><strong>JavaScript Interpreter:</strong> Executes scripts (V8, SpiderMonkey, JavaScriptCore).</li>
          </ul>
        </div>
        <p class="leading-relaxed text-sm text-neutral-300">
          Click Browser uniquely separates WebView profile spaces into Simple, Developer, Power, and Advance workspaces with isolated cookie caches and privacy controls.
        </p>
      </article>
    `
  },
  'https://github.com/trending': {
    title: 'Trending repositories on GitHub',
    url: 'https://github.com/trending',
    favicon: '🐙',
    content: `
      <div class="p-5 space-y-4">
        <div class="flex items-center justify-between border-b border-neutral-800 pb-3">
          <div class="flex items-center gap-2">
            <span class="text-2xl">🐙</span>
            <div>
              <h2 class="font-bold text-lg">Trending Repositories</h2>
              <p class="text-xs text-neutral-400">See what the GitHub community is most excited about today.</p>
            </div>
          </div>
          <span class="text-xs px-2.5 py-1 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-full font-mono">Today</span>
        </div>
        
        <div class="space-y-3">
          <div class="p-4 rounded-xl bg-neutral-900 border border-neutral-800 hover:border-neutral-700 transition">
            <div class="flex items-center justify-between">
              <span class="text-blue-400 font-semibold hover:underline cursor-pointer">SagerNet / sing-box</span>
              <span class="text-xs text-neutral-400">★ 22.8k</span>
            </div>
            <p class="text-xs text-neutral-300 mt-1">The universal proxy platform with high performance and tun routing engine.</p>
            <div class="flex items-center gap-3 mt-3 text-[11px] text-neutral-400 font-mono">
              <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-cyan-400"></span> Go</span>
              <span>Updated 3 hours ago</span>
            </div>
          </div>

          <div class="p-4 rounded-xl bg-neutral-900 border border-neutral-800 hover:border-neutral-700 transition">
            <div class="flex items-center justify-between">
              <span class="text-blue-400 font-semibold hover:underline cursor-pointer">google / gemini-api</span>
              <span class="text-xs text-neutral-400">★ 16.4k</span>
            </div>
            <p class="text-xs text-neutral-300 mt-1">Official TypeScript & Kotlin client libraries for Google GenAI models.</p>
            <div class="flex items-center gap-3 mt-3 text-[11px] text-neutral-400 font-mono">
              <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-blue-400"></span> TypeScript</span>
              <span>Updated today</span>
            </div>
          </div>

          <div class="p-4 rounded-xl bg-neutral-900 border border-neutral-800 hover:border-neutral-700 transition">
            <div class="flex items-center justify-between">
              <span class="text-blue-400 font-semibold hover:underline cursor-pointer">mozilla / geckoview</span>
              <span class="text-xs text-neutral-400">★ 8.9k</span>
            </div>
            <p class="text-xs text-neutral-300 mt-1">Firefox Gecko web engine packaged as a drop-in Android AAR component.</p>
            <div class="flex items-center gap-3 mt-3 text-[11px] text-neutral-400 font-mono">
              <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-orange-400"></span> Rust / C++</span>
              <span>Updated yesterday</span>
            </div>
          </div>
        </div>
      </div>
    `
  },
  'https://news.ycombinator.com': {
    title: 'Hacker News',
    url: 'https://news.ycombinator.com',
    favicon: '🟧',
    content: `
      <div class="bg-[#1a1a1b] text-neutral-200 min-h-full">
        <div class="bg-[#ff6600] text-black px-4 py-2 flex items-center justify-between font-bold text-sm">
          <div class="flex items-center gap-2">
            <span class="border border-white/40 px-1 py-0.5 bg-white text-[#ff6600] font-mono text-xs">Y</span>
            <span>Hacker News</span>
            <span class="font-normal text-xs ml-2 hidden sm:inline">new | past | comments | ask | show | jobs | submit</span>
          </div>
          <span class="text-xs font-mono">login</span>
        </div>
        <div class="p-4 space-y-3 font-sans text-xs">
          <div class="space-y-1">
            <div class="flex gap-2">
              <span class="text-neutral-500">1.</span>
              <span class="text-neutral-100 font-medium hover:underline cursor-pointer">Click Browser architecture: Four isolated profiles on Android</span>
              <span class="text-neutral-500 text-[10px]">(clickbrowser.org)</span>
            </div>
            <div class="text-[10px] text-neutral-400 ml-5">
              342 points by techdev 4 hours ago | 89 comments
            </div>
          </div>

          <div class="space-y-1">
            <div class="flex gap-2">
              <span class="text-neutral-500">2.</span>
              <span class="text-neutral-100 font-medium hover:underline cursor-pointer">Single-box VPN tun tunneling in pure Android userland</span>
              <span class="text-neutral-500 text-[10px]">(github.com/sagernet)</span>
            </div>
            <div class="text-[10px] text-neutral-400 ml-5">
              512 points by networkwiz 7 hours ago | 143 comments
            </div>
          </div>

          <div class="space-y-1">
            <div class="flex gap-2">
              <span class="text-neutral-500">3.</span>
              <span class="text-neutral-100 font-medium hover:underline cursor-pointer">SQLite and Jetpack Datastore persistence benchmarks</span>
              <span class="text-neutral-500 text-[10px]">(android-developers.googleblog.com)</span>
            </div>
            <div class="text-[10px] text-neutral-400 ml-5">
              198 points by mobileguru 9 hours ago | 42 comments
            </div>
          </div>
        </div>
      </div>
    `
  },
  'https://duckduckgo.com': {
    title: 'DuckDuckGo — Privacy, simplified.',
    url: 'https://duckduckgo.com',
    favicon: '🦆',
    content: `
      <div class="p-6 flex flex-col items-center justify-center min-h-[400px] text-center space-y-4">
        <div class="w-16 h-16 rounded-full bg-orange-500/10 border border-orange-500/30 flex items-center justify-center text-3xl">
          🦆
        </div>
        <h2 class="text-xl font-bold">DuckDuckGo</h2>
        <p class="text-xs text-neutral-400 max-w-xs">
          The search engine that doesn't track you. No profile, no cookies, voluntary GPC privacy signals.
        </p>
        <div class="w-full max-w-sm p-3 bg-neutral-900 border border-neutral-700 rounded-2xl flex items-center gap-2 text-xs text-neutral-400">
          <span>🔍</span>
          <span>Search without being tracked...</span>
        </div>
      </div>
    `
  }
};
