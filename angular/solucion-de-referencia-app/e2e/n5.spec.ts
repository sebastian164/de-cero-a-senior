import { captura, expect, test } from './base';

test.describe('Nivel 5', () => {
  test('n5-01 ejemplo: rutas perezosas, almacén por funcionalidad y canMatch', async ({ page }) => {
    await page.goto('/n5-01/ejemplo');
    await page.getByRole('link', { name: 'Pedidos' }).click();
    const almacen = await page.getByText(/^almacén /).textContent();
    await page.getByRole('link', { name: 'Pedido 42' }).click();
    await expect(page.getByRole('heading', { name: 'Pedido 42' })).toBeVisible();
    expect(await page.getByText(/^almacén /).textContent()).toBe(almacen);
    await page.goto('/n5-01/ejemplo/admin');                                        // sin rol: vuelve al inicio
    await expect(page.getByRole('heading', { name: 'Estructura por funcionalidades (README)' })).toBeVisible();
  });

  test('n5-01 taller: cada funcionalidad descarga su código al navegar; @defer descarga la gráfica aparte', async ({ page }) => {
    const scripts: string[] = [];
    page.on('request', (r) => { if (r.url().endsWith('.js')) scripts.push(r.url().split('/').pop()!); });
    await page.goto('/n5-01/taller');
    await expect(page.getByRole('heading', { name: 'Tienda por funcionalidades' })).toBeVisible();
    await page.waitForLoadState('networkidle');
    const alInicio = scripts.length;
    await page.getByRole('link', { name: 'Catálogo' }).click();
    await expect(page.getByText('Teclado: $ 119.000 con IVA')).toBeVisible();
    await page.waitForLoadState('networkidle');
    const trasCatalogo = scripts.length;
    expect(trasCatalogo).toBeGreaterThan(alInicio);                                 // el catálogo se descargó al entrar
    await page.goBack();
    await page.getByRole('link', { name: 'Administración' }).click();               // sin rol: canMatch redirige y NO descarga admin
    await expect(page.getByRole('alert')).toHaveText('Necesitas el rol de administración.');
    await page.waitForLoadState('networkidle');
    expect(scripts.length).toBe(trasCatalogo);
    await page.goto('/n5-01/taller');
    await page.getByRole('button', { name: 'Entrar como admin' }).click();
    const antesAdmin = scripts.length;
    await page.getByRole('link', { name: 'Administración' }).click();
    await expect(page.getByRole('heading', { name: 'Administración' })).toBeVisible();
    await expect(page.getByText('6000 puntos')).toBeVisible();                       // la gráfica diferida llegó a la vista y se cargó
    expect(scripts.length).toBeGreaterThanOrEqual(antesAdmin + 2);                  // el código de admin y el de la gráfica
    await page.screenshot({ path: captura('n5-01-taller-admin'), fullPage: true });
  });

  test('n5-01 taller: pedidos comparte un almacén entre lista y detalle', async ({ page }) => {
    await page.goto('/n5-01/taller/pedidos');
    await expect(page.getByRole('link', { name: 'Pedido p1' })).toBeVisible();
    const almacen = await page.locator('code').textContent();
    await page.getByRole('link', { name: 'Pedido p2' }).click();
    await expect(page.getByRole('heading', { name: 'Pedido p2' })).toBeVisible();
    await expect(page.locator('code')).toHaveText(almacen!);
    await expect(page.getByText('1 líneas')).toBeVisible();
  });
});

