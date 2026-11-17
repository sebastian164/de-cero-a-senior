import { expect, test } from '../../../../e2e/base';

// La prueba del README, con una sola diferencia: la tienda cuelga de /n4-04/ejemplo (no de /).
test('un cliente puede hacer un pedido', async ({ page }) => {
  await page.goto('/n4-04/ejemplo/catalogo');
  await page.getByRole('button', { name: 'Agregar Teclado al carrito' }).click();
  await page.getByRole('link', { name: 'Carrito' }).click();

  await expect(page.getByRole('heading', { name: 'Tu carrito' })).toBeVisible();
  await expect(page.getByText('Total: $ 120.000')).toBeVisible();

  await page.getByRole('button', { name: 'Confirmar pedido' }).click();
  await expect(page.getByRole('status')).toHaveText(/pedido confirmado/i);
});
