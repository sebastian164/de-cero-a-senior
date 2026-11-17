// Taller nivel-06/01: mediciones en navegador sobre la construcción de producción.
import { chromium, expect, test, type Page } from '@playwright/test';
import { gzipSync } from 'node:zlib';
import lighthouse from 'lighthouse';
import { guardarMedicion } from './registro';

const COMPILADOR = process.env.COMPILADOR === '1';
const ARCHIVO = 'n6-01.json';
const BASE = `http://127.0.0.1:${process.env.PUERTO_APP ?? 49510}`;

async function jsDe(page: Page, ruta: string) {
  const lotes: { url: string; gzip: number; bytes: number; alCargar: boolean }[] = [];
  let cargo = false;
  page.on('load', () => { cargo = true; });
  page.on('response', async (r) => {
    if (r.request().resourceType() !== 'script') return;
    const alCargar = !cargo;
    const c = await r.body().catch(() => Buffer.alloc(0));
    lotes.push({ url: r.url(), bytes: c.length, gzip: gzipSync(c).length, alCargar });
  });
  await page.goto(ruta, { waitUntil: 'networkidle' });
  const suma = (f: (x: (typeof lotes)[number]) => boolean) => lotes.filter(f).reduce((s, x) => s + x.gzip, 0);
  return { gzipInicial: suma((x) => x.alCargar), gzipTotal: suma(() => true), archivos: lotes.length };
}