test.describe('Nivel 5 (02)', () => {
  test('n5-02 ejemplo: filtro y página viajan en la URL, sobreviven a recargar y el atrás los deshace', async ({ page }) => {
    await page.goto('/n5-02/ejemplo');
    await expect(page.getByText('Filtro: todos · página 1')).toBeVisible();
    await page.getByRole('button', { name: 'Pagados' }).click();
    await expect(page).toHaveURL(/estado=pagado/);
    await expect(page.getByText('Filtro: pagado · página 1')).toBeVisible();
    await page.reload();
    await expect(page.getByText('Filtro: pagado · página 1')).toBeVisible();
    await page.goBack();
    await expect(page.getByText('Filtro: todos · página 1')).toBeVisible();
    await page.goto('/n5-02/ejemplo/carrito');
    await page.getByRole('button', { name: 'Agregar línea' }).click();
    await expect(page.getByText('Servicio: 2 unidades, total 200')).toBeVisible();
    await expect(page.getByText('SignalStore: 2 unidades, total 200')).toBeVisible();
  });

  test('n5-02 taller: página y pestaña en la URL, carrito con servicio y store, caché con una lectura y revalidación tras mutar', async ({ page, request }) => {
    await page.goto('/n5-02/taller?estado=todos&pagina=2&pestana=resumen');
    await expect(page.getByText('Resumen: 5 pedidos en esta página')).toBeVisible();
    await page.getByRole('link', { name: 'Lista' }).click();
    await expect(page).toHaveURL(/pestana=lista/);
    await expect(page).toHaveURL(/estado=todos/);
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await expect(page.getByText('Página 3')).toBeVisible();
    await expect(page).toHaveURL(/pagina=3/);

    await page.getByRole('button', { name: 'Agregar teclado' }).click();
    await page.getByRole('button', { name: 'Agregar teclado' }).click();
    await expect(page.locator('#serv')).toHaveText('2 / 240000');
    await expect(page.locator('#store')).toHaveText('2 / 240000');

    // Dos listas leen la clave "productos": una sola petición al servidor.
    await expect(page.getByRole('list', { name: 'Productos A' }).getByRole('listitem')).toHaveCount(2);
    await expect(page.getByRole('list', { name: 'Productos B' }).getByRole('listitem')).toHaveCount(2);
    expect((await (await request.get('/api/n5-02/lecturas')).json()).lecturas).toBe(1);
    // Crear un producto invalida y revalida: las dos listas se actualizan con una lectura más.
    await page.getByRole('button', { name: 'Crear producto' }).click();
    await expect(page.getByRole('list', { name: 'Productos A' }).getByRole('listitem')).toHaveCount(3);
    await expect(page.getByRole('list', { name: 'Productos B' }).getByRole('listitem')).toHaveCount(3);
    expect((await (await request.get('/api/n5-02/lecturas')).json()).lecturas).toBe(2);
    await page.screenshot({ path: captura('n5-02-taller'), fullPage: true });
  });
});

