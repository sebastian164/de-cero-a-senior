import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

const r = (p: string) => fileURLToPath(new URL(p, import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [
      { find: /^@talleres\//, replacement: r('./talleres/') },
      { find: /^@app\//, replacement: r('./src/') },
      { find: /^@\//, replacement: r('./proyectos/') },
      // 'server-only' lanza al importarse fuera de un componente de servidor; en pruebas unitarias se neutraliza.
      { find: /^server-only$/, replacement: r('./scripts/vacio.ts') },
    ],
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    include: ['talleres/**/*.test.{ts,tsx}', 'proyectos/**/*.test.{ts,tsx}', 'src/**/*.test.{ts,tsx}'],
    exclude: ['**/node_modules/**', 'talleres/**/fallo-*/**'],
  },
});
