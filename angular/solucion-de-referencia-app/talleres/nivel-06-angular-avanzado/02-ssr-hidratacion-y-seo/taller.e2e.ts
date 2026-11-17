import { spawn, type ChildProcess } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { captura, expect, test } from '../../../e2e/base';

// Solución del taller n6-02: la aplicación está en proyectos/ssr (project "n6-02-ssr"). Aquí se arranca su servidor Express
// construido (producción en 49261, desarrollo en 49262) y se comprueban los siete puntos del taller.
const PROD = 'http://localhost:49261';
const DEV = 'http://localhost:49262';
const medidas: Record<string, unknown> = {};
const guardar = () => { mkdirSync('tmp', { recursive: true }); writeFileSync('tmp/medidas-n6-02.json', JSON.stringify(medidas, null, 2)); };
const servidores: ChildProcess[] = [];
const registro: string[] = [];

test.beforeAll(async () => {
  for (const [dir, puerto] of [['tmp/n6-02-prod', 49261], ['tmp/n6-02-dev', 49262]] as const) {
    const p = spawn('node', [`${dir}/server/server.mjs`], { env: { ...process.env, PORT: String(puerto) } });
    p.stdout.on('data', (d) => registro.push(String(d))); p.stderr.on('data', (d) => registro.push(String(d)));
    servidores.push(p);
  }
  await new Promise((r) => setTimeout(r, 1500));
});
test.afterAll(() => servidores.forEach((s) => s.kill()));

const html = async (ruta: string, base = PROD) => { const r = await fetch(base + ruta); return { estado: r.status, cuerpo: await r.text() }; };

