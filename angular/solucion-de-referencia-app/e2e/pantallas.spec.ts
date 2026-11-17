import { readFileSync } from 'node:fs';
import { captura, expect, test } from './base';

// Abre cada pantalla de la aplicación (ejemplo y taller de las 28 sesiones), espera a que la red quede quieta,
// guarda una captura y falla si hubo errores en la consola.
const sesiones = [...readFileSync('src/app/sesiones.ts', 'utf8').matchAll(/id: '([^']+)', carpeta: '[^']+', titulo: '[^']+', ejemplo: (true|false)/g)]
  .map(([, id, ejemplo]) => ({ id, ejemplo: ejemplo === 'true' }));

test('inicio', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Soluciones de referencia de la ruta Angular' })).toBeVisible();
  await expect(page.getByText(`${sesiones.length} sesiones.`)).toBeVisible();
  await page.screenshot({ path: captura('00-inicio'), fullPage: true });
});

// Pantallas que provocan un error HTTP a propósito al abrir (y lo muestran): solo se admite ese.
const ESPERADOS: Record<string, RegExp> = {
  'n3-04/taller': /HTTP 500 \/api\/n3-04\/tablero\/recomendaciones/,
};

for (const s of sesiones) {
  for (const tipo of s.ejemplo ? ['ejemplo', 'taller'] : ['taller']) {
    test.describe(`${s.id} ${tipo}`, () => {
      const esperado = ESPERADOS[`${s.id}/${tipo}`];
      if (esperado) test.use({ erroresEsperados: [esperado] });
      test('abre sin errores', async ({ page }) => {
        await page.goto(`/${s.id}/${tipo}`);
        await page.waitForLoadState('networkidle');
        await expect(page.locator('main')).not.toBeEmpty();
        await expect(page.locator('main')).not.toContainText('PENDIENTE-DE-ESCRIBIR');
        await page.screenshot({ path: captura(`${s.id}-${tipo}`), fullPage: true });
      });
    });
  }
}
