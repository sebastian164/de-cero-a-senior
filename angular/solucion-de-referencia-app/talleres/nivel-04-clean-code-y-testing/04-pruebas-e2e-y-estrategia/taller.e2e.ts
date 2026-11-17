import AxeBuilder from '@axe-core/playwright';
import { captura, expect, test } from '../../../e2e/base';

const BASE = '/n4-04/taller';
const agregarTeclado = async (page: import('@playwright/test').Page) => {
  await page.goto(`${BASE}/catalogo`);
  await page.getByRole('button', { name: 'Agregar Teclado al carrito' }).click();
};

// 1. Tres recorridos críticos
test.describe('recorridos críticos', () => {
  test('1a. comprar: catálogo → carrito → confirmar', async ({ page, request }) => {
    await agregarTeclado(page);
    await page.getByRole('link', { name: 'Carrito (1)' }).click();
    await expect(page.getByText('Total: $ 120.000')).toBeVisible();
    await page.getByRole('button', { name: 'Confirmar pedido' }).click();
    await expect(page.getByRole('status')).toHaveText('Pedido confirmado');
    await expect(page.getByRole('link', { name: 'Carrito (0)' })).toBeVisible();
    // El servidor recibió exactamente un pedido: se comprueba sin pausas fijas.
    await expect.poll(async () => (await (await request.get('/api/n4-04/pedidos/total')).json()).total).toBe(1);
  });

  test('1b. el carrito vacío no permite confirmar', async ({ page }) => {
    await page.goto(`${BASE}/carrito`);
    await expect(page.getByText('El carrito está vacío')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Confirmar pedido' })).toBeDisabled();
  });

  test('1c. dos productos suman en el total y el contador del carrito', async ({ page }) => {
    await page.goto(`${BASE}/catalogo`);
    await page.getByRole('button', { name: 'Agregar Teclado al carrito' }).click();
    await page.getByRole('button', { name: 'Agregar Mouse al carrito' }).click();
    await page.getByRole('link', { name: 'Carrito (2)' }).click();
    await expect(page.getByText('Total: $ 180.000')).toBeVisible();
  });
});

// 2. Red simulada, incluido un error del servidor
test.describe('red simulada', () => {
  test('2a. catálogo simulado: la prueba no depende de los datos del servidor', async ({ page }) => {
    await page.route('**/api/n4-04/catalogo', (ruta) => ruta.fulfill({ json: [{ id: 99, nombre: 'Cable', precio: 5000 }] }));
    await page.goto(`${BASE}/catalogo`);
    await expect(page.getByRole('button', { name: /^Agregar .* al carrito$/ })).toHaveCount(1);
    await page.getByRole('button', { name: 'Agregar Cable al carrito' }).click();
    await page.getByRole('link', { name: 'Carrito (1)' }).click();
    await expect(page.getByText('Total: $ 5.000')).toBeVisible();
  });

  test.describe('con un 500', () => {
    test.use({ erroresEsperados: [/HTTP 500 \/api\/n4-04\/pedidos/] });
    test('2b. un 500 al confirmar muestra el error y conserva el carrito', async ({ page }) => {
      await page.route('**/api/n4-04/pedidos', (ruta) => ruta.fulfill({ status: 500, json: { error: 'x' } }));
      await agregarTeclado(page);
      await page.getByRole('link', { name: 'Carrito (1)' }).click();
      await page.getByRole('button', { name: 'Confirmar pedido' }).click();
      await expect(page.getByRole('alert')).toHaveText('No se pudo confirmar el pedido; intenta de nuevo');
      await expect(page.getByRole('link', { name: 'Carrito (1)' })).toBeVisible();   // no se perdió el carrito
      await expect(page.getByRole('status')).toHaveCount(0);
    });
  });
});

// 3. Accesibilidad automática en una página
test('3. el carrito no tiene violaciones de accesibilidad (axe)', async ({ page }) => {
  await agregarTeclado(page);
  await page.getByRole('link', { name: 'Carrito (1)' }).click();
  await expect(page.getByRole('heading', { name: 'Tu carrito' })).toBeVisible();
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
  await page.screenshot({ path: captura('n4-04-taller-carrito') });
});
