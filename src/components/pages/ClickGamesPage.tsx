import React, { useState, useEffect, useRef } from 'react';
import { Gamepad2, RotateCcw, Trophy, Play, Zap } from 'lucide-react';
import confetti from 'canvas-confetti';

export const ClickGamesPage: React.FC = () => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [score, setScore] = useState(0);
  const [highScore, setHighScore] = useState(1280);
  const [gameOver, setGameOver] = useState(false);
  
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const gameStateRef = useRef({
    playerY: 150,
    velocity: 0,
    obstacles: [] as Array<{ x: number; width: number; height: number; speed: number }>,
    particles: [] as Array<{ x: number; y: number; vx: number; vy: number; color: string; life: number }>,
    frame: 0
  });

  const jump = () => {
    if (!isPlaying) {
      startGame();
      return;
    }
    if (gameStateRef.current.playerY >= 140) {
      gameStateRef.current.velocity = -9.5;
    }
  };

  const startGame = () => {
    setIsPlaying(true);
    setGameOver(false);
    setScore(0);
    gameStateRef.current = {
      playerY: 150,
      velocity: 0,
      obstacles: [{ x: 340, width: 20, height: 35, speed: 4 }],
      particles: [],
      frame: 0
    };
  };

  useEffect(() => {
    if (!isPlaying) return;

    let animationId: number;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const gravity = 0.45;
    const groundY = 160;

    const loop = () => {
      gameStateRef.current.frame++;
      const state = gameStateRef.current;

      // Update player
      state.velocity += gravity;
      state.playerY += state.velocity;
      if (state.playerY > groundY - 20) {
        state.playerY = groundY - 20;
        state.velocity = 0;
      }

      // Spawn obstacles
      if (state.frame % 85 === 0) {
        const height = 25 + Math.random() * 25;
        state.obstacles.push({
          x: canvas.width + 10,
          width: 18 + Math.random() * 10,
          height: height,
          speed: 3.8 + Math.min(state.frame * 0.001, 3.5)
        });
      }

      // Move obstacles & collision check
      for (let i = state.obstacles.length - 1; i >= 0; i--) {
        const obs = state.obstacles[i];
        obs.x -= obs.speed;

        // Player hitbox: x: 40, y: playerY, w: 20, h: 20
        const playerBox = { x: 40, y: state.playerY, w: 20, h: 20 };
        const obsBox = { x: obs.x, y: groundY - obs.height, w: obs.width, h: obs.height };

        if (
          playerBox.x < obsBox.x + obsBox.w &&
          playerBox.x + playerBox.w > obsBox.x &&
          playerBox.y < obsBox.y + obsBox.h &&
          playerBox.y + playerBox.h > obsBox.y
        ) {
          // Crash!
          setGameOver(true);
          setIsPlaying(false);
          return;
        }

        if (obs.x + obs.width < 0) {
          state.obstacles.splice(i, 1);
          setScore((s) => {
            const next = s + 10;
            if (next > highScore) setHighScore(next);
            if (next === 500 || next === 1000) {
              try { confetti({ particleCount: 30, spread: 60 }); } catch (_) {}
            }
            return next;
          });
        }
      }

      // Draw scene
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      // Cyber grid background
      ctx.strokeStyle = 'rgba(34, 197, 94, 0.15)';
      ctx.lineWidth = 1;
      for (let x = 0; x < canvas.width; x += 30) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, canvas.height);
        ctx.stroke();
      }

      // Ground line
      ctx.strokeStyle = '#22c55e';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(0, groundY);
      ctx.lineTo(canvas.width, groundY);
      ctx.stroke();

      // Draw obstacles (Cyber Firewall nodes)
      state.obstacles.forEach((obs) => {
        ctx.fillStyle = '#ef4444';
        ctx.shadowColor = '#ef4444';
        ctx.shadowBlur = 8;
        ctx.fillRect(obs.x, groundY - obs.height, obs.width, obs.height);
        ctx.shadowBlur = 0;
      });

      // Draw player (Click Cyber Cube)
      ctx.fillStyle = '#22c55e';
      ctx.shadowColor = '#22c55e';
      ctx.shadowBlur = 10;
      ctx.fillRect(40, state.playerY, 20, 20);
      
      // Eyes / visor
      ctx.fillStyle = '#ffffff';
      ctx.shadowBlur = 0;
      ctx.fillRect(52, state.playerY + 4, 6, 4);

      animationId = requestAnimationFrame(loop);
    };

    animationId = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(animationId);
  }, [isPlaying, highScore]);

  return (
    <div className="h-full flex flex-col items-center justify-between p-4 bg-black text-green-400 font-cyber">
      {/* Top Bar */}
      <div className="w-full flex items-center justify-between border-b border-green-500/30 pb-2">
        <div className="flex items-center gap-2">
          <Gamepad2 className="w-5 h-5 text-green-400" />
          <span className="font-bold text-sm tracking-wider">CLICK // MATRIX RUNNER</span>
        </div>
        <div className="flex items-center gap-3 text-xs">
          <span className="flex items-center gap-1 text-neutral-400 font-mono">
            <Trophy className="w-3.5 h-3.5 text-amber-400" /> {highScore}
          </span>
          <span className="px-2 py-0.5 rounded bg-green-500/10 border border-green-500/30 font-mono font-bold">
            PTS: {score}
          </span>
        </div>
      </div>

      {/* Main Game Screen */}
      <div 
        onClick={jump}
        className="w-full max-w-[340px] my-auto flex flex-col items-center cursor-pointer select-none"
      >
        <div className="relative border-2 border-green-500/40 rounded-2xl overflow-hidden bg-neutral-950 shadow-[0_0_25px_rgba(34,197,94,0.15)]">
          <canvas
            ref={canvasRef}
            width={340}
            height={200}
            className="block"
          />

          {!isPlaying && !gameOver && (
            <div className="absolute inset-0 bg-black/80 backdrop-blur-xs flex flex-col items-center justify-center p-4 text-center space-y-3">
              <div className="w-12 h-12 rounded-full bg-green-500/20 border border-green-400 flex items-center justify-center">
                <Play className="w-6 h-6 text-green-400 ml-1" />
              </div>
              <h3 className="text-sm font-bold text-white">READY TO RUN?</h3>
              <p className="text-[11px] text-green-300 font-mono">
                Tap anywhere or press Spacebar to jump over firewalls!
              </p>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  startGame();
                }}
                className="px-4 py-2 rounded-xl bg-green-500 text-black font-bold text-xs uppercase hover:bg-green-400 transition"
              >
                START GAME
              </button>
            </div>
          )}

          {gameOver && (
            <div className="absolute inset-0 bg-black/85 backdrop-blur-xs flex flex-col items-center justify-center p-4 text-center space-y-2">
              <span className="text-red-500 font-bold text-lg tracking-widest animate-pulse">FIREWALL DETECTED</span>
              <p className="text-xs text-neutral-300 font-mono">Score: {score} · Best: {highScore}</p>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  startGame();
                }}
                className="mt-2 px-4 py-2 rounded-xl bg-green-500 text-black font-bold text-xs flex items-center gap-1.5 hover:bg-green-400 transition"
              >
                <RotateCcw className="w-3.5 h-3.5" /> REBOOT RUNNER
              </button>
            </div>
          )}
        </div>

        <p className="text-[11px] text-green-500/70 mt-3 font-mono">
          [TAP SCREEN TO JUMP]
        </p>
      </div>

      {/* Footer Info */}
      <div className="w-full text-center text-[10px] text-neutral-500 font-mono border-t border-neutral-900 pt-2">
        Offline Mini Game · Zero Network Overhead · Click Browser Advance
      </div>
    </div>
  );
};
