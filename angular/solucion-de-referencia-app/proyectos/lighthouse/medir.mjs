// Punto 1 del taller n6-01: Lighthouse en perfil móvil (predeterminado) sobre la aplicación servida.
import { spawn } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as chromeLauncher from 'chrome-launcher';
import lighthouse from 'lighthouse';
const raiz = join(dirname(fileURLToPath(import.meta.url)), '../..');
const srv = spawn('node', ['servidor/servidor.mjs'], { cwd: raiz, env: { ...process.env, PUERTO: '49251', RAIZ: 'dist/app/browser' }, stdio: 'ignore' });
await new Promise((r) => setTimeout(r, 800));
const chrome = await chromeLauncher.launch({ chromePath: process.env.CHROME_PATH, chromeFlags: ['--headless=new', '--no-sandbox'] });
try {
  const res = {};
  for (const ruta of ['/n6-01/taller/filas-virtuales', '/n6-01/taller/sin-defer']) {
    const r = await lighthouse(`http://localhost:49251${ruta}`, { port: chrome.port, onlyCategories: ['performance'], output: 'json', logLevel: 'error' });
    const a = r.lhr.audits;
    res[ruta] = { rendimiento: Math.round(r.lhr.categories.performance.score * 100), LCP_ms: Math.round(a['largest-contentful-paint'].numericValue), CLS: +a['cumulative-layout-shift'].numericValue.toFixed(3), TBT_ms: Math.round(a['total-blocking-time'].numericValue), FCP_ms: Math.round(a['first-contentful-paint'].numericValue), formFactor: r.lhr.configSettings.formFactor, INP: a['interaction-to-next-paint']?.numericValue ?? 'no medible en una carga sin interacciones: ver taller.e2e.ts (punto 1, INP con una interacción real)' };
  }
  console.log(JSON.stringify(res, null, 2));
  writeFileSync(join(raiz, 'tmp/lighthouse.json'), JSON.stringify(res, null, 2));
} finally { await chrome.kill(); srv.kill(); }
