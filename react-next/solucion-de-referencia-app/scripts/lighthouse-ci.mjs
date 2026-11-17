// Presupuesto de rendimiento (Lighthouse en CI): falla si el puntaje de rendimiento o el LCP de laboratorio de pantallas clave empeoran.
// Arranca `next start` sobre la construcción actual, espera a que responda y lo apaga al terminar, para que el paso de CI no dependa de
// un servidor que alguien dejó levantado. Con BASE_URL se mide un entorno ya desplegado (por ejemplo la vista previa de un PR) y no arranca nada.
import { spawn } from 'node:child_process';
import { chromium } from '@playwright/test';
import lighthouse from 'lighthouse';

const PUERTO = process.env.PUERTO_LIGHTHOUSE ?? 49513;
const BASE = process.env.BASE_URL ?? `http://127.0.0.1:${PUERTO}`;
let servidor = null;
if (!process.env.BASE_URL) {
  servidor = spawn('npx', ['next', 'start', '-p', String(PUERTO), '-H', '127.0.0.1'], { stdio: 'ignore', env: { ...process.env, NEXT_TELEMETRY_DISABLED: '1' } });
  let listo = false;
  for (let i = 0; i < 60 && !listo; i++) {
    listo = await fetch(`${BASE}/`).then((r) => r.ok, () => false);
    if (!listo) await new Promise((r) => setTimeout(r, 1000));
  }
  if (!listo) { servidor.kill(); console.error('next start no respondió en 60 s'); process.exit(1); }
}
const PANTALLAS = ['/n6/01-rendimiento/vitales', '/n5/04-renderizado/inicio', '/n5/01-servidor-cliente/productos/1'];
const MIN_PUNTAJE = 0.9; const MAX_LCP_MS = 2500; const MAX_CLS = 0.1;
const navegador = await chromium.launch({ args: ['--remote-debugging-port=49598'] });
let fallos = 0;
try {
  for (const ruta of PANTALLAS) {
    const r = await lighthouse(`${BASE}${ruta}`, { port: 49598, output: 'json', onlyCategories: ['performance'], logLevel: 'error' });
    const puntaje = r.lhr.categories.performance.score; const lcp = r.lhr.audits['largest-contentful-paint'].numericValue; const cls = r.lhr.audits['cumulative-layout-shift'].numericValue;
    const ok = puntaje >= MIN_PUNTAJE && lcp <= MAX_LCP_MS && cls <= MAX_CLS;
    console.log(`${ok ? 'OK    ' : 'FALLA '} ${ruta} puntaje=${puntaje} LCP=${Math.round(lcp)}ms CLS=${cls}`);
    if (!ok) fallos++;
  }
} finally { await navegador.close(); servidor?.kill('SIGTERM'); }
process.exit(fallos ? 1 : 0);
