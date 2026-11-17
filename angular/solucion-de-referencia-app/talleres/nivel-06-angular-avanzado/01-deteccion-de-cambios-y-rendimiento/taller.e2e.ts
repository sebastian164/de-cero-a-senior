import { spawn, type ChildProcess } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { captura, expect, test } from '../../../e2e/base';

const medidas: Record<string, unknown> = {};
const guardar = () => { mkdirSync('tmp', { recursive: true }); writeFileSync('tmp/medidas-n6-01.json', JSON.stringify(medidas, null, 2)); };

test.describe('n6-01 taller', () => {
  // Punto 1 (INP): Lighthouse no mide INP sin interacciones. Se mide aquí en laboratorio con una interacción real (un clic de
  // Playwright) y un PerformanceObserver de tipo 'event' (la API que usa la biblioteca web-vitals), con la CPU 4 veces más lenta.
  // INP = la interacción más lenta de la página (con pocas interacciones, la peor); aquí cada medición tiene una sola.
  test('punto 1: INP del clic que pinta 10.000 filas frente a la lista virtual (laboratorio, CPU 4x)', async ({ page, context }) => {
    const cdp = await context.newCDPSession(page);
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    const inp = async (enlace: string, desde: string) => {
      const muestras: number[] = [];
      for (let i = 0; i < 5; i++) {
        await page.goto(desde);
        await page.waitForLoadState('networkidle');
        await page.evaluate(() => {
          (window as unknown as { __inp: number[] }).__inp = [];
          new PerformanceObserver((lista) => {
            for (const e of lista.getEntries() as PerformanceEventTiming[]) if (e.interactionId) (window as unknown as { __inp: number[] }).__inp.push(e.duration);
          }).observe({ type: 'event', durationThreshold: 16, buffered: true });
        });
        await page.getByRole('link', { name: enlace }).click();
        await page.waitForTimeout(600);
        const d = await page.evaluate(() => (window as unknown as { __inp: number[] }).__inp);
        muestras.push(Math.max(...d));
      }
      muestras.sort((a, b) => a - b);
      return { mediana: muestras[2], muestras };
    };
    const plano = await inp('10.000 filas', '/n6-01/taller/filas-virtuales');
    const virtual = await inp('Desplazamiento virtual', '/n6-01/taller/filas-planas');
    medidas['inp'] = { cpu: '4x', plano, virtual, nota: 'medida de laboratorio con una interacción por carga; no es INP de campo' };
    expect(plano.mediana).toBeGreaterThan(virtual.mediana);
    guardar();
  });

  test('punto 4: 10.000 filas planas frente a desplazamiento virtual', async ({ page }) => {
    await page.goto('/n6-01/taller/filas-virtuales');
    await page.waitForLoadState('networkidle');
    // Tiempo desde el clic hasta que la lista pinta (dos fotogramas después de insertar las filas).
    const medir = (enlace: string, selector: string) => page.evaluate(async ([texto, sel]) => {
      const a = [...document.querySelectorAll('a')].find((x) => x.textContent?.trim() === texto)!;
      const t0 = performance.now();
      a.click();
      while (!document.querySelector(sel)) await new Promise((r) => setTimeout(r, 0));
      await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
      return performance.now() - t0;
    }, [enlace, selector]);
    const msPlano = await medir('10.000 filas', '[role=listitem]');
    const nodosPlano = await page.locator('[role=listitem]').count();
    await page.screenshot({ path: captura('n6-01-taller-filas-planas') });
    const msVirtual = await medir('Desplazamiento virtual', 'cdk-virtual-scroll-viewport [role=listitem]');
    await page.waitForTimeout(200);
    const nodosVirtual = await page.locator('[role=listitem]').count();
    medidas['filas'] = { msPlano: Math.round(msPlano), nodosPlano, msVirtual: Math.round(msVirtual), nodosVirtual };
    expect(nodosPlano).toBe(10_000);
    expect(nodosVirtual).toBeLessThan(60);
    expect(msVirtual).toBeLessThan(msPlano);
    // El desplazamiento virtual sigue dando acceso a todas las filas.
    await page.locator('cdk-virtual-scroll-viewport').evaluate((v) => v.scrollTo(0, 28 * 9_990));
    await expect(page.getByText('Cliente 9995: ')).toBeVisible();
    await page.screenshot({ path: captura('n6-01-taller-filas-virtuales') });
    guardar();
  });

  test('punto 5: @defer deja fuera del paquete inicial de la ruta los dos componentes pesados', async ({ page }) => {
    const bytes = async (ruta: string, antesDeEsperar?: () => Promise<void>) => {
      const descargas: Record<string, number> = {};
      const alRespuesta = async (r: import('@playwright/test').Response) => {
        if (r.url().endsWith('.js')) descargas[r.url().split('/').pop()!] = (await r.body()).length;
      };
      page.on('response', alRespuesta);
      await page.goto(ruta);
      await page.waitForLoadState('networkidle');
      await antesDeEsperar?.();
      page.off('response', alRespuesta);
      return descargas;
    };
    const suma = (d: Record<string, number>) => Object.values(d).reduce((a, b) => a + b, 0);
    const sin = await bytes('/n6-01/taller/sin-defer');
    await expect(page.getByRole('region', { name: 'Mapa' })).toBeVisible();
    const con = await bytes('/n6-01/taller/con-defer');
    console.log('sin', sin, 'con', con);
    await expect(page.getByText('Gráfica pendiente')).toBeHidden().catch(() => {});   // la gráfica entra en pantalla y se carga
    await expect(page.getByRole('region', { name: 'Mapa' })).toHaveCount(0);           // el mapa sigue sin cargarse
    const trasGrafica = await page.evaluate(() => performance.getEntriesByType('resource').filter((r) => r.name.endsWith('.js')).length);
    const descargasClic: string[] = [];
    page.on('request', (r) => { if (r.url().endsWith('.js')) descargasClic.push(r.url().split('/').pop()!); });
    await page.getByRole('button', { name: 'Mostrar mapa' }).click();
    await expect(page.getByRole('region', { name: 'Mapa' })).toBeVisible();
    expect(descargasClic.length).toBeGreaterThanOrEqual(1);                            // el mapa se descargó al interactuar
    medidas['defer'] = { bytesSinDefer: suma(sin), bytesConDeferAlEntrar: suma(con), archivosSin: Object.keys(sin).length, archivosCon: Object.keys(con).length, trasGrafica, descargasAlClicMapa: descargasClic.length };
    expect(suma(con)).toBeLessThan(suma(sin) * 0.8);
    await page.screenshot({ path: captura('n6-01-taller-defer') });
    guardar();
  });
});

