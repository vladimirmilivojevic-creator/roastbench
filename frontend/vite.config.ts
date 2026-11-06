/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// VITE_BASE is "/roastbench/" for GitHub Pages and "/" everywhere else.
// In development the API runs on :8080 and is reached through the proxy, so the browser sees one origin.
export default defineConfig({
  base: process.env.VITE_BASE ?? '/',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
      '/v3': 'http://localhost:8080',
      '/swagger-ui': 'http://localhost:8080',
    },
  },
  build: {
    chunkSizeWarningLimit: 900,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    css: false,
    testTimeout: 20000,
  },
});