test.describe('n6-02 taller', () => {
  test('puntos 1 y 2: inicio prerenderizado, detalle en servidor y área privada en cliente', async () => {
    const inicio = await html('/');
    expect(inicio.estado).toBe(200);
    expect(inicio.cuerpo).toContain('<h1>Tienda de prueba</h1>');                           // prerenderizado: HTML con contenido
    const detalle = await html('/producto/1');
    expect(detalle.cuerpo).toContain('Teclado');                                            // servidor: HTML con los datos de la API
    const privado = await html('/privado');
    expect(privado.cuerpo).not.toContain('Área privada');                                   // cliente: solo la cáscara, sin contenido
    expect(privado.cuerpo).toContain('<app-root');
    medidas['modos'] = { inicioConContenido: true, detalleConContenido: true, privadoSoloCascara: true };
  });

  test('punto 5: las peticiones del servidor no se repiten al hidratar', async ({ page }) => {
    await fetch(`${PROD}/api/_reiniciar`, { method: 'POST' });
    const delNavegador: string[] = [];
    page.on('request', (r) => { if (r.url().includes('/api/productos')) delNavegador.push(r.url()); });
    await page.goto(`${PROD}/producto/1`);
    await expect(page.getByRole('heading', { name: 'Teclado' })).toBeVisible();
    await page.waitForLoadState('networkidle');
    const contador = await (await fetch(`${PROD}/api/_contador`)).json();
    medidas['transferencia'] = { peticionesAlApiVistasPorElNavegador: delNavegador.length, peticionesRecibidasPorLaApi: contador };
    expect(delNavegador).toEqual([]);                                                       // el navegador no la repitió
    expect(contador).toEqual({ '1': 1 });                                                   // el servidor la hizo una sola vez
    await page.screenshot({ path: captura('n6-02-taller-producto'), fullPage: true });
    // Con la navegación en el cliente (sin recarga) sí se pide: la caché solo cubre la hidratación
    await page.getByRole('link', { name: 'Inicio', exact: true }).first().click();
    await page.getByRole('link', { name: 'Producto 2' }).click();
    await expect(page.getByRole('heading', { name: 'Ratón' })).toBeVisible();
    expect((await (await fetch(`${PROD}/api/_contador`)).json())['2']).toBe(1);
  });

  test('punto 4: sin window ni localStorage directos el servidor no falla y el navegador los usa tras renderizar', async ({ page }) => {
    registro.length = 0;
    await html('/producto/2');
    await page.goto(`${PROD}/privado`);
    await expect(page.locator('[data-id=ancho]')).toContainText(/Ancho de la ventana: [1-9]\d+/);
    await page.goto(`${PROD}/producto/3`);
    await expect(page.locator('[data-id=visto] output')).toHaveText('ninguno');
    await page.goto(`${PROD}/producto/1`);
    await expect(page.locator('[data-id=visto] output')).toHaveText('3');                  // lo guardó la visita anterior (localStorage del navegador)
    expect(registro.join('')).not.toMatch(/ReferenceError|window is not defined|localStorage is not defined/);
  });

  test('punto 6: título, descripción, vista previa y JSON-LD en el HTML sin JavaScript', async ({ browser }) => {
    const ctx = await browser.newContext({ javaScriptEnabled: false });
    const page = await ctx.newPage();
    await page.goto(`${PROD}/producto/3`);
    await expect(page).toHaveTitle('Monitor | Tienda de prueba');
    await expect(page.getByRole('heading', { name: 'Monitor' })).toBeVisible();
    expect(await page.locator('meta[name=description]').getAttribute('content')).toBe('Compra Monitor por 300 dólares');
    expect(await page.locator('meta[property="og:title"]').getAttribute('content')).toBe('Monitor | Tienda de prueba');
    const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!);
    expect(ld).toMatchObject({ '@type': 'Product', name: 'Monitor', offers: { price: 300, priceCurrency: 'USD' } });
    await page.goto(`${PROD}/`);
    await expect(page).toHaveTitle('Tienda de prueba');
    expect(await page.locator('script[type="application/ld+json"]').count()).toBe(0);      // el inicio no hereda el JSON-LD de otra página
    await ctx.close();
  });

  test('código de estado correcto: 404 real para producto y ruta inexistentes', async () => {
    expect((await html('/producto/99')).estado).toBe(404);
    expect((await html('/no-existe')).estado).toBe(404);
    expect((await html('/producto/1')).estado).toBe(200);
  });

  test('estado en el servidor: la variable de módulo se comparte entre peticiones; el servicio raíz, no', async () => {
    const leer = async () => { const { cuerpo } = await html('/visitas'); return { servicio: /servicio: (\d+)/.exec(cuerpo)![1], modulo: /módulo: (\d+)/.exec(cuerpo)![1] }; };
    const a = await leer(); const b = await leer(); const c = await leer();
    medidas['estadoCompartido'] = [a, b, c];
    expect([a.servicio, b.servicio, c.servicio]).toEqual(['1', '1', '1']);
    expect(Number(c.modulo)).toBe(Number(a.modulo) + 2);
  });

  test.describe('punto 3: desajuste de hidratación (build de desarrollo)', () => {
    const errores = async (ruta: string, page: import('@playwright/test').Page) => {
      const lista: string[] = [];
      page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') lista.push(m.text()); });
      page.on('pageerror', (e) => lista.push(String(e)));
      await page.goto(DEV + ruta);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      return lista;
    };
    test.use({ erroresEsperados: [/.*/] });                  // aquí se provocan a propósito
    test('con descuadre aparece NG0500 y sin él, no', async ({ page }) => {
      const malos = await errores('/descuadre', page);
      medidas['descuadre'] = malos.map((m) => m.slice(0, 160));
      expect(malos.some((m) => /NG0500|hydrat/i.test(m))).toBe(true);
      const page2 = await page.context().newPage();
      const buenos = await errores('/cuadre', page2);
      medidas['cuadre'] = buenos;
      expect(buenos.filter((m) => /NG0500|hydrat/i.test(m))).toEqual([]);
      await expect(page2.locator('[data-id=solo-navegador]')).toBeVisible();
      // La fecha NO produce error: el servidor y el navegador difieren en el texto y el navegador lo reescribe sin avisar
      const delServidor = /Hora: ([\d:.]+)/.exec((await html('/descuadre', DEV)).cuerpo)![1];
      const delNavegador = (await page.locator('[data-id=fecha]').textContent())!.replace('Hora: ', '');
      medidas['fechaServidorFrenteANavegador'] = { delServidor, delNavegador };
      expect(delNavegador).not.toBe(delServidor);
      await page.screenshot({ path: captura('n6-02-taller-descuadre') });
      guardar();
    });
    test('solo la fecha no produce error de hidratación: el texto se reescribe', async () => {
      const hora = (await html('/cuadre', DEV)).cuerpo.match(/Hora: ([\d:.]+)/)![1];
      const d = new Date(0), dos = (n: number) => String(n).padStart(2, '0');
      expect(hora).toBe(`${dos(d.getHours())}:${dos(d.getMinutes())}:${dos(d.getSeconds())}.000`);   // valor estable renderizado en el servidor
    });
  });

  test('punto 7: LCP con y sin SSR (CPU 4x más lenta, red 4G lenta)', async ({ browser }) => {
    const lcp = async (ruta: string) => {
      const ctx = await browser.newContext();
      const page = await ctx.newPage();
      const cdp = await ctx.newCDPSession(page);
      await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
      await cdp.send('Network.enable');
      await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 });
      await page.addInitScript(() => {
        (window as unknown as { __lcp: number }).__lcp = 0;
        new PerformanceObserver((l) => { const e = l.getEntries(); (window as unknown as { __lcp: number }).__lcp = e[e.length - 1].startTime; }).observe({ type: 'largest-contentful-paint', buffered: true });
      });
      await page.goto(PROD + ruta, { waitUntil: 'networkidle' });
      const v = await page.evaluate(() => (window as unknown as { __lcp: number }).__lcp);
      await ctx.close();
      return Math.round(v);
    };
    const mediana = (xs: number[]) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];
    const con: number[] = [], sin: number[] = [];
    for (let i = 0; i < 3; i++) { con.push(await lcp('/producto/1')); sin.push(await lcp('/producto-csr/1')); }
    medidas['lcp'] = { conSsr_ms: con, sinSsr_ms: sin, medianaConSsr: mediana(con), medianaSinSsr: mediana(sin) };
    guardar();
    expect(mediana(con)).toBeLessThan(mediana(sin));
  });
});