test.describe('n6-01 taller: punto 6, zona frente a sin zona', () => {
  const servidores: ChildProcess[] = [];
  test.beforeAll(async () => {
    for (const [dir, puerto] of [['tmp/n6-01-zona/browser', 49241], ['tmp/n6-01-sin-zona/browser', 49242]] as const) {
      servidores.push(spawn('node', ['servidor/servidor.mjs'], { env: { ...process.env, PUERTO: String(puerto), RAIZ: dir }, stdio: 'ignore' }));
    }
    await new Promise((r) => setTimeout(r, 1000));
  });
  test.afterAll(() => servidores.forEach((s) => s.kill()));

  for (const [nombre, puerto, roto] of [['con zona', 49241, true], ['sin zona', 49242, false]] as const) {
    test(`${nombre}: ${roto ? 'los tres campos normales se actualizan solos' : 'los tres campos normales se quedan en "esperando"; las señales, no'}`, async ({ page }) => {
      await page.goto(`http://localhost:${puerto}/`);
      await expect(page.locator('[data-id=ok-timer]')).toHaveText('listo');
      await expect(page.locator('[data-id=ok-fetch]')).toHaveText('del servidor');
      await page.keyboard.press('k');
      await expect(page.locator('[data-id=ok-tecla]')).toHaveText('k');
      await page.waitForTimeout(300);
      const lectura = { timer: await page.locator('[data-id=roto-timer]').textContent(), fetch: await page.locator('[data-id=roto-fetch]').textContent(), tecla: await page.locator('[data-id=roto-tecla]').textContent() };
      medidas[`zona-${nombre}`] = lectura;
      expect(lectura).toEqual(roto ? { timer: 'listo', fetch: 'del servidor', tecla: 'k' } : { timer: 'esperando', fetch: 'esperando', tecla: 'esperando' });
      await page.screenshot({ path: captura(`n6-01-taller-${nombre.replace(' ', '-')}`) });
      guardar();
    });
  }
});
