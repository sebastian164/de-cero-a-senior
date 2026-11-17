import { spawn, type ChildProcess } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { captura, expect, test } from '../../../e2e/base';

const CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";
const CON_CSP = 'http://localhost:49272';

test.describe('n6-03 taller', () => {
  test('punto 1: bypassSecurityTrustHtml ejecuta el script del comentario; innerHTML saneado y texto, no', async ({ page }) => {
    const ejecutado = () => page.evaluate(() => (window as unknown as { __xss?: number }).__xss ?? 0);
    await page.goto('/n6-03/taller/xss-peligroso');
    await expect(page.locator('.comentario')).toHaveCount(2);
    await expect.poll(ejecutado).toBeGreaterThan(0);                                 // el XSS funcionó
    await page.screenshot({ path: captura('n6-03-taller-xss-peligroso'), fullPage: true });
    for (const ruta of ['xss-saneado', 'xss-seguro']) {
      await page.goto(`/n6-03/taller/${ruta}`);
      await expect(page.locator('.comentario')).toHaveCount(2);
      await page.waitForTimeout(300);
      expect(await ejecutado(), ruta).toBe(0);
    }
    await expect(page.getByText('<img src=x onerror=')).toBeVisible();               // el texto se ve tal cual
    await page.screenshot({ path: captura('n6-03-taller-xss-seguro'), fullPage: true });
    await page.goto('/n6-03/taller/xss-saneado');
    expect(await page.locator('.comentario img').first().getAttribute('onerror')).toBeNull();   // Angular quitó el atributo peligroso
  });

  test.describe('punto 2: la guarda se salta; el servidor rechaza', () => {
    test.use({ erroresEsperados: [/HTTP 403 \/api\/n6-03\/secreto/] });
    test('con la bandera falsa se ve la pantalla, pero no hay datos', async ({ page, request }) => {
      await page.goto('/n6-03/taller');
      await page.goto('/n6-03/taller/admin');                                        // sin bandera: la guarda redirige
      await expect(page.getByRole('heading', { name: 'Administración' })).toHaveCount(0);
      await page.evaluate(() => localStorage.setItem('rol', 'admin'));               // el atacante, con las herramientas del navegador
      await page.goto('/n6-03/taller/admin');
      await expect(page.getByRole('heading', { name: 'Administración' })).toBeVisible();   // guarda saltada
      await expect(page.getByRole('alert')).toHaveText('El servidor rechazó la petición (403).');
      expect((await request.get('/api/n6-03/secreto')).status()).toBe(403);
      await page.screenshot({ path: captura('n6-03-taller-guarda-saltada') });
    });
  });
  test('punto 2: con sesión real el servidor entrega el dato', async ({ page }) => {
    await page.goto('/n6-03/taller');
    await page.getByRole('button', { name: 'Iniciar sesión real (admin)' }).click();
    await page.getByRole('link', { name: 'Administración' }).click();
    await expect(page.getByRole('status')).toHaveText('ventas del trimestre: 12 M');
  });
});

test.describe('n6-03 taller: punto 5, CSP', () => {
  test.use({ erroresEsperados: [/Content Security Policy|HTTP 500 \/api\/n3-04\/tablero\/recomendaciones/] });          // el bloqueo se registra como error de consola
  test.setTimeout(420_000);
  let srv: ChildProcess;
  test.beforeAll(async () => {
    srv = spawn('node', ['servidor/servidor.mjs'], { env: { ...process.env, PUERTO: '49272', RAIZ: 'dist/app/browser', CSP }, stdio: 'ignore' });
    await new Promise((r) => setTimeout(r, 1000));
  });
  test.afterAll(() => srv.kill());

  test('la CSP bloquea el controlador inline del comentario malicioso', async ({ page }) => {
    test.info().annotations.push({ type: 'consola', description: 'el navegador registra el bloqueo como error de consola' });
    const violaciones: string[] = [];
    await page.exposeFunction('__violacion', (d: string) => violaciones.push(d));
    await page.addInitScript(() => document.addEventListener('securitypolicyviolation', (e) => (window as unknown as { __violacion: (d: string) => void }).__violacion(`${e.violatedDirective}`)));
    await page.goto(`${CON_CSP}/n6-03/taller/xss-peligroso`);
    await expect(page.locator('.comentario')).toHaveCount(2);
    await page.waitForTimeout(400);
    expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss ?? 0)).toBe(0);
    expect(violaciones.some((v) => v.startsWith('script-src'))).toBe(true);
  });

  test('con la CSP, todas las pantallas de la aplicación cargan sin violaciones (política ajustada)', async ({ page }) => {
    const ids = [...readFileSync('src/app/sesiones.ts', 'utf8').matchAll(/id: '([^']+)', carpeta: '[^']+', titulo: '[^']+', ejemplo: (true|false)/g)];
    const violaciones: string[] = [];
    await page.exposeFunction('__violacion', (d: string) => violaciones.push(d));
    await page.addInitScript(() => document.addEventListener('securitypolicyviolation', (e) => (window as unknown as { __violacion: (d: string) => void }).__violacion(`${location.pathname} ${e.violatedDirective} ${e.blockedURI}`)));
    let vistas = 0;
    for (const [, id, ejemplo] of ids) {
      for (const tipo of ejemplo === 'true' ? ['ejemplo', 'taller'] : ['taller']) {
        if (id === 'n6-03' && tipo === 'taller') continue;                                // ahí se provoca a propósito
        await page.goto(`${CON_CSP}/${id}/${tipo}`);
        await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {});
        vistas++;
      }
    }
    expect(vistas).toBeGreaterThan(40);
    expect(violaciones).toEqual([]);
  });
});
