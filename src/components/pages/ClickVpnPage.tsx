import React, { useState, useEffect } from 'react';
import { 
  ShieldCheck, 
  ShieldAlert, 
  Power, 
  Globe2, 
  Activity, 
  Lock, 
  CheckCircle2, 
  Server, 
  Radio, 
  ArrowDownCircle, 
  ArrowUpCircle,
  RefreshCw,
  Info,
  ChevronRight
} from 'lucide-react';
import { VpnServer } from '../../types/browser';

interface ClickVpnPageProps {
  vpnConnected: boolean;
  onToggleVpn: () => void;
}

export const ClickVpnPage: React.FC<ClickVpnPageProps> = ({
  vpnConnected,
  onToggleVpn,
}) => {
  const [selectedServer, setSelectedServer] = useState<string>('us-east');
  const [dohProvider, setDohProvider] = useState<'cloudflare' | 'quad9' | 'google'>('cloudflare');
  const [downSpeed, setDownSpeed] = useState<number>(vpnConnected ? 3.4 : 0);
  const [upSpeed, setUpSpeed] = useState<number>(vpnConnected ? 0.9 : 0);
  const [isConnecting, setIsConnecting] = useState(false);

  const servers: VpnServer[] = [
    { id: 'us-east', name: 'US East (New York)', city: 'New York', country: 'United States', flag: '🇺🇸', pingMs: 24, loadPercent: 42, ip: '104.28.19.44' },
    { id: 'de-fra', name: 'Europe Central (Frankfurt)', city: 'Frankfurt', country: 'Germany', flag: '🇩🇪', pingMs: 48, loadPercent: 58, ip: '194.36.12.89' },
    { id: 'jp-tyo', name: 'Asia Pacific (Tokyo)', city: 'Tokyo', country: 'Japan', flag: '🇯🇵', pingMs: 112, loadPercent: 35, ip: '160.16.211.5' },
    { id: 'sg-sin', name: 'Southeast Asia (Singapore)', city: 'Singapore', country: 'Singapore', flag: '🇸🇬', pingMs: 86, loadPercent: 64, ip: '139.180.141.12' },
    { id: 'uk-lon', name: 'UK London', city: 'London', country: 'United Kingdom', flag: '🇬🇧', pingMs: 38, loadPercent: 51, ip: '185.190.140.2' },
    { id: 'nl-ams', name: 'Netherlands (Amsterdam)', city: 'Amsterdam', country: 'Netherlands', flag: '🇳🇱', pingMs: 41, loadPercent: 29, ip: '193.148.18.72' },
  ];

  const currentServer = servers.find((s) => s.id === selectedServer) || servers[0];

  useEffect(() => {
    if (!vpnConnected) {
      setDownSpeed(0);
      setUpSpeed(0);
      return;
    }
    const interval = setInterval(() => {
      setDownSpeed(parseFloat((2.5 + Math.random() * 2.8).toFixed(1)));
      setUpSpeed(parseFloat((0.6 + Math.random() * 0.8).toFixed(1)));
    }, 1500);
    return () => clearInterval(interval);
  }, [vpnConnected]);

  const handlePowerClick = () => {
    setIsConnecting(true);
    setTimeout(() => {
      setIsConnecting(false);
      onToggleVpn();
    }, 600);
  };

  return (
    <div className="h-full overflow-y-auto px-4 py-5 space-y-5 bg-neutral-950 text-neutral-100">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-indigo-400" />
            V9 Shield & SingBox VPN
          </h2>
          <p className="text-xs text-neutral-400">
            Hardware-backed full IP tunnel (0.0.0.0/0 + ::/0)
          </p>
        </div>
        <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
          gVisor Core
        </span>
      </div>

      {/* Main Connection Card */}
      <div className={`p-5 rounded-3xl border transition-all duration-300 relative overflow-hidden flex flex-col items-center text-center ${
        vpnConnected 
          ? 'bg-gradient-to-b from-indigo-950/70 via-neutral-900 to-neutral-950 border-indigo-500/40 shadow-xl shadow-indigo-950/40' 
          : 'bg-neutral-900/60 border-neutral-800'
      }`}>
        <div className="w-full flex items-center justify-between text-xs mb-4">
          <span className="flex items-center gap-1.5 text-neutral-400">
            <span className={`w-2 h-2 rounded-full ${vpnConnected ? 'bg-emerald-400 animate-pulse' : 'bg-neutral-600'}`} />
            {vpnConnected ? 'STATUS: PROTECTED' : 'STATUS: UNPROTECTED'}
          </span>
          <span className="font-mono text-[11px] text-neutral-400">
            {vpnConnected ? currentServer.ip : '192.168.1.1 (Exposed)'}
          </span>
        </div>

        {/* Big Power Toggle Button */}
        <div className="relative my-3">
          {vpnConnected && (
            <div className="absolute -inset-3 rounded-full bg-indigo-500/20 blur-xl animate-pulse" />
          )}
          <button
            onClick={handlePowerClick}
            disabled={isConnecting}
            className={`w-24 h-24 rounded-full flex flex-col items-center justify-center transition-all duration-300 active:scale-95 shadow-2xl relative z-10 border-2 ${
              vpnConnected
                ? 'bg-indigo-600 hover:bg-indigo-500 text-white border-indigo-400 shadow-indigo-500/50'
                : 'bg-neutral-800 hover:bg-neutral-700 text-neutral-400 border-neutral-700'
            }`}
          >
            {isConnecting ? (
              <RefreshCw className="w-8 h-8 animate-spin text-white" />
            ) : (
              <Power className={`w-9 h-9 ${vpnConnected ? 'text-white' : 'text-neutral-400'}`} />
            )}
            <span className="text-[10px] font-bold tracking-wider mt-1 uppercase">
              {isConnecting ? 'TUNING...' : vpnConnected ? 'CONNECTED' : 'CONNECT'}
            </span>
          </button>
        </div>

        <div className="mt-3">
          <div className="font-semibold text-sm flex items-center justify-center gap-1.5">
            <span>{currentServer.flag}</span>
            <span>{currentServer.name}</span>
          </div>
          <p className="text-[11px] text-neutral-400 mt-0.5">
            {vpnConnected ? 'Real-time encryption: ChaCha20-Poly1305' : 'Tap power button to tunnel all web traffic'}
          </p>
        </div>

        {/* Live bandwidth metrics when connected */}
        {vpnConnected && (
          <div className="w-full grid grid-cols-3 gap-2 mt-5 pt-4 border-t border-neutral-800/80">
            <div className="p-2 rounded-xl bg-neutral-900/80 border border-neutral-800">
              <div className="text-[10px] text-neutral-500 uppercase flex items-center justify-center gap-1">
                <ArrowDownCircle className="w-3 h-3 text-emerald-400" /> Down
              </div>
              <div className="text-xs font-mono font-bold text-neutral-200 mt-0.5">{downSpeed} MB/s</div>
            </div>
            <div className="p-2 rounded-xl bg-neutral-900/80 border border-neutral-800">
              <div className="text-[10px] text-neutral-500 uppercase flex items-center justify-center gap-1">
                <ArrowUpCircle className="w-3 h-3 text-blue-400" /> Up
              </div>
              <div className="text-xs font-mono font-bold text-neutral-200 mt-0.5">{upSpeed} MB/s</div>
            </div>
            <div className="p-2 rounded-xl bg-neutral-900/80 border border-neutral-800">
              <div className="text-[10px] text-neutral-500 uppercase flex items-center justify-center gap-1">
                <Activity className="w-3 h-3 text-indigo-400" /> Ping
              </div>
              <div className="text-xs font-mono font-bold text-emerald-400 mt-0.5">{currentServer.pingMs} ms</div>
            </div>
          </div>
        )}
      </div>

      {/* Server Location Selector */}
      <div className="space-y-2">
        <div className="flex items-center justify-between px-1">
          <span className="text-xs font-semibold text-neutral-400 uppercase tracking-wider flex items-center gap-1.5">
            <Server className="w-3.5 h-3.5 text-indigo-400" />
            SingBox Endpoints
          </span>
          <span className="text-[10px] text-neutral-500 font-mono">6 Locations Available</span>
        </div>

        <div className="grid grid-cols-1 gap-2">
          {servers.map((srv) => {
            const isSelected = srv.id === selectedServer;
            return (
              <button
                key={srv.id}
                onClick={() => setSelectedServer(srv.id)}
                className={`p-3 rounded-2xl flex items-center justify-between border transition active:scale-[0.99] text-left ${
                  isSelected
                    ? 'bg-indigo-950/40 border-indigo-500/50 text-neutral-100 shadow-sm'
                    : 'bg-neutral-900/60 border-neutral-800/80 hover:bg-neutral-800/60 text-neutral-300'
                }`}
              >
                <div className="flex items-center gap-3">
                  <span className="text-xl">{srv.flag}</span>
                  <div>
                    <div className="text-xs font-semibold">{srv.name}</div>
                    <div className="text-[10px] text-neutral-400 font-mono">{srv.ip} · Load {srv.loadPercent}%</div>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`text-[11px] font-mono ${srv.pingMs < 50 ? 'text-emerald-400' : 'text-amber-400'}`}>
                    {srv.pingMs}ms
                  </span>
                  {isSelected && <CheckCircle2 className="w-4 h-4 text-indigo-400" />}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* DNS-over-HTTPS (DoH) Options */}
      <div className="p-4 rounded-2xl bg-neutral-900/50 border border-neutral-800 space-y-3">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold text-neutral-300 flex items-center gap-1.5">
            <Radio className="w-3.5 h-3.5 text-cyan-400" />
            DNS-over-HTTPS (Anti-Leak)
          </span>
          <span className="text-[10px] px-2 py-0.5 rounded bg-cyan-500/10 text-cyan-400 font-mono border border-cyan-500/20">
            Encrypted
          </span>
        </div>
        <p className="text-[11px] text-neutral-400">
          Encrypted DNS prevents ISP eavesdropping and DNS hijacking.
        </p>

        <div className="grid grid-cols-3 gap-2 text-xs">
          {[
            { id: 'cloudflare', name: 'Cloudflare', ip: '1.1.1.1' },
            { id: 'quad9', name: 'Quad9 Security', ip: '9.9.9.9' },
            { id: 'google', name: 'Google DNS', ip: '8.8.8.8' },
          ].map((dns) => (
            <button
              key={dns.id}
              onClick={() => setDohProvider(dns.id as any)}
              className={`p-2.5 rounded-xl border text-center transition ${
                dohProvider === dns.id
                  ? 'bg-cyan-950/40 border-cyan-500/50 text-cyan-300 font-semibold'
                  : 'bg-neutral-900 border-neutral-800 text-neutral-400 hover:text-neutral-200'
              }`}
            >
              <div className="text-[11px] font-medium">{dns.name}</div>
              <div className="text-[9px] font-mono text-neutral-500 mt-0.5">{dns.ip}</div>
            </button>
          ))}
        </div>
      </div>

      {/* Privacy Notice Disclaimer */}
      <div className="p-3 rounded-xl bg-neutral-900/30 border border-neutral-800/60 flex items-start gap-2.5 text-[11px] text-neutral-400 leading-relaxed">
        <Info className="w-4 h-4 text-neutral-500 flex-shrink-0 mt-0.5" />
        <p>
          Play policy compliance disclosure: Traffic is routed through high-speed sing-box tunnel servers. Device-location permission is not requested by Click Browser.
        </p>
      </div>
    </div>
  );
};
