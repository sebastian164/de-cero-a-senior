import { captura, expect, test } from './base';

test.describe('Nivel 3', () => {
  test('n3-01 taller: elegir cursos y confirmar', async ({ page }) => {
    await page.goto('/n3-01/taller');
    await page.getByRole('button', { name: 'Elegir Angular desde cero' }).click();
    await page.getByRole('radio', { name: 'Backend' }).check();
    await page.getByRole('button', { name: 'Elegir Kafka práctico' }).click();
    await expect(page.getByRole('complementary', { name: 'Selección' })).toContainText('2 cursos');
    await page.getByRole('button', { name: 'Confirmar inscripción' }).click();
    await expect(page.getByRole('status')).toHaveText('Inscripción confirmada');
  });

  test('n3-02 taller: el país nuevo (Chile) se valida sin tocar el servicio', async ({ page }) => {
    await page.goto('/n3-02/taller');
    await page.getByLabel('País').selectOption('CL');
    await page.getByLabel('Documento').fill('12.345.678-9');
    await page.getByRole('button', { name: 'Validar' }).click();
    await expect(page.getByRole('status')).toHaveText('El dígito verificador del RUT no coincide');
    await page.getByLabel('Documento').fill('12.345.678-5');
    await page.getByRole('button', { name: 'Validar' }).click();
    await expect(page.getByRole('status')).toHaveText('Documento válido');
  });

  test.describe('con un 503 provocado', () => {
    test.use({ erroresEsperados: [/HTTP 503 \/api\/n3-03\/carrito/] });
    test('n3-03 taller: dos vistas del mismo almacén, reversa ante fallo y filtro en la URL', async ({ page }) => {
      await page.goto('/n3-03/taller');
      await page.getByRole('button', { name: 'Agregar Teclado' }).click();
      await expect(page.getByTestId('contador')).toHaveText('1');
      await expect(page.getByRole('heading', { name: 'Tu carrito (1)' })).toBeVisible();
      await page.getByLabel('Simular fallo del servidor').check();
      await page.getByRole('button', { name: 'Agregar Mouse' }).click();
      await expect(page.getByRole('alert')).toHaveText('No se pudo guardar el carrito; se deshizo el cambio');
      await expect(page.getByTestId('contador')).toHaveText('1');
      await page.getByRole('link', { name: 'Monitores' }).click();
      await expect(page).toHaveURL(/categoria=monitores/);
      await page.reload();
      await expect(page.getByRole('button', { name: /^Agregar/ })).toHaveText(['Agregar Pantalla']);
      await page.goBack();
      await expect(page.getByRole('button', { name: /^Agregar/ })).toHaveCount(3);
    });
  });

  test.describe('tablero con un 500 en la parte opcional', () => {
    test.use({ erroresEsperados: [/HTTP 500 \/api\/n3-04\/tablero\/recomendaciones|HTTP 503 \/api\/n3-04\/documentos/] });
    test('n3-04 taller: exhaustMap envía una vez, el tablero se muestra sin la parte opcional', async ({ page, request }) => {
      await page.goto('/n3-04/taller');
      await expect(page.getByText('Ventas: 1200000 · Clientes nuevos: 14')).toBeVisible();
      await expect(page.getByText('Recomendaciones no disponibles')).toBeVisible();
      await page.getByLabel('Correo').fill('ana@ejemplo.co');
      await page.getByLabel('Mensaje').fill('Hola');
      const enviar = page.getByRole('button', { name: 'Enviar mensaje' });
      await enviar.click(); await enviar.click(); await enviar.click();
      await expect(page.getByTestId('envios')).toHaveText('Respuestas: 1');
      expect((await (await request.get('/api/n3-04/_contadores')).json()).envios).toBe(1);
      await page.getByLabel('Título').fill('Borrador');
      await expect(page.getByTestId('guardado')).toHaveText(/guardado|error/, { timeout: 8000 });
      await page.screenshot({ path: captura('n3-04-taller-rxjs') });
    });
  });

  test.describe('n3-04: errores dentro del flujo interno', () => {
    test.use({ erroresEsperados: [/HTTP 500 \/api\/n3-04\/(contacto|estado|tablero\/recomendaciones)/] });
    test('un envío fallido no mata el botón y una consulta fallida no detiene el sondeo', async ({ page, request }) => {
      await request.post('/api/n3-04/_fallar', { data: { contacto: true, estado: true } });
      await page.goto('/n3-04/taller');
      await expect(page.getByTestId('sondeo-ultima')).toHaveText('Última consulta: error');
      await page.getByLabel('Correo').fill('ana@ejemplo.co');
      await page.getByLabel('Mensaje').fill('Hola');
      const enviar = page.getByRole('button', { name: 'Enviar mensaje' });
      await enviar.click();
      await expect(page.getByRole('alert').filter({ hasText: 'No se pudo enviar' })).toBeVisible();
      await page.screenshot({ path: captura('n3-04-taller-error-de-envio') });
      // El servidor se recupera: el mismo botón envía y el sondeo, 10 s después, cuenta su primera consulta buena.
      await request.post('/api/n3-04/_fallar', { data: {} });
      await enviar.click();
      await expect(page.getByTestId('estado-envio')).toHaveText('Mensaje enviado');
      await expect(page.getByTestId('sondeo')).toHaveText('Consultas: 1', { timeout: 15000 });
      await expect(page.getByTestId('sondeo-ultima')).toHaveText('Última consulta: ok');
    });
  });

  test.describe('renovación del token', () => {
    test.use({ erroresEsperados: [/HTTP 401 \/api\/n3-05\/privado\/\d/] });
    test('n3-05 taller: tres 401 simultáneos, una sola renovación', async ({ page }) => {
      await page.goto('/n3-05/taller');
      await expect(page.getByText('Leanne Graham (Gwenborough, Romaguera-Crona): 2 publicaciones')).toBeVisible();
      await page.getByRole('link', { name: 'Ingresar' }).click();
      await page.getByRole('button', { name: 'Ingresar como cliente' }).click();
      await page.getByRole('button', { name: 'Tres peticiones con el token vencido' }).click();
      await expect(page.getByRole('status')).toHaveText('Respuestas: 1, 2, 3 · renovaciones: 1');
    });
  });

  test.describe('la guarda no protege los datos', () => {
    test.use({ erroresEsperados: [/HTTP 401 \/api\/n3-05\/admin\/datos-protegidos/] });
    test('n3-05 taller: la guarda redirige, pero el dato se obtiene con fetch desde la consola', async ({ page }) => {
      await page.goto('/n3-05/taller/admin');
      await expect(page).toHaveURL(/\/n3-05\/taller\/ingreso\?volverA=/);
      const robado = await page.evaluate(async () => (await fetch('/api/n3-05/admin/datos')).json());
      expect(robado).toEqual({ sueldos: [4200000, 5100000] });
      const protegido = await page.evaluate(async () => (await fetch('/api/n3-05/admin/datos-protegidos')).status);
      expect(protegido).toBe(401);
      await page.getByRole('button', { name: 'Ingresar como cliente' }).click();
      await page.goto('/n3-05/taller/admin');
      await expect(page).toHaveURL(/\/n3-05\/taller\/ingreso/);   // la sesión vive en memoria: recargar la pierde
    });
  });

  test('n3-05 ejemplo: resolver, guarda de rol y aviso de 5xx', async ({ page }) => {
    await page.goto('/n3-05/ejemplo');
    await page.getByRole('link', { name: 'Luis Pérez' }).click();
    await expect(page.getByRole('heading', { name: 'Pedido 2' })).toBeVisible();
    await page.getByRole('link', { name: 'Volver' }).click();
    await page.getByRole('link', { name: 'Administración' }).click();
    await expect(page).toHaveURL(/\/ingreso\?volverA=%2Fn3-05%2Fejemplo%2Fadmin/);
    await page.getByRole('button', { name: 'Ingresar como cliente' }).click();   // vuelve a /admin, pero sin rol
    await expect(page.getByRole('heading', { name: 'Sin permiso' })).toBeVisible();
  });

  test.describe('reporte que falla', () => {
    test.use({ erroresEsperados: [/HTTP 503 \/api\/n3-05\/reportes/] });
    test('n3-05 ejemplo: el interceptor de errores avisa', async ({ page }) => {
      await page.goto('/n3-05/ejemplo');
      await page.getByRole('button', { name: 'Pedir reporte' }).click();
      await expect(page.getByRole('alert')).toHaveText('El servicio no está disponible. Intenta más tarde.');
    });
  });

  test('n3-06 taller: autofoco, diálogo con clic fuera y lista con estados', async ({ page }) => {
    await page.goto('/n3-06/taller');
    await expect(page.getByLabel('Buscar')).toBeFocused();
    await page.getByRole('button', { name: 'Dentro del menú' }).click();
    await page.getByRole('heading', { name: 'Composición' }).click();
    await expect(page.getByTestId('clics-fuera')).toHaveText('Clics fuera del menú: 1');
    await page.getByRole('button', { name: 'Eliminar cuenta' }).click();
    await expect(page.getByRole('dialog', { name: '¿Eliminar la cuenta?' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cerrar' })).toBeFocused();
    await page.screenshot({ path: captura('n3-06-taller-dialogo') });
    await page.getByRole('heading', { name: 'Composición' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('button', { name: 'Cargar pedidos' }).click();
    await expect(page.getByText('Buscando pedidos…')).toBeVisible();
    await expect(page.getByText(/Luis — \$\s80\.000/)).toBeVisible();
  });

  test('n3-06 ejemplo: la directiva de permiso reacciona', async ({ page }) => {
    await page.goto('/n3-06/ejemplo');
    await expect(page.getByRole('button', { name: 'Cancelar pedido' })).toHaveCount(0);
    await page.getByRole('button', { name: 'Dar permiso de cancelar' }).click();
    await expect(page.getByRole('button', { name: 'Cancelar pedido' })).toBeVisible();
    await page.getByRole('link', { name: 'Ver pedido 42' }).click();
    await expect(page.getByRole('heading', { name: 'Pedido 42' })).toBeVisible();
  });
});
