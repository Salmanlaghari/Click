import React, { useState } from 'react';
import { 
  Bot, 
  Send, 
  Sparkles, 
  Zap, 
  Copy, 
  Check, 
  FileText, 
  Code, 
  ShieldAlert,
  Trash2
} from 'lucide-react';

interface ClickAiPageProps {
  currentUrl: string;
}

export const ClickAiPage: React.FC<ClickAiPageProps> = ({ currentUrl }) => {
  const [messages, setMessages] = useState<Array<{ role: 'user' | 'assistant'; text: string; time: string }>>([
    {
      role: 'assistant',
      text: `Hello! I am your Click Browser AI Copilot. I can summarize current pages, analyze web code, explain technical terms, and help you browse privately.`,
      time: '10:00 AM'
    }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);

  const handleSend = (userText?: string) => {
    const textToSend = userText || input;
    if (!textToSend.trim()) return;

    const newMsg = {
      role: 'user' as const,
      text: textToSend,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    setMessages((prev) => [...prev, newMsg]);
    if (!userText) setInput('');
    setIsTyping(true);

    setTimeout(() => {
      let reply = '';
      const lower = textToSend.toLowerCase();

      if (lower.includes('summarize')) {
        reply = `📄 **Page Summary for:** \`${currentUrl || 'Current Page'}\`\n\n• **Core Topic:** Android WebView multi-profile architecture and tunnel routing.\n• **Key Points:** Isolates data storage per profile (Simple, Developer, Power, Advance), incorporates hardware-level TUN encryption via sing-box.\n• **Security Assessment:** Strict CSP, no geolocation tracking, voluntary GPC privacy signals enabled.`;
      } else if (lower.includes('profile') || lower.includes('advance')) {
        reply = `🔒 **Click Browser 4 Profiles:**\n\n1. **Simple:** Clean, distraction-free reading.\n2. **Developer:** Live inspection, DOM, JS console & network.\n3. **Power:** High-speed streaming, aggressive ad-blocking.\n4. **Advance:** Full disk partition isolation with independent app-data store and cyber UI styling.`;
      } else if (lower.includes('vpn') || lower.includes('shield')) {
        reply = `🛡️ **V9 Shield & SingBox VPN:**\n\nUses a real gVisor TUN stack on Android to tunnel all IPv4/IPv6 socket traffic (0.0.0.0/0) through encrypted endpoints with DoH (DNS-over-HTTPS) leak protection.`;
      } else {
        reply = `I processed your request regarding "${textToSend}". Click Browser's AI operates securely without saving persistent cloud cookies or sending telemetry to advertising brokers.`;
      }

      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: reply,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }
      ]);
      setIsTyping(false);
    }, 700);
  };

  const copyText = (txt: string, idx: number) => {
    navigator.clipboard.writeText(txt);
    setCopiedIndex(idx);
    setTimeout(() => setCopiedIndex(null), 1500);
  };

  const promptShortcuts = [
    { label: 'Summarize Page', icon: FileText, prompt: `Summarize the contents of ${currentUrl}` },
    { label: 'Explain Profiles', icon: Sparkles, prompt: 'Explain the 4 profiles of Click Browser' },
    { label: 'VPN Details', icon: Zap, prompt: 'How does the V9 Shield SingBox VPN work?' },
  ];

  return (
    <div className="h-full flex flex-col bg-neutral-950 text-neutral-100">
      {/* Top Header */}
      <div className="flex items-center justify-between p-3.5 bg-neutral-900 border-b border-neutral-800 flex-shrink-0">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-xl bg-purple-500/20 border border-purple-500/30 flex items-center justify-center text-purple-400">
            <Bot className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-xs font-bold flex items-center gap-1.5">
              Click AI Assistant
              <span className="text-[9px] px-1.5 py-0.2 rounded-full bg-purple-500/10 text-purple-400 font-mono border border-purple-500/20">Groq / Gemini</span>
            </h3>
            <p className="text-[10px] text-neutral-400">Page-aware private AI copilot</p>
          </div>
        </div>

        <button
          onClick={() => setMessages([messages[0]])}
          className="p-1.5 text-neutral-400 hover:text-red-400 transition"
          title="Clear Chat"
        >
          <Trash2 className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto p-3.5 space-y-3">
        {messages.map((msg, i) => (
          <div
            key={i}
            className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}
          >
            <div
              className={`max-w-[85%] p-3 rounded-2xl text-xs leading-relaxed ${
                msg.role === 'user'
                  ? 'bg-purple-600 text-white rounded-br-none shadow-md'
                  : 'bg-neutral-900 border border-neutral-800 text-neutral-200 rounded-bl-none shadow-sm'
              }`}
            >
              <div className="whitespace-pre-line">{msg.text}</div>
            </div>
            <div className="flex items-center gap-1.5 mt-1 px-1 text-[9px] text-neutral-500">
              <span>{msg.time}</span>
              {msg.role === 'assistant' && (
                <button
                  onClick={() => copyText(msg.text, i)}
                  className="hover:text-neutral-300 transition"
                  title="Copy"
                >
                  {copiedIndex === i ? <Check className="w-2.5 h-2.5 text-emerald-400" /> : <Copy className="w-2.5 h-2.5" />}
                </button>
              )}
            </div>
          </div>
        ))}

        {isTyping && (
          <div className="flex items-center gap-1.5 p-3 rounded-2xl bg-neutral-900 border border-neutral-800 w-24">
            <span className="w-1.5 h-1.5 rounded-full bg-purple-400 animate-bounce" style={{ animationDelay: '0ms' }} />
            <span className="w-1.5 h-1.5 rounded-full bg-purple-400 animate-bounce" style={{ animationDelay: '150ms' }} />
            <span className="w-1.5 h-1.5 rounded-full bg-purple-400 animate-bounce" style={{ animationDelay: '300ms' }} />
          </div>
        )}
      </div>

      {/* Suggested Quick Prompts */}
      <div className="px-3 pt-1 pb-2 flex items-center gap-1.5 overflow-x-auto no-scrollbar flex-shrink-0">
        {promptShortcuts.map((s, idx) => {
          const Icon = s.icon;
          return (
            <button
              key={idx}
              onClick={() => handleSend(s.prompt)}
              className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-neutral-900 border border-neutral-800 hover:border-neutral-700 text-[10px] text-neutral-300 whitespace-nowrap transition active:scale-95"
            >
              <Icon className="w-3 h-3 text-purple-400" />
              <span>{s.label}</span>
            </button>
          );
        })}
      </div>

      {/* Chat Input */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSend();
        }}
        className="p-3 bg-neutral-900 border-t border-neutral-800 flex items-center gap-2 flex-shrink-0"
      >
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask AI about this page or web code..."
          className="flex-1 bg-neutral-950 px-3.5 py-2.5 rounded-xl border border-neutral-800 text-xs focus:border-purple-500 focus:outline-none transition placeholder:text-neutral-500"
        />
        <button
          type="submit"
          disabled={!input.trim()}
          className="p-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 disabled:opacity-40 text-white transition active:scale-95 shadow-md"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
};
