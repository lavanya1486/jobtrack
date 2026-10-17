import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The API base URL is empty in development because every request goes through the
// proxy below (`/api` -> backend). In production the SPA is served behind the same
// origin as the API (or VITE_API_BASE_URL points at the deployed backend).
const backend = process.env.VITE_BACKEND_URL || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    // 0.0.0.0 so the dev server is reachable from outside the container / preview host.
    host: '0.0.0.0',
    port: Number(process.env.PORT || 5173),
    strictPort: false,
    allowedHosts: true,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/actuator': { target: backend, changeOrigin: true },
      '/v3/api-docs': { target: backend, changeOrigin: true },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    proxy: { '/api': { target: backend, changeOrigin: true } },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/tests/setup.js',
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{js,jsx}'],
      exclude: ['src/tests/**', 'src/main.jsx'],
    },
  },
});
