
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // CRITICAL: Sets relative path so app works at file:/// or custom protocols in Capacitor
  base: './', 
  build: {
    target: 'es2020',
    outDir: 'dist',
    emptyOutDir: true,
    cssCodeSplit: true,          // load only critical CSS first; other styles lazy-loaded
    sourcemap: false,            // no sourcemaps in prod → smaller output
    reportCompressedSize: false, // skip gzip estimate → faster CI builds
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('react') || id.includes('scheduler')) return 'react-vendor';
          if (id.includes('motion')) return 'motion-vendor';
          if (id.includes('@capacitor')) return 'capacitor-vendor';
          if (id.includes('zustand') || id.includes('idb-keyval')) return 'state-vendor';
          if (id.includes('fontawesome') || id.includes('@fortawesome')) return 'icons-vendor';
          return 'vendor';
        }
      }
    }
  }
});
