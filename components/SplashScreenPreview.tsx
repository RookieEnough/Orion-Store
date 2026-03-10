import React, { useState, useEffect } from 'react';

const SplashScreenPreview: React.FC = () => {
  const [isFadingOut, setIsFadingOut] = useState(false);

  useEffect(() => {
    const fadeTimer = setTimeout(() => {
      setIsFadingOut(true);
    }, 2100); // Start fade-out slightly before the timeout in App.tsx

    return () => clearTimeout(fadeTimer);
  }, []);

  return (
    <div 
      className={`fixed inset-0 z-[9999] flex items-center justify-center bg-[#0f0f11] transition-opacity duration-300 ${isFadingOut ? 'opacity-0' : 'opacity-100'}`}
      aria-hidden="true"
    >
      <div className="w-24 h-24">
        {/*
          This is the inline SVG of the logo.
          We use CSS classes to target and animate each shape individually,
          mimicking the native Android AnimatedVectorDrawable.
        */}
        <svg width="100" height="100" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
          <g className="splash-triangle">
            <path
                fill="#FFFFFF"
                d="M39,52 L61,52 A4,4 0 0,0 63.8,48.5 L51.8,29.5 A4,4 0 0,0 50,28 A4,4 0 0,0 48.2,29.5 L36.2,48.5 A4,4 0 0,0 39,52 Z"/>
          </g>
          <g className="splash-circle">
            <path
                fill="#FFFFFF"
                d="M 36.8,54.7 A 12.2,12.2 0 1,1 36.79,54.7 Z"/>
          </g>
          <g className="splash-square">
            <path
                fill="#FFFFFF"
                d="M 54.9,54.7 H 71.4 A 3.9,3.9 0 0,1 75.3,58.6 V 75.2 A 3.9,3.9 0 0,1 71.4,79.1 H 54.9 A 3.9,3.9 0 0,1 51,75.2 V 58.6 A 3.9,3.9 0 0,1 54.9,54.7 Z"/>
          </g>
        </svg>
      </div>
    </div>
  );
};

export default SplashScreenPreview;