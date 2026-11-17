// Taller nivel-04/03, punto 5: dos pruebas de extremo a extremo de recorridos críticos, contra la aplicación de producción.
import { expect, test } from '@playwright/test';

test('recorrido crítico 1: armar el carrito, y que sobreviva a recargar la página', async ({ page }) => {
  await page.goto('/n3/03-carrito', { waitUntil: 'networkidle' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.getByText('El carrito está vacío.')).toBeVisible();

  await page.getByRole('button', { name: 'Agregar Café' }).first().click();
  await page.getByRole('button', { name: 'Agregar Pan' }).first().click();
  await page.getByRole('button', { name: 'Agregar Pan' }).first().click();
  await expect(page.getByText('Pan × 2')).toBeVisible();
  await expect(page.getByLabel('total').first()).toHaveText('$25.000');   // 18.000 + 2 × 3.500

  await page.reload({ waitUntil: 'networkidle' });                        // persistencia
  await expect(page.getByText('Pan × 2')).toBeVisible();
  await page.getByRole('button', { name: 'Vaciar' }).click();
  await expect(page.getByText('El carrito está vacío.')).toBeVisible();
});

test('recorrido crítico 2: registro: error al salir del campo, error del servidor y corrección', async ({ page }) => {
  await page.goto('/n4/02-formularios', { waitUntil: 'networkidle' });
  const formulario = page.locator('section', { hasText: 'valida al salir del campo' });
  const correo = formulario.getByLabel('Correo');

  await correo.fill('no-es-correo');
  await correo.blur();
  await expect(formulario.getByText('Escribe un correo válido')).toBeVisible();   // validación al salir del campo

  await correo.fill('ana@correo.example');                                         // ya registrado en el servidor
  await formulario.getByLabel('Documento').fill('1012345');
  await formulario.getByLabel('Clave', { exact: true }).fill('una-clave-larga-1');
  await formulario.getByLabel('Confirma la clave').fill('una-clave-larga-1');
  await formulario.getByRole('button', { name: 'Registrarme' }).click();
  await expect(formulario.getByText('Ese correo ya está registrado')).toBeVisible();   // el servidor validó de nuevo

  await correo.fill('nuevo@correo.example');
  await formulario.getByRole('button', { name: 'Registrarme' }).click();
  await expect(formulario.getByText('Ese correo ya está registrado')).toHaveCount(0);
});
