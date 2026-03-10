
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // CRITICAL: Sets relative path so app works at file:/// or custom protocols in Capacitor
  base: './',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      // Bundling all dependencies locally to prevent "White Screen" issues on Android
      output: {
        manualChunks: {
          'vendor': ['react', 'react-dom', 'zustand'],
          'capacitor': ['@capacitor/app', '@capacitor/core', '@capacitor/haptics', '@capacitor/local-notifications'],
        }
      }
    }
  }
});
