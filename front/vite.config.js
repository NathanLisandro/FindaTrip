import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// O build sai direto para o static do Spring, entao a aplicacao serve o front pronto.
// Em desenvolvimento, o proxy manda /api para o backend na 8080.
export default defineConfig({
  plugins: [react()],
  build: { outDir: '../src/main/resources/static', emptyOutDir: true },
  server: { proxy: { '/api': 'http://localhost:8080' } },
});
