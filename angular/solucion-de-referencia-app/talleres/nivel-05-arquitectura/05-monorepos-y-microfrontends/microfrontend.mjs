// Punto 5 del taller: anfitrión y remoto (Native Federation) construidos por separado, compuestos en el navegador.
// Comprueba composición, estilos aislados, sesión (no compartida) y el fallo sin CORS. Falla con código 1 si algo no se cumple.
import { chromium } from '@playwright/test';
import { spawn } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const raiz = join(aqui, '../../../proyectos/mfe');
const capturas = join(aqui, '../../../capturas'); mkdirSync(capturas, { recursive: true });
let fallos = 0;
const comprobar = (ok, t, d = '') => { console.log(`${ok ? 'OK   ' : 'FALLO'} ${t}${d ? ' — ' + d : ''}`); if (!ok) fallos++; };
const espera = (ms) => new Promise((r) => setTimeout(r, ms));

async function escenario(sinCors) {
  const srv = spawn('node', ['servir.mjs'], { cwd: raiz, env: { ...process.env, SIN_CORS: sinCors ? '1' : '0' }, stdio: 'ignore' });
  await espera(800);
  const nav = await chromium.launch();
  const page = await nav.newPage();
  const errores = []; const fallosRed = [];
  page.on('console', (m) => { if (m.type() === 'error') errores.push(m.text()); });
  page.on('pageerror', (e) => errores.push(String(e)));
  page.on('requestfailed', (r) => fallosRed.push(r.url()));
  try {
    await page.goto('http://localhost:49281/');
    await page.getByRole('link', { name: 'Resumen (remoto)' }).click();
    if (sinCors) {
      await espera(1500);
      return { visible: await page.getByRole('heading', { name: 'Resumen del remoto' }).count(), errores, fallosRed };
    }
    await page.getByRole('heading', { name: 'Resumen del remoto' }).waitFor();
    // Composición: el remoto muestra su contenido y es interactivo (señales del remoto dentro de la aplicación del anfitrión)
    await page.getByRole('button', { name: /Remoto pulsado 0/ }).click();
    await page.getByRole('button', { name: 'Remoto pulsado 1' }).waitFor({ timeout: 3000 }).catch(() => {});
    comprobar(await page.getByRole('button', { name: 'Remoto pulsado 1' }).isVisible(), 'el remoto se carga en ejecución y responde a eventos');
    const total = await page.locator('[data-prueba=total]').textContent();
    comprobar(total.trim() === '$150,000', 'problema: el remoto formatea con el LOCALE_ID del anfitrión (aquí en-US, sin registrar es-CO)', total);
    // Estilos: cada componente conserva sus estilos; los del remoto no se filtran al anfitrión
    const colorRemoto = await page.getByRole('heading', { name: 'Resumen del remoto' }).evaluate((e) => getComputedStyle(e).color);
    comprobar(colorRemoto === 'rgb(0, 128, 0)', 'los estilos del remoto se aplican a su componente (verde)', colorRemoto);
    await page.getByRole('link', { name: 'Inicio' }).click();
    const colorHost = await page.getByRole('heading', { name: 'Inicio del anfitrión' }).evaluate((e) => getComputedStyle(e).color);
    comprobar(colorHost !== 'rgb(0, 128, 0)', 'el estilo `h2` del remoto no se filtra a los h2 del anfitrión', colorHost);
    // Sesión: el remoto no comparte el servicio del anfitrión; solo ve lo que haya en el almacenamiento del navegador
    await page.getByRole('button', { name: 'Entrar como ana' }).click();
    await page.getByRole('link', { name: 'Resumen (remoto)' }).click();
    await page.getByRole('heading', { name: 'Resumen del remoto' }).waitFor();
    await page.waitForFunction(() => document.querySelector('[data-prueba=sesion]')?.textContent === 'ana', null, { timeout: 3000 }).catch(() => {});
    comprobar((await page.locator('[data-prueba=sesion]').textContent()) === 'ana', 'la sesión llega al remoto solo porque ambos leen sessionStorage');
    // Dependencias: Angular se descarga una sola vez (singleton compartido)
    const angulares = await page.evaluate(() => performance.getEntriesByType('resource').map((r) => r.name).filter((n) => /_angular_core\./.test(n)));
    comprobar(new Set(angulares).size === 1, 'Angular core se descarga una sola vez para anfitrión y remoto (singleton)', [...new Set(angulares)].join(', '));
    comprobar(errores.length === 0, 'sin errores en la consola', errores.join(' | '));
    await page.screenshot({ path: join(capturas, 'n5-05-taller-microfrontend.png'), fullPage: true });
    return {};
  } finally { await nav.close(); srv.kill(); await espera(300); }
}

await escenario(false);
const sin = await escenario(true);
comprobar(sin.visible === 0 && (sin.errores.length > 0 || sin.fallosRed.length > 0), 'sin CORS en el remoto el anfitrión no puede cargarlo (problema anotado)', `errores: ${sin.errores.length}, peticiones fallidas: ${sin.fallosRed.length}`);
process.exit(fallos ? 1 : 0);