test.describe('paquete, listas y filtro (sin compilador)', () => {
  test.skip(COMPILADOR, 'estas mediciones se hacen en la construcción normal');

  test('punto 1: el JavaScript inicial baja al pasar a servidor + carga diferida', async ({ browser }) => {
    const antes = await jsDe(await (await browser.newContext()).newPage(), '/n6/01-rendimiento/paquete/antes');
    const despues = await jsDe(await (await browser.newContext()).newPage(), '/n6/01-rendimiento/paquete/despues');
    guardarMedicion(ARCHIVO, 'paquete', { antes, despues });
    expect(despues.gzipInicial).toBeLessThan(antes.gzipInicial);
  });

  test('punto 4: 10.000 filas, sin y con virtualización', async ({ browser }) => {
    const r: Record<string, { ms: number; nodos: number }> = {};
    for (const modo of ['completo', 'virtual']) {
      const page = await (await browser.newContext()).newPage();
      await page.goto(`/n6/01-rendimiento/filas/${modo}`, { waitUntil: 'networkidle' });
      await page.getByRole('button', { name: 'Mostrar 10.000 filas' }).click();
      const t = await page.getByTestId('resultado').textContent();
      const [ms, nodos] = [...t!.matchAll(/\d+/g)].map((m) => Number(m[0]));
      r[modo] = { ms: ms!, nodos: nodos! };
    }
    guardarMedicion(ARCHIVO, 'filas', r);
    expect(r.completo!.nodos).toBe(10_000);
    expect(r.virtual!.nodos).toBeLessThan(60);
    expect(r.virtual!.ms).toBeLessThan(r.completo!.ms);
  });

  test('punto 5: useDeferredValue mantiene la escritura fluida con un filtro costoso (CPU 4× más lenta)', async ({ browser }) => {
    const resultado: Record<string, { medianaMs: number; maximoMs: number; muestras: number[] }> = {};
    for (const modo of ['sin-diferido', 'con-diferido']) {
      const contexto = await browser.newContext();
      const page = await contexto.newPage();
      const cdp = await contexto.newCDPSession(page);
      await page.goto(`/n6/01-rendimiento/filtro/${modo}`, { waitUntil: 'networkidle' });
      await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
      await page.evaluate(() => {
        const w = window as unknown as { __lat: number[] };
        w.__lat = [];
        // Tiempo desde que el campo recibe la tecla hasta el siguiente cuadro pintado.
        document.addEventListener('input', () => { const t = performance.now(); requestAnimationFrame(() => requestAnimationFrame(() => w.__lat.push(performance.now() - t))); }, true);
      });
      await page.getByLabel('Filtro').click();
      for (const c of 'cafe 1') { await page.keyboard.press(c === ' ' ? 'Space' : c); await page.waitForTimeout(250); }
      await page.waitForTimeout(1500);
      const lat = (await page.evaluate(() => (window as unknown as { __lat: number[] }).__lat)).map((x) => Math.round(x));
      const orden = [...lat].sort((a, b) => a - b);
      resultado[modo] = { medianaMs: orden[Math.floor(orden.length / 2)]!, maximoMs: orden.at(-1)!, muestras: lat };
      await contexto.close();
    }
    guardarMedicion(ARCHIVO, 'filtro', resultado);
    expect(resultado['con-diferido']!.medianaMs).toBeLessThan(resultado['sin-diferido']!.medianaMs);
  });

  test('punto 7: métricas web en campo (web-vitals) frente a Lighthouse (laboratorio)', async ({ request }) => {
    const navegador = await chromium.launch({ args: ['--remote-debugging-port=49599'] });
    try {
      const page = await navegador.newPage();
      await page.goto(`${BASE}/n6/01-rendimiento/vitales`, { waitUntil: 'networkidle' });
      await page.getByRole('button', { name: 'Pulsar' }).click();
      await page.waitForTimeout(500);
      await page.goto('about:blank');                              // al ocultar la página, web-vitals envía lo pendiente
      await page.waitForTimeout(500);
      const campo = (await (await request.get('/api/vitales')).json()) as { nombre: string; valor: number; ruta: string }[];
      const de = (n: string) => campo.filter((m) => m.nombre === n && m.ruta === '/n6/01-rendimiento/vitales').at(-1)?.valor;

      const lab = await lighthouse(`${BASE}/n6/01-rendimiento/vitales`, { port: 49599, output: 'json', onlyCategories: ['performance'], logLevel: 'error' });
      const a = lab!.lhr.audits;
      const comparacion = {
        campo: { LCP: de('LCP'), CLS: de('CLS'), FCP: de('FCP'), TTFB: de('TTFB'), INP: de('INP') },
        laboratorio: { LCP: a['largest-contentful-paint']!.numericValue, CLS: a['cumulative-layout-shift']!.numericValue, FCP: a['first-contentful-paint']!.numericValue, TBT: a['total-blocking-time']!.numericValue, puntaje: lab!.lhr.categories.performance!.score },
        nota: 'Campo: una sola visita en este equipo, sin limitación. Laboratorio: Lighthouse con su limitación de red y CPU por defecto.',
      };
      guardarMedicion(ARCHIVO, 'vitales', comparacion);
      expect(comparacion.campo.LCP, 'LCP recogido en campo').toBeGreaterThan(0);
      expect(comparacion.campo.FCP, 'FCP recogido en campo').toBeGreaterThan(0);
      expect(comparacion.laboratorio.LCP).toBeGreaterThan(0);
    } finally { await navegador.close(); }
  });
});

test('punto 6: el compilador de React memoriza solo; sin él, el hijo sin memo se renderiza en cada tic', async ({ page }) => {
  await page.goto('/n6/01-rendimiento/compilador', { waitUntil: 'networkidle' });
  for (let i = 0; i < 5; i++) {
    await page.getByRole('button', { name: /Tic manual/ }).click();
    await page.getByRole('button', { name: /Tic simple/ }).click();
  }
  const r = await page.evaluate(() => (window as unknown as { __renders: Record<string, number> }).__renders);
  guardarMedicion(ARCHIVO, COMPILADOR ? 'compiladorActivado' : 'compiladorDesactivado', r);
  expect(r.manual, 'memo manual: solo el montaje').toBe(1);
  expect(r.simple).toBe(COMPILADOR ? 1 : 6);                       // 1 montaje + 5 tics sin compilador
});
