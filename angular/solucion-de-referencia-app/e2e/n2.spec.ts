import { captura, expect, test } from './base';

test.describe('Nivel 2', () => {
  test('n2-01 taller: la pantalla refactorizada muestra la factura y envía el correo', async ({ page }) => {
    await page.goto('/n2-01/taller');
    await expect(page.getByText(/Total:\s*\$\s*1\.253\.070/)).toBeVisible();
    await page.getByLabel('Correo').fill('compras@tornillo.co');
    await page.getByRole('button', { name: 'Enviar' }).click();
    await expect(page.getByRole('status')).toHaveText('Enviada a compras@tornillo.co');
    await page.getByLabel('Cupón').fill('BIENVENIDA');
    await expect(page.getByText(/Cupón aplicado: \$\s*20\.000/)).toBeVisible();
    const descarga = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Exportar CSV' }).click();
    expect((await descarga).suggestedFilename()).toBe('factura-F-2.csv');
  });

  test('n2-02 taller: tablas genéricas y cuatro tipos de notificación', async ({ page }) => {
    await page.goto('/n2-02/taller');
    await expect(page.getByRole('table')).toHaveCount(2);
    await expect(page.getByText('Tu pedido salió con Servientrega (guía 123)')).toBeVisible();
    await expect(page.getByRole('alert')).toHaveText('Stock bajo');
  });

  test('n2-03 taller: estrellas deshabilitadas por el formulario y botón primario por teclado', async ({ page }) => {
    await page.goto('/n2-03/taller');
    await page.getByRole('radio', { name: '5 estrellas' }).click();
    await expect(page.getByText('Valor: 5 · habilitado')).toBeVisible();
    await page.getByRole('button', { name: 'Alternar habilitado' }).click();
    await expect(page.getByRole('radio', { name: '1 estrella' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Primario deshabilitado' })).toBeDisabled();
    await page.getByRole('button', { name: 'Enviar calificación' }).focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('list', { name: 'Avisos' })).toContainText('Calificación 5 enviada');
  });

  test('n2-05 taller: implementación por ruta (localStorage frente a memoria)', async ({ page }) => {
    await page.goto('/n2-05/taller');
    await expect(page.getByText('Registro de avisos activo en esta ruta: localStorage')).toBeVisible();
    await page.getByRole('button', { name: 'Programar cita de mañana' }).click();
    await expect(page.getByRole('status')).toHaveText('avisada');
    await page.reload();
    await page.getByRole('button', { name: 'Programar cita de mañana' }).click();
    await expect(page.getByRole('status')).toHaveText('pendiente');      // recordó el aviso
    await page.goto('/n2-05/taller/demo');
    await expect(page.getByText('Registro de avisos activo en esta ruta: memoria')).toBeVisible();
    await page.getByRole('button', { name: 'Programar cita de mañana' }).click();
    await expect(page.getByRole('status')).toHaveText('avisada');        // la memoria empieza vacía
    await page.screenshot({ path: captura('n2-05-taller-demo') });
  });
});
