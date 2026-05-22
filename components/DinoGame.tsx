import React, { useEffect, useRef, useCallback, useImperativeHandle, forwardRef } from 'react';

export type DinoGameStatus = 'idle' | 'playing' | 'crashed';

export interface DinoGameHandle {
  jump: () => void;
  start: () => void;
  restart: () => void;
}

interface DinoGameProps {
  autoPlay?: boolean;
  onGameOver?: (score: number) => void;
  className?: string;
  isPaused?: boolean;
  onStatusChange?: (status: DinoGameStatus) => void;
}

const DinoGame = forwardRef<DinoGameHandle, DinoGameProps>(function DinoGame({
  autoPlay = false,
  onGameOver,
  className = "",
  isPaused = false,
  onStatusChange
}, ref) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const autoPlayInterval = useRef<number | null>(null);
  const statusPollInterval = useRef<number | null>(null);
  const lastStatusRef = useRef<DinoGameStatus>('idle');

  // Listen for score messages from the iframe
  useEffect(() => {
    const handleMessage = (e: MessageEvent) => {
      if (e.data?.type === 'dino-game-over' && onGameOver) {
        onGameOver(e.data.score);
      }
    };
    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [onGameOver]);

  useEffect(() => {
    const emitStatus = (status: DinoGameStatus) => {
      if (lastStatusRef.current === status) return;
      lastStatusRef.current = status;
      onStatusChange?.(status);
    };

    statusPollInterval.current = window.setInterval(() => {
      const iframe = iframeRef.current;
      if (!iframe?.contentWindow) return;

      try {
        const runner = (iframe.contentWindow as any).Runner?.instance_;
        if (!runner) return;

        if (runner.crashed) {
          emitStatus('crashed');
        } else if (runner.playing) {
          emitStatus('playing');
        } else {
          emitStatus('idle');
        }
      } catch (_) { /* cross-origin safety */ }
    }, 120);

    return () => {
      if (statusPollInterval.current) clearInterval(statusPollInterval.current);
    };
  }, [onStatusChange]);

  // AutoPlay AI - polls the iframe's Runner instance
  useEffect(() => {
    if (!autoPlay) return;

    autoPlayInterval.current = window.setInterval(() => {
      const iframe = iframeRef.current;
      if (!iframe?.contentWindow) return;

      try {
        const runner = (iframe.contentWindow as any).Runner?.instance_;
        if (!runner) return;

        // Auto-start
        if (!runner.playing && !runner.crashed) {
          runner.playing = true;
          runner.update();
          runner.tRex?.startJump(runner.currentSpeed);
          return;
        }

        // Auto-restart on crash
        if (runner.crashed) {
          runner.restart();
          return;
        }

        // Jump over obstacles
        if (runner.playing && !runner.crashed && runner.horizon) {
          const obs = runner.horizon.obstacles;
          if (obs && obs.length > 0) {
            const o = obs[0];
            const tRex = runner.tRex;
            if (o.xPos < 130 && o.xPos > 20 && tRex && !tRex.jumping) {
              tRex.startJump(runner.currentSpeed);
            }
          }
        }
      } catch (_) { /* cross-origin safety */ }
    }, 50);

    return () => {
      if (autoPlayInterval.current) clearInterval(autoPlayInterval.current);
    };
  }, [autoPlay]);

  const startGame = useCallback(() => {
    const iframe = iframeRef.current;
    if (!iframe?.contentWindow) return;

    try {
      const runner = (iframe.contentWindow as any).Runner?.instance_;
      if (!runner) return;

      if (!runner.playing && !runner.crashed) {
        runner.playing = true;
        runner.update();
        runner.tRex?.startJump(runner.currentSpeed);
      }
    } catch (_) { /* cross-origin safety */ }
  }, []);

  const jumpGame = useCallback(() => {
    const iframe = iframeRef.current;
    if (!iframe?.contentWindow) return;

    try {
      const runner = (iframe.contentWindow as any).Runner?.instance_;
      if (!runner) return;

      if (runner.playing && !runner.crashed && runner.tRex && !runner.tRex.jumping && !runner.tRex.ducking) {
        runner.tRex.startJump(runner.currentSpeed);
      }
    } catch (_) { /* cross-origin safety */ }
  }, []);

  const restartGame = useCallback(() => {
    const iframe = iframeRef.current;
    if (!iframe?.contentWindow) return;

    try {
      const runner = (iframe.contentWindow as any).Runner?.instance_;
      if (!runner) return;

      if (runner.crashed) {
        runner.restart();
      } else if (!runner.playing) {
        runner.restart();
      }
    } catch (_) { /* cross-origin safety */ }
  }, []);

  // Expose imperative handle so parent can call jump/start/restart directly
  // (avoids React state → effect round-trip that caused button lag)
  useImperativeHandle(ref, () => ({
    jump: jumpGame,
    start: startGame,
    restart: restartGame,
  }), [jumpGame, startGame, restartGame]);

  // Handle tap/click to interact with the game inside the iframe
  const handleInteraction = useCallback(() => {
    const status = lastStatusRef.current;
    if (status === 'crashed') {
      restartGame();
      return;
    }
    if (status === 'idle') {
      startGame();
      return;
    }
    jumpGame();
  }, [jumpGame, restartGame, startGame]);

  return (
    <div className={`relative w-full overflow-hidden ${className} ${autoPlay ? 'pointer-events-none' : ''}`}>
      <iframe
        ref={iframeRef}
        src="/dino/game.html"
        className="w-full border-none bg-transparent"
        style={{ height: '160px', minHeight: '150px' }}
        scrolling="no"
        title="Dino Game"
        allow="autoplay"
      />
      {/* Tap overlay for non-autoPlay mode - captures taps and forwards to iframe */}
      {!autoPlay && (
        <div
          className="absolute inset-0 z-10 cursor-pointer"
          onClick={handleInteraction}
          onTouchStart={(e) => { e.preventDefault(); handleInteraction(); }}
        />
      )}
    </div>
  );
});

export default DinoGame;