test.describe('Nivel 5 (03)', () => {
  test('n5-03 ejemplo: el botón sale solo si la regla de las 24 horas lo permite', async ({ page }) => {
    await page.goto('/n5-03/ejemplo');
    await expect(page.getByRole('button', { name: /^Cancelar pedido/ })).toHaveText(['Cancelar pedido 1']);
    await expect(page.getByText('no cancelable')).toHaveCount(2);
    await page.getByRole('button', { name: 'Cancelar pedido 1' }).click();
    await expect(page.getByRole('status')).toHaveText('Pedido 1 cancelado');
    await expect(page.getByRole('button', { name: /^Cancelar pedido/ })).toHaveCount(0);
  });

  test.describe('con un 422 provocado', () => {
  test.use({ erroresEsperados: [/HTTP 422 \/api\/n5-03\/solicitudes/] });
  test('n5-03 taller: asistente con pasos condicionales y el servidor que vuelve a validar', async ({ page }) => {
    await page.goto('/n5-03/taller');
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await expect(page.getByRole('alert')).toContainText('Escribe tu nombre completo');
    await page.getByLabel('Nombre completo').fill('Ana Gómez');
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByLabel('Ingreso mensual').fill('500000000');
    await page.getByLabel('Monto').fill('60000000');
    await expect(page.getByText('Cuota estimada')).toBeVisible();
    await expect(page.getByRole('list', { name: 'Pasos' }).getByRole('listitem')).toHaveText(['personales', 'ingresos', 'codeudor', 'revision']);   // monto > 20 M: aparece el paso
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByLabel('Nombre del codeudor').fill('Luis Pérez');
    await page.getByLabel('Documento del codeudor').fill('1234567');
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByRole('button', { name: 'Enviar solicitud' }).click();
    // El cliente lo dejó pasar; el servidor lo rechaza (máximo 50 M) y el error aparece en el paso de ingresos.
    await expect(page.getByRole('alert')).toContainText('El monto máximo es 50.000.000');
    await expect(page.getByLabel('Monto')).toBeVisible();
    await page.getByLabel('Monto').fill('30000000');
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByRole('button', { name: 'Enviar solicitud' }).click();
    await expect(page.getByRole('status')).toHaveText('Solicitud enviada (1)');
    await page.screenshot({ path: captura('n5-03-taller-enviada') });
  });
  });

  test.describe('con un 500 provocado', () => {
    test.use({ erroresEsperados: [/HTTP 500 \/api\/n5-03\/solicitudes/] });
    test('n5-03 taller: un 500 no se pierde: se avisa y se puede reintentar', async ({ page }) => {
      await page.goto('/n5-03/taller');
      await page.getByLabel('Nombre completo').fill('Error500');
      await page.getByRole('button', { name: 'Siguiente' }).click();
      await page.getByLabel('Ingreso mensual').fill('5000000');
      await page.getByLabel('Monto').fill('10000000');
      await page.getByRole('button', { name: 'Siguiente' }).click();
      await page.getByRole('button', { name: 'Enviar solicitud' }).click();
      await expect(page.getByTestId('error-general')).toContainText('No se pudo enviar la solicitud');
      await expect(page.getByRole('button', { name: 'Enviar solicitud' })).toBeVisible();
      await page.screenshot({ path: captura('n5-03-taller-error-500') });
    });
  });

  test('n5-03 taller: por configuración (?datos=memoria) el adaptador cambia y no hay peticiones', async ({ page }) => {
    const posts: string[] = [];
    page.on('request', (r) => { if (r.method() === 'POST') posts.push(r.url()); });
    await page.goto('/n5-03/taller?datos=memoria');
    await page.getByLabel('Nombre completo').fill('Ana Gómez');
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByLabel('Ingreso mensual').fill('5000000');
    await page.getByLabel('Monto').fill('10000000');
    await page.getByRole('button', { name: 'Siguiente' }).click();
    await page.getByRole('button', { name: 'Enviar solicitud' }).click();
    await expect(page.getByRole('status')).toHaveText('Solicitud enviada (-1)');
    expect(posts).toEqual([]);
  });
});

