
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // CRITICAL: Sets relative path so app works at https://user.github.io/RepoName/
  base: './', 
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  }
});
