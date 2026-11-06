import React, { useState, useEffect } from 'react';
import { 
  Wifi, 
  BatteryMedium, 
  BatteryCharging, 
  ShieldCheck, 
  Lock, 
  ChevronDown,
  Circle,
  Square,
  Triangle
} from 'lucide-react';

interface PhoneFrameProps {
  children: React.ReactNode;
  vpnConnected: boolean;
  onOpenNotificationShade: () => void;
  onHomeGesture?: () => void;
  onBackGesture?: () => void;
  onTabsGesture?: () => void;
  isLandscape?: boolean;
  showFrame?: boolean;
}

export const PhoneFrame: React.FC<PhoneFrameProps> = ({
  children,
  vpnConnected,
  onOpenNotificationShade,
  onHomeGesture,
  onBackGesture,
  onTabsGesture,
  isLandscape = false,
  showFrame = true,
}) => {
  const [time, setTime] = useState('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTime(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false }));
    };
    updateTime();
    const interval = setInterval(updateTime, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className={`relative transition-all duration-300 flex items-center justify-center ${
      showFrame ? 'p-2 sm:p-5' : 'p-0 w-full h-full'
    }`}>
      {/* Phone Hardware Chassis */}
      <div 
        className={`relative transition-all duration-300 bg-neutral-900 border-[7px] border-neutral-800 shadow-[0_25px_60px_-15px_rgba(0,0,0,0.8),0_0_0_1px_rgba(255,255,255,0.08)] ${
          showFrame ? 'rounded-[46px]' : 'rounded-none border-0'
        } ${
          isLandscape 
            ? 'w-[780px] h-[390px]' 
            : 'w-full max-w-[400px] h-[780px] max-h-[92vh]'
        } flex flex-col overflow-hidden`}
      >
        {/* Hardware side buttons (Visible when frame enabled) */}
        {showFrame && (
          <>
            {/* Power button right */}
            <div className="absolute -right-[9px] top-28 w-[3px] h-12 bg-neutral-700 rounded-r-sm pointer-events-none" />
            {/* Volume buttons left */}
            <div className="absolute -left-[9px] top-24 w-[3px] h-10 bg-neutral-700 rounded-l-sm pointer-events-none" />
            <div className="absolute -left-[9px] top-36 w-[3px] h-10 bg-neutral-700 rounded-l-sm pointer-events-none" />
          </>
        )}

        {/* Android Status Bar (Interactive pull-down) */}
        <div 
          onClick={onOpenNotificationShade}
          className="h-7 px-6 bg-neutral-950 flex items-center justify-between text-neutral-300 text-[11px] font-medium select-none cursor-pointer hover:bg-neutral-900/60 transition flex-shrink-0 z-30 group"
          title="Tap to pull down Android Notification Shade"
        >
          {/* Time & Notifications */}
          <div className="flex items-center gap-2">
            <span className="font-semibold text-neutral-200">{time || '10:42'}</span>
            {vpnConnected && (
              <span className="inline-flex items-center gap-0.5 text-indigo-400" title="Click VPN Tunnel Active">
                <Lock className="w-2.5 h-2.5" />
              </span>
            )}
            <ChevronDown className="w-3 h-3 text-neutral-600 opacity-0 group-hover:opacity-100 transition" />
          </div>

          {/* Front Camera Cutout (Punch-hole) */}
          <div className="w-3.5 h-3.5 rounded-full bg-black border border-neutral-800 flex items-center justify-center flex-shrink-0">
            <div className="w-1.5 h-1.5 rounded-full bg-neutral-950/80" />
          </div>

          {/* Network & Battery Status */}
          <div className="flex items-center gap-2 text-[10px]">
            <span className="font-mono text-[9px] text-neutral-400 font-bold">5G</span>
            <Wifi className="w-3.5 h-3.5 text-neutral-300" />
            <div className="flex items-center gap-0.5">
              <span className="text-[10px] font-mono">88%</span>
              <BatteryMedium className="w-4 h-4 text-emerald-400" />
            </div>
          </div>
        </div>

        {/* Screen Viewport Content */}
        <div className="flex-1 relative overflow-hidden bg-neutral-950 flex flex-col">
          {children}
        </div>

        {/* Android Bottom Navigation Bar (Gesture pill) */}
        <div className="h-5 bg-neutral-950 flex items-center justify-center flex-shrink-0 z-30 select-none">
          <div 
            onClick={onHomeGesture}
            className="w-32 h-1 rounded-full bg-neutral-600/80 hover:bg-neutral-400 hover:h-1.5 cursor-pointer transition-all active:scale-95"
            title="Android Home Gesture"
          />
        </div>
      </div>
    </div>
  );
};