test.describe('Nivel 5 (04)', () => {
  test.describe('errores esperados', () => {
    test.use({ erroresEsperados: [/HTTP (404|500|503) \/api\/n5-04\/pedidos/] });
    test('n5-04 ejemplo: 404, 500 y reintentos con el servidor simulado; las peticiones usan Fetch', async ({ page, request }) => {
      const tipos = new Set<string>();
      page.on('request', (r) => { if (r.url().includes('/api/n5-04/')) tipos.add(r.resourceType()); });
      await page.goto('/n5-04/ejemplo');
      await page.getByRole('button', { name: 'Pedido 1', exact: true }).click();
      await expect(page.getByRole('status')).toHaveText('Ana Gómez: 238000');
      await page.getByRole('button', { name: 'Pedido 404' }).click();
      await expect(page.getByRole('alert')).toHaveText('Ese pedido no existe');
      expect((await (await request.get('/api/n5-04/intentos')).json()).intentos).toBe(0);
      await page.getByRole('button', { name: 'Pedido inestable' }).click();         // 503, 503 y 200: se reintenta (0,5 s y 1 s)
      await expect(page.getByRole('status')).toHaveText('Tras reintentos: 1');
      expect((await (await request.get('/api/n5-04/intentos')).json()).intentos).toBe(3);
      await page.getByRole('button', { name: 'Pedido 500' }).click();
      await expect(page.getByRole('alert')).toHaveText('No pudimos cargar el pedido', { timeout: 10_000 });
      expect([...tipos]).toEqual(['fetch']);                                         // Angular 22: HttpClient usa Fetch por defecto
    });

    test('n5-04 taller: los cuatro estados de la lista (cargando, éxito, vacío, error y reintento)', async ({ page }) => {
      await page.goto('/n5-04/taller');
      await expect(page.getByText('Cargando pedidos…')).toBeVisible();
      await expect(page.getByRole('link', { name: 'Ana Gómez' })).toBeVisible();
      await page.getByRole('button', { name: 'Simular vacío' }).click();
      await expect(page.getByText('Aún no tienes pedidos.')).toBeVisible();
      await page.getByRole('button', { name: 'Simular error' }).click();
      await expect(page.getByRole('alert')).toContainText('No pudimos completar la operación');
      await page.getByRole('button', { name: 'Normal' }).click();
      await expect(page.getByRole('link', { name: 'Luis Pérez' })).toBeVisible();
    });

    test('n5-04 taller: sin conexión y red lenta (tiempo límite configurable) conservan lo escrito y se pueden reintentar', async ({ page }) => {
      await page.goto('/n5-04/taller/nuevo?limite=600');
      await page.getByLabel('Cliente').fill('Ana');
      await page.route('**/api/n5-04/pedidos', (ruta) => ruta.abort('internetdisconnected'));
      await page.getByRole('button', { name: 'Crear pedido' }).click();
      await expect(page.getByRole('alert').filter({ hasText: 'Sin conexión' })).toBeVisible();
      await expect(page.getByLabel('Cliente')).toHaveValue('Ana');                    // lo escrito se conserva
      await page.unroute('**/api/n5-04/pedidos');
      await page.route('**/api/n5-04/pedidos', async (ruta) => { await new Promise((r) => setTimeout(r, 1500)); await ruta.continue().catch(() => undefined); });
      await page.getByRole('button', { name: 'Crear pedido' }).click();                // red lenta: vence a los 600 ms
      await expect(page.getByRole('alert').filter({ hasText: 'tardó demasiado' })).toBeVisible();
      await page.unroute('**/api/n5-04/pedidos');
      await page.getByRole('button', { name: 'Crear pedido' }).click();                // red normal
      await expect(page.getByRole('status')).toHaveText('Pedido 3 creado');
    });
  });

  test.describe('422 al crear', () => {
  test.use({ erroresEsperados: [/HTTP 422 \/api\/n5-04\/pedidos/] });
  test('n5-04 taller: un 422 se muestra junto al campo', async ({ page }) => {
    await page.goto('/n5-04/taller/nuevo');
    await page.getByRole('button', { name: 'Crear pedido' }).click();
    await expect(page.locator('#cliente-error')).toHaveText('El cliente es obligatorio');
    await expect(page.getByLabel('Cliente')).toHaveAttribute('aria-invalid', 'true');
  });
  });

  test.describe('404 del detalle', () => {
    test.use({ erroresEsperados: [/HTTP 404 \/api\/n5-04\/pedidos\/404/] });
    test('n5-04 taller: el detalle traduce el 404 a "no existe"', async ({ page }) => {
      await page.goto('/n5-04/taller/pedido/404');
      await expect(page.getByRole('alert')).toHaveText('Ese pedido no existe.');
    });
  });

  test('n5-04 taller: la cascada tarda la suma de las esperas y el paralelo, menos', async ({ page }) => {
    await page.goto('/n5-04/taller/cascada');
    await page.getByRole('button', { name: 'Cargar en cascada' }).click();
    await expect(page.locator('.medicion').first()).toContainText('cascada:');
    await page.getByRole('button', { name: 'Cargar en paralelo' }).click();
    await expect(page.locator('.medicion')).toHaveCount(2);
    const ms = async (i: number) => Number(/(\d+) ms/.exec((await page.locator('.medicion').nth(i).textContent())!)![1]);
    const [cascada, paralelo] = [await ms(0), await ms(1)];
    expect(cascada).toBeGreaterThanOrEqual(850);          // 3 × 300 ms
    expect(paralelo).toBeLessThan(cascada - 200);         // 2 × 300 ms
    expect(paralelo).toBeGreaterThanOrEqual(550);
    await page.screenshot({ path: captura('n5-04-taller-cascada') });
  });
});
