import { defineConfig, devices } from '@playwright/test';

const PUERTO = Number(process.env.PUERTO_APP ?? 49510);

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  timeout: 60_000,
  use: { baseURL: `http://127.0.0.1:${PUERTO}`, trace: 'off' },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    // WebKit (el motor de Safari) recorre todas las pantallas y la sesión de internacionalización/accesibilidad (n6-03).
    // Firefox solo con CON_FIREFOX=1: en esta máquina (macOS 27.2.1, Firefox de Playwright 1.65.1, build 1572) ni siquiera arranca: "Could not find profile folder".
    ...(process.env.CON_FIREFOX ? [{ name: 'firefox', use: { ...devices['Desktop Firefox'] }, testMatch: ['navegar.spec.ts', 'n6-03.spec.ts'] }] : []),
    { name: 'webkit', use: { ...devices['Desktop Safari'] }, testMatch: ['navegar.spec.ts', 'n6-03.spec.ts'] },
  ],
  webServer: process.env.SIN_SERVIDOR ? undefined : {
    command: `npx next start -p ${PUERTO} -H 127.0.0.1`,
    url: `http://127.0.0.1:${PUERTO}/`,
    reuseExistingServer: false,
    timeout: 120_000,
    env: { NEXT_TELEMETRY_DISABLED: '1', SESION_SECRETO: 'secreto-de-pruebas-de-32-caracteres!!', SECRETO_DEL_SERVIDOR: 'valor-secreto-del-servidor', SECRETO_WEBHOOK: 'secreto-compartido-del-webhook' },
  },
});
