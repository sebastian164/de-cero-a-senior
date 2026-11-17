import { captura, expect, test } from './base';

test.describe('Nivel 1', () => {
  test.describe.configure({ mode: 'serial' });
  test('n1-01 ejemplo: elimina un pedido contra el servidor', async ({ page }) => {
    await page.goto('/n1-01/ejemplo');
    await expect(page.getByRole('heading', { name: 'Ana' })).toBeVisible();
    await page.getByRole('button', { name: 'Eliminar' }).first().click();
    await expect(page.getByRole('heading', { name: 'Ana' })).toHaveCount(0);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Luis' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Ana' })).toHaveCount(0);   // el DELETE llegó al servidor
  });

  test('n1-01 taller: lista de tareas y ruta de detalle', async ({ page }) => {
    await page.goto('/n1-01/taller');
    await page.getByLabel('Nueva tarea').fill('Probar en el navegador');
    await page.getByRole('button', { name: 'Agregar' }).click();
    await expect(page.getByRole('status')).toHaveText('Pendientes: 2');
    await page.getByRole('checkbox', { name: 'Probar en el navegador' }).check();
    await expect(page.getByRole('status')).toHaveText('Pendientes: 1');
    await page.getByRole('link', { name: 'detalle' }).first().click();
    await expect(page).toHaveURL(/\/n1-01\/taller\/1$/);
    await expect(page.getByRole('heading', { name: 'Tarea 1' })).toBeVisible();
    await page.screenshot({ path: captura('n1-01-taller-detalle') });
  });

  test('n1-02 ejemplo: el buscador muestra resultados del servidor', async ({ page }) => {
    await page.goto('/n1-02/ejemplo');
    await page.getByLabel('Buscar').fill('tec');
    await expect(page.getByText('1 resultados')).toBeVisible();
    await expect(page.getByText('Teclado')).toBeVisible();
  });

  for (const [operador, final] of [['switchMap', 'tecl'], ['mergeMap', 'te']] as const) {
    test(`n1-02 taller: carrera con ${operador} termina mostrando '${final}'`, async ({ page }) => {
      await page.goto('/n1-02/taller');
      await page.getByLabel(operador).check();
      await page.getByRole('button', { name: 'Simular carrera' }).click();
      await page.waitForTimeout(2500);   // deja llegar también la respuesta lenta de 'te' (1200 ms)
      await expect(page.getByTestId('consulta')).toHaveText(`Resultados para: ${final}`);
      await page.screenshot({ path: captura(`n1-02-taller-${operador}`) });
    });
  }

  test('n1-02 taller: exhaustMap evita el doble envío y el carrito persiste al recargar', async ({ page }) => {
    await page.goto('/n1-02/taller');
    await page.getByLabel('Buscar').fill('tec');
    await page.getByRole('button', { name: 'Agregar Teclado' }).click();
    await expect(page.getByRole('heading', { name: 'Carrito (1)' })).toBeVisible();
    const guardar = page.getByRole('button', { name: 'Guardar' });
    await guardar.click(); await guardar.click(); await guardar.click();
    await expect(page.getByRole('status')).toHaveText('Guardados: 1 · Peticiones de guardado: 1');
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Carrito (1)' })).toBeVisible();
  });

  test('n1-03 taller: ir a la primera lleva el foco al primer elemento', async ({ page }) => {
    await page.goto('/n1-03/taller');
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await expect(page.getByText('Página 3 de 7')).toBeVisible();
    await page.getByRole('button', { name: 'Ir a la primera' }).click();
    await expect(page.getByText('Elemento 1', { exact: true })).toBeFocused();
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByLabel('Total de elementos').fill('6');
    await page.getByLabel('Total de elementos').press('Tab');
    await expect(page.getByText('Página 2 de 2')).toBeVisible();
    await expect(page.getByText('Página en el padre: 2')).toBeVisible();
  });

  test('n1-04 taller: cambiar de un pedido lento a uno rápido no deja carrera', async ({ page }) => {
    await page.goto('/n1-04/taller/pedidos/1');
    await expect(page.getByText('Cargando pedido 1…')).toBeVisible();
    await page.getByRole('link', { name: 'Pedido 2 (rápido)' }).click();
    await expect(page.getByRole('heading', { name: 'Pedido 2: Luis Pérez' })).toBeVisible();
    await page.waitForTimeout(2000);   // más que los 1500 ms del pedido 1
    await expect(page.getByRole('heading', { name: 'Pedido 2: Luis Pérez' })).toBeVisible();
    await page.screenshot({ path: captura('n1-04-taller-detalle') });
  });

  test('n1-04 taller: eliminar recarga la lista y el servicio compartido hace una petición', async ({ page }) => {
    const peticiones: string[] = [];
    page.on('request', (r) => { if (r.url().endsWith('/api/n1-04/pedidos')) peticiones.push(r.method()); });
    await page.goto('/n1-04/taller');
    await expect(page.getByText('Total de pedidos: 3')).toBeVisible();
    await page.getByRole('button', { name: 'Eliminar Ana Gómez' }).click();
    await expect(page.getByRole('button', { name: 'Eliminar Ana Gómez' })).toHaveCount(0);
    // 1 GET de la lista, 1 GET del servicio compartido (los dos componentes), 1 GET de la recarga.
    expect(peticiones.filter((m) => m === 'GET')).toHaveLength(3);
  });

  test.describe('eliminar con un 500 provocado', () => {
    test.use({ erroresEsperados: [/HTTP 500 \/api\/n1-04\/pedidos\/\d+/] });
    test('n1-04 taller: si eliminar falla se avisa y el pedido sigue en la lista', async ({ page }) => {
      await page.route('**/api/n1-04/pedidos/*', (ruta) => ruta.request().method() === 'DELETE' ? ruta.fulfill({ status: 500, body: '{}' }) : ruta.continue());
      await page.goto('/n1-04/taller');
      await page.getByRole('button', { name: 'Eliminar Ana Gómez' }).click();
      await expect(page.getByRole('alert')).toContainText('No se pudo eliminar el pedido');
      await expect(page.getByRole('button', { name: 'Eliminar Ana Gómez' })).toBeVisible();
    });
  });

  test.describe('con un 503 provocado', () => {
  test.use({ erroresEsperados: [/HTTP 503 \/api\/n1-04\/pedidos/] });
  test('n1-04 taller: estado de error forzado por la red y reintento', async ({ page }) => {
    let fallar = true;
    await page.route('**/api/n1-04/pedidos', (ruta) => fallar ? ruta.fulfill({ status: 503, body: '{}' }) : ruta.continue());
    await page.goto('/n1-04/taller');
    await expect(page.getByText('No se pudieron cargar los pedidos.')).toBeVisible();
    fallar = false;
    await page.getByRole('button', { name: 'Reintentar' }).click();
    await expect(page.getByRole('link', { name: 'Ana Gómez' })).toBeVisible();
  });
});
});
