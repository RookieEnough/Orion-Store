import React, { useRef, useEffect } from 'react';

/**
 * Lightweight canvas animation that shows the Chrome Dino running.
 * Uses the actual spritesheet for authentic visuals, but draws only
 * the dino + ground + a cactus in a tight loop. No iframe, no Runner engine.
 */
interface DinoCirclePreviewProps {
  size?: number;
  isPaused?: boolean;
}

const DinoCirclePreview: React.FC<DinoCirclePreviewProps> = ({ size = 96, isPaused = false }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const animRef = useRef<number>(0);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    ctx.scale(dpr, dpr);

    const sprite = new Image();
    sprite.src = '/dino/assets/default_100_percent/100-offline-sprite.png';

    // LDPI sprite coordinates
    const TREX = { x: 848, y: 2, w: 44, h: 47 };
    const RUN_FRAMES = [88, 132];
    const CLOUD = { x: 86, y: 2, w: 46, h: 14 };
    const GROUND = { x: 2, y: 54, w: 600, h: 12 };

    const SCALE = 1.0; 
    const groundY = size * 0.72;
    let frame = 0;
    let tick = 0;
    
    // Cloud state
    const clouds = [
      { x: size, y: groundY - 45 },
      { x: size + 60, y: groundY - 30 }
    ];

    const draw = () => {
      if (isPaused) {
        animRef.current = requestAnimationFrame(draw);
        return;
      }

      ctx.clearRect(0, 0, size, size);

      if (!sprite.complete) {
        animRef.current = requestAnimationFrame(draw);
        return;
      }

      // Use absolute time so the leg speed is exactly the same on 60hz, 120hz, and 144hz monitors
      const now = Date.now();
      // Switch dino running frame exactly every 120ms (a relaxed jog)
      frame = Math.floor(now / 120) % 2;

      const dinoW = TREX.w * SCALE;
      const dinoH = TREX.h * SCALE;
      const dinoX = size * 0.28;
      const dinoY = groundY - dinoH + 2;

      // Draw clouds (scrolling slow)
      clouds.forEach(cloud => {
        ctx.drawImage(
          sprite,
          CLOUD.x, CLOUD.y, CLOUD.w, CLOUD.h,
          cloud.x, cloud.y, CLOUD.w * SCALE, CLOUD.h * SCALE
        );
        cloud.x -= 0.5;
        if (cloud.x < -CLOUD.w * SCALE) {
          cloud.x = size + Math.random() * 20;
          cloud.y = groundY - 30 - Math.random() * 20;
        }
      });

      // Draw ground line (scrolling normal speed matching the 120ms leg ticks)
      // Base speed: 60 pixels per second
      const groundOffset = Math.floor((now / 1000) * 60) % Math.floor(GROUND.w * 0.3);
      ctx.save();
      ctx.globalAlpha = 0.4;
      ctx.drawImage(
        sprite,
        GROUND.x + groundOffset, GROUND.y, size / SCALE, GROUND.h,
        0, groundY, size, GROUND.h * SCALE
      );
      ctx.restore();

      // Draw running T-Rex
      const frameOffset = RUN_FRAMES[frame] ?? 0;
      ctx.drawImage(
        sprite,
        TREX.x + frameOffset, TREX.y, TREX.w, TREX.h,
        dinoX, dinoY, dinoW, dinoH
      );

      animRef.current = requestAnimationFrame(draw);
    };

    sprite.onload = () => draw();
    if (sprite.complete) draw();

    return () => cancelAnimationFrame(animRef.current);
  }, [size, isPaused]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: size, height: size }}
      className="rounded-full"
    />
  );
};

export default DinoCirclePreview;
