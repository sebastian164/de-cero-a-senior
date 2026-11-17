// Abre cada pantalla de la aplicación en Chromium sin cabeza, guarda una captura y falla si hay errores en la consola.
import { expect, test, type ConsoleMessage, type Page } from '@playwright/test';
import { PANTALLAS } from '../src/rutas';

/** Errores que la propia pantalla provoca a propósito y anuncia (taller de límites de error, error.tsx). */
const ESPERADOS: Record<string, RegExp[]> = {
  // La pantalla de Suspense y límites de error provoca dos fallos a propósito y los captura con límites de error:
  // React los informa por consola aunque estén controlados.
  '/n3/05-suspense': [/Servicio de recomendaciones caído/, /Fallo simulado al cargar pedidos/],
};

function vigilarConsola(page: Page, ruta: string) {
  const errores: string[] = [];
  const permitidos = ESPERADOS[ruta] ?? [];
  page.on('console', (m: ConsoleMessage) => {
    if (m.type() === 'error' && !permitidos.some((r) => r.test(m.text()))) errores.push(m.text());
  });
  page.on('pageerror', (e) => { if (!permitidos.some((r) => r.test(e.message))) errores.push(`pageerror: ${e.message}`); });
  return errores;
}

for (const pantalla of PANTALLAS) {
  test(`abre ${pantalla.ruta}`, async ({ page }) => {
    const errores = vigilarConsola(page, pantalla.ruta);
    const respuesta = await page.goto(pantalla.ruta, { waitUntil: 'networkidle' });
    expect(respuesta?.status(), 'código HTTP').toBeLessThan(400);
    await expect(page.getByText(pantalla.espera, { exact: false }).first()).toBeVisible();
    const nombre = pantalla.ruta.replace(/^\//, '').replace(/[/?=&]/g, '_') || 'indice';
    const motor = test.info().project.name;
    await page.screenshot({ path: `capturas/${motor === 'chromium' ? '' : `${motor}/`}${nombre}.png`, fullPage: true });
    expect(errores, 'errores de consola').toEqual([]);
  });
}

test('el índice enlaza todas las pantallas', async ({ page }) => {
  const errores = vigilarConsola(page, '/');
  await page.goto('/');
  for (const p of PANTALLAS) await expect(page.locator(`a[href="${p.ruta}"]`)).toHaveCount(1);
  await page.screenshot({ path: 'capturas/indice.png', fullPage: true });
  expect(errores).toEqual([]);
});
