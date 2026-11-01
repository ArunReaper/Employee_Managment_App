import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the API runs on the Spring Boot port; in production the jar serves this build.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': 'http://localhost:8091' } },
  test: { environment: 'jsdom', setupFiles: './src/test/setup.js', css: false },
});
