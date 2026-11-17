import { defineConfig, devices } from '@playwright/test';

const PUERTO = Number(process.env.PUERTO_E2E ?? 49200);

export default defineConfig({
  testDir: '.',
  testMatch: ['e2e/**/*.spec.ts', 'talleres/**/*.e2e.ts', 'proyectos/**/*.e2e.ts'],
  // Solo se ignoran estas carpetas en la RAÍZ del proyecto (un patrón '**/tmp/**' ignoraría todo dentro de una copia que viva bajo una carpeta tmp).
  testIgnore: [new RegExp(`^${process.cwd().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}/(node_modules|dist|tmp|\\.ci-limpio)/`)],
  outputDir: 'resultados-e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${PUERTO}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: {
    command: `PUERTO=${PUERTO} RAIZ=dist/app/browser node servidor/servidor.mjs`,
    url: `http://localhost:${PUERTO}/`,
    reuseExistingServer: false,
    timeout: 30_000,
  },
});
