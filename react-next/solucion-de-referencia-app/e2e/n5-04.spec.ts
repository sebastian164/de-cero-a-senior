// Taller nivel-05/04: transmisión, metadatos, variables públicas y métricas de imágenes y fuentes, en producción.
import { expect, test, type Page } from '@playwright/test';

import { guardarMedicion } from './registro';
const registro = new Proxy({} as Record<string, unknown>, { set: (_, k, v) => (guardarMedicion('n5-04.json', String(k), v), true) });

async function tiempoHasta(page: Page, ruta: string, testid: string) {
  const t0 = Date.now();
  await page.goto(ruta, { waitUntil: 'commit' });
  const respuesta = Date.now() - t0;
  await page.getByTestId(testid).waitFor({ state: 'visible', timeout: 10_000 });
  return { primerByteMs: respuesta, contenidoUtilMs: Date.now() - t0 };
}

test('punto 3: la frontera fina muestra el contenido rápido antes que la sección lenta', async ({ browser }) => {
  const medir = async (ruta: string, id: string) => tiempoHasta(await (await browser.newContext()).newPage(), ruta, id);
  const bloqueante = await medir('/n5/04-renderizado/lenta/bloqueante', 'rapido');
  const grueso = await medir('/n5/04-renderizado/lenta/grueso', 'rapido');
  const fino = await medir('/n5/04-renderizado/lenta/fino', 'rapido');
  registro.transmision = { bloqueante, grueso, fino, nota: 'contenidoUtilMs = hasta que la sección rápida es visible; la lenta tarda 1500 ms' };
  expect(bloqueante.contenidoUtilMs).toBeGreaterThan(1400);    // nada útil hasta que termina la lenta
  expect(grueso.contenidoUtilMs).toBeGreaterThan(1400);        // la cáscara sale antes, pero el contenido útil espera
  expect(fino.contenidoUtilMs).toBeLessThan(700);              // lo rápido sale de inmediato
});

test('punto 4: metadatos dinámicos, sitemap y robots', async ({ page, request }) => {
  await page.goto('/n5/04-renderizado/producto/1');
  await expect(page).toHaveTitle('Café de Huila · Referencia');
  await expect(page.locator('meta[name=description]')).toHaveAttribute('content', 'Café de Huila a 32000 pesos');
  await expect(page.locator('link[rel=canonical]')).toHaveAttribute('href', /\/n5\/04-renderizado\/producto\/1$/);
  await page.goto('/n5/04-renderizado/mi-cuenta');
  await expect(page.locator('meta[name=robots]')).toHaveAttribute('content', /noindex/);

  const robots = await (await request.get('/robots.txt')).text();
  expect(robots).toContain('Disallow: /n5/04-renderizado/mi-cuenta');
  expect(robots).toMatch(/Sitemap: http:\/\/127\.0\.0\.1:\d+\/sitemap\.xml/);
  const sitemap = await (await request.get('/sitemap.xml')).text();
  for (const id of ['1', '2', '3']) expect(sitemap).toContain(`/n5/04-renderizado/producto/${id}</loc>`);
  expect(sitemap).toContain('/n5/04-renderizado/categoria/bebidas</loc>');
  const noExiste = await request.get('/n5/04-renderizado/producto/999');
  expect(noExiste.status()).toBe(404);                         // "no encontrado" responde 404, no 200
  const html = await request.get('/n5/04-renderizado/inicio');
  expect(html.headers()['x-nextjs-cache'] ?? html.headers()['cache-control']).toBeTruthy();
});

test('punto 6: NEXT_PUBLIC_ queda en el paquete del navegador y la variable de servidor no', async ({ page }) => {
  const cuerposJs: string[] = [];
  page.on('response', async (r) => { if (r.request().resourceType() === 'script') cuerposJs.push(await r.text().catch(() => '')); });
  await page.goto('/n5/04-renderizado/config-publica', { waitUntil: 'networkidle' });
  await expect(page.getByTestId('publica')).toHaveText('https://api.tienda.example');
  await expect(page.getByTestId('publica-cliente')).toHaveText('https://api.tienda.example');
  await expect(page.getByTestId('servidor')).toHaveText('definida (no se muestra)');
  const html = await page.content();
  // La pública se incrusta al construir: está en el HTML y en el JavaScript descargado (la carga RSC que viaja con él).
  const enPaquetes = cuerposJs.some((c) => c.includes('https://api.tienda.example'));
  const secretoEnPaquetes = cuerposJs.some((c) => c.includes('valor-secreto-del-servidor')) || html.includes('valor-secreto-del-servidor');
  registro.variables = { publicaEnHtml: html.includes('https://api.tienda.example'), publicaEnJavaScript: enPaquetes, secretoEnPaquetesOHtml: secretoEnPaquetes };
  expect(enPaquetes, 'la variable pública debe estar en el JavaScript descargado').toBe(true);
  expect(secretoEnPaquetes).toBe(false);
});

test('punto 7: métricas web con y sin next/image y next/font (red limitada)', async ({ browser }) => {
  const resultado: Record<string, unknown> = {};
  for (const modo of ['simple', 'optimizado'] as const) {
    const contexto = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const page = await contexto.newPage();
    const cdp = await contexto.newCDPSession(page);
    await cdp.send('Network.enable');
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 });
    await page.addInitScript(() => {
      const w = window as unknown as { __m: { lcp: number; cls: number } };
      w.__m = { lcp: 0, cls: 0 };
      new PerformanceObserver((l) => { for (const e of l.getEntries()) w.__m.lcp = e.startTime; }).observe({ type: 'largest-contentful-paint', buffered: true });
      new PerformanceObserver((l) => { for (const e of l.getEntries() as unknown as { value: number; hadRecentInput: boolean }[]) if (!e.hadRecentInput) w.__m.cls += e.value; }).observe({ type: 'layout-shift', buffered: true });
    });
    let imagenBytes = 0; let fuenteBytes = 0;
    cdp.on('Network.loadingFinished', (e: { encodedDataLength: number; requestId: string }) => { pendientes.push(e); });
    const pendientes: { encodedDataLength: number; requestId: string }[] = [];
    const tipos = new Map<string, string>();
    cdp.on('Network.responseReceived', (e: { requestId: string; type: string }) => tipos.set(e.requestId, e.type));
    await page.goto(`/n5/04-renderizado/imagenes?modo=${modo}`, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    for (const p of pendientes) { const t = tipos.get(p.requestId); if (t === 'Image') imagenBytes += p.encodedDataLength; if (t === 'Font') fuenteBytes += p.encodedDataLength; }
    const m = await page.evaluate(() => (window as unknown as { __m: { lcp: number; cls: number } }).__m);
    resultado[modo] = { lcpMs: Math.round(m.lcp), cls: Number(m.cls.toFixed(4)), bytesImagen: imagenBytes, bytesFuente: fuenteBytes };
    await contexto.close();
  }
  registro.imagenesYFuentes = { ...resultado, red: '1,6 Mbps, 150 ms de latencia' };
  const s = resultado.simple as { bytesImagen: number; cls: number }; const o = resultado.optimizado as { bytesImagen: number; cls: number };
  expect(o.bytesImagen).toBeLessThan(s.bytesImagen);          // formato moderno y tamaño adaptado
  expect(o.cls).toBeLessThanOrEqual(s.cls);                    // dimensiones reservadas
});
