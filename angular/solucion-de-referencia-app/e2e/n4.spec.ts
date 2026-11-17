import { captura, expect, test } from './base';

test.describe('Nivel 4', () => {
  test('n4-01 ejemplo: filtro, total derivado y estado de error', async ({ page }) => {
    await page.goto('/n4-01/ejemplo');
    await expect(page.getByText('Total: 1180000')).toBeVisible();
    await page.getByLabel('Filtro').fill('Teclado');
    await expect(page.getByText('Total: 280000')).toBeVisible();
  });

  test.describe('con un 500 provocado', () => {
    test.use({ erroresEsperados: [/HTTP 500 \/api\/n4-01\/pedidos\/99/] });
    test('n4-01 ejemplo: el pedido 99 muestra el estado de error', async ({ page }) => {
      await page.goto('/n4-01/ejemplo');
      await page.getByRole('button', { name: 'Pedido 99 (falla)' }).click();
      await expect(page.getByRole('alert')).toHaveText('No se pudo cargar el pedido');
      await page.getByRole('button', { name: 'Pedido 1', exact: true }).click();
      await expect(page.getByText('Total: 1180000')).toBeVisible();
    });
  });

  test('n4-01 taller: derivados sin cálculos en la plantilla y la intención de editar', async ({ page }) => {
    await page.goto('/n4-01/taller');
    await expect(page.getByText('Total con IVA: 1404200')).toBeVisible();
    await expect(page.getByText('Items activos: 3')).toBeVisible();
    await page.getByRole('button', { name: 'Editar pedido' }).click();
    await expect(page.getByRole('status')).toHaveText('Edición solicitada del pedido 1');
  });

  test('n4-02 ejemplo: errores al enviar vacío y registro correcto', async ({ page }) => {
    await page.goto('/n4-02/ejemplo');
    await page.getByRole('button', { name: 'Registrarme' }).click();
    await expect(page.locator('#correo-error')).toHaveText('Escribe tu correo');
    await page.getByLabel('Correo').fill('ana@correo.co');
    await expect(page.locator('#correo-error')).toHaveText('Ese correo ya está registrado');   // validateHttp
    await page.getByLabel('Correo').fill('nueva@correo.co');
    await expect(page.locator('#correo-error')).toHaveText('');
    await page.screenshot({ path: captura('n4-02-ejemplo-validado'), fullPage: true });
  });

  test('n4-02 taller: validaciones, acudiente del menor, comprobación con espera y errores del servidor', async ({ page, request }) => {
    await page.goto('/n4-02/taller');
    // El correo se escribe de golpe: una sola consulta al servidor (la espera de 400 ms funciona).
    await page.getByLabel('Correo', { exact: true }).pressSequentially('ana@correo.co');
    await page.getByLabel('Correo', { exact: true }).blur();
    await expect(page.locator('#correo-error')).toHaveText('Ese correo ya está registrado');
    expect((await (await request.get('/api/n4-02/consultas')).json()).consultas).toBe(1);
    // Enviar vacío: errores accesibles y foco en el primer campo con error.
    await page.getByRole('button', { name: 'Registrar paciente' }).click();
    await expect(page.locator('#nombre-error')).toHaveText('Escribe tu nombre');
    await expect(page.getByLabel('Nombre completo')).toBeFocused();
    await expect(page.getByLabel('Nombre completo')).toHaveAttribute('aria-invalid', 'true');
    // Documento: la regla cambia con el tipo.
    await page.getByLabel('Documento', { exact: true }).fill('12ab');
    await page.getByLabel('Documento', { exact: true }).blur();
    await expect(page.locator('#documento-error')).toHaveText('Entre 6 y 10 dígitos');
    await page.getByLabel('Tipo de documento').selectOption('pasaporte');
    await expect(page.locator('#documento-error')).toHaveText('Entre 6 y 9 letras o números');
    // Acudiente solo para menores (la fecha 2015 siempre es menor de edad en 2026-2032).
    await expect(page.getByLabel('Nombre del acudiente (menor de edad)')).toHaveCount(0);
    await page.getByLabel('Fecha de nacimiento').fill('2020-01-01');
    await expect(page.getByLabel('Nombre del acudiente (menor de edad)')).toBeVisible();
    await page.getByLabel('Fecha de nacimiento').fill('1990-05-05');
    await expect(page.getByLabel('Nombre del acudiente (menor de edad)')).toHaveCount(0);
    // Contactos: máximo tres.
    await page.getByRole('button', { name: 'Agregar contacto' }).click();
    await page.getByRole('button', { name: 'Agregar contacto' }).click();
    await expect(page.getByRole('group', { name: /Contacto de emergencia/ })).toHaveCount(3);
    await expect(page.getByRole('button', { name: 'Agregar contacto' })).toBeDisabled();
    await page.getByRole('button', { name: 'Quitar contacto 3' }).click();
    await page.getByRole('button', { name: 'Quitar contacto 2' }).click();
    await expect(page.getByRole('button', { name: 'Quitar contacto 1' })).toBeDisabled();
    await page.screenshot({ path: captura('n4-02-taller-errores'), fullPage: true });
  });

  test.describe('registro con un 422 del servidor', () => {
    test.use({ erroresEsperados: [/HTTP 422 \/api\/n4-02\/usuarios/] });
    test('n4-02 taller: el error del servidor cae en el campo del correo; luego se registra y la guarda deja salir', async ({ page, request }) => {
      page.on('dialog', (d) => d.accept());
      await page.goto('/n4-02/taller');
      const llenar = async (correo: string) => {
        await page.getByLabel('Nombre completo').fill('Ana Gómez');
        await page.getByLabel('Documento', { exact: true }).fill('1234567');
        await page.getByLabel('Correo', { exact: true }).fill(correo);
        await page.getByLabel('Clave', { exact: true }).fill('una clave larga 1');
        await page.getByLabel('Confirma la clave').fill('una clave larga 1');
        await page.getByLabel('Fecha de nacimiento').fill('1990-05-05');
        await page.getByLabel('Nombre del contacto 1').fill('Luis');
        await page.getByLabel('Teléfono del contacto 1').fill('3001234567');
      };
      await llenar('tomado@correo.co');
      await page.getByRole('button', { name: 'Registrar paciente' }).click();
      await expect(page.locator('#correo-error')).toHaveText('El servidor rechazó este correo');
      await expect(page.getByLabel('Correo', { exact: true })).toBeFocused();
      await expect(page.getByLabel('Nombre completo')).toHaveValue('Ana Gómez');   // no se pierde lo escrito
      await page.getByLabel('Correo', { exact: true }).fill('nueva.paciente@correo.co');
      await page.getByRole('button', { name: 'Registrar paciente' }).click();
      await expect(page.getByRole('status').filter({ hasText: 'Paciente registrado' })).toBeVisible();
      expect((await (await request.get('/api/n4-02/registros')).json()).registros).toBe(1);
    });
  });

  test('n4-02 taller: la guarda pide confirmación solo con cambios sin guardar', async ({ page }) => {
    const mensajes: string[] = [];
    let aceptar = false;
    page.on('dialog', async (d) => { mensajes.push(d.message()); if (aceptar) await d.accept(); else await d.dismiss(); });
    await page.goto('/n4-02/taller');
    await page.getByRole('link', { name: 'Salir sin registrar' }).click();
    await expect(page.getByRole('heading', { name: 'Salida' })).toBeVisible();      // sin cambios: sale sin preguntar
    expect(mensajes).toEqual([]);
    await page.getByRole('link', { name: 'Volver al registro' }).click();
    // Esperar al formulario ya renderizado: Playwright escribe en milisegundos y, si lo hace entre la creación del
    // componente y su primera detección de cambios, el campo (`FormField`) aún no tiene su entrada `field` (NG0950).
    await expect(page.getByLabel('Nombre completo')).toHaveAttribute('aria-invalid', 'false');   // la primera detección de cambios ya corrió
    await page.getByLabel('Nombre completo').fill('Ana');
    await page.getByRole('link', { name: 'Salir sin registrar' }).click();
    await expect.poll(() => mensajes.length).toBe(1);
    await expect(page.getByRole('heading', { name: 'Registro de paciente' })).toBeVisible();   // rechazó: se queda
    aceptar = true;
    await page.getByRole('link', { name: 'Salir sin registrar' }).click();
    await expect(page.getByRole('heading', { name: 'Salida' })).toBeVisible();
    expect(mensajes[0]).toContain('cambios sin guardar');
  });

  test('n4-03 ejemplo: la lista y el filtro', async ({ page }) => {
    await page.goto('/n4-03/ejemplo');
    await expect(page.getByRole('button', { name: /^Cancelar pedido de/ })).toHaveCount(3);
    await page.getByRole('button', { name: 'Solo pendientes' }).click();
    await expect(page.getByRole('button', { name: /^Cancelar pedido de/ })).toHaveText(['Cancelar pedido de Ana', 'Cancelar pedido de Marta']);
  });

  test.describe('con un 409 provocado', () => {
    test.use({ erroresEsperados: [/HTTP 409 \/api\/n4-03\/pedidos\/9/] });
    test('n4-03 taller: cancelación optimista, reversa ante el 409, buscador con espera y formulario', async ({ page }) => {
      await page.goto('/n4-03/taller');
      await page.getByRole('button', { name: 'Cancelar pedido de Ana' }).click();
      await expect(page.getByRole('button', { name: 'Cancelar pedido de Ana' })).toHaveCount(0);
      await page.getByRole('button', { name: 'Cancelar pedido de Marta' }).click();
      await expect(page.getByRole('alert')).toHaveText('No se pudo cancelar');                  // reversa
      await expect(page.getByRole('button', { name: 'Cancelar pedido de Marta' })).toBeVisible();
      await page.getByRole('searchbox').pressSequentially('lu');
      await expect(page.locator('#coincidencias')).toHaveText('Luis');
      await page.getByRole('button', { name: 'Enviar consulta' }).click();
      await expect(page.getByText('Escribe un correo válido')).toBeVisible();
      await page.getByLabel('Correo').fill('ana@correo.co');
      await page.getByLabel('Mensaje').fill('Quisiera pedir una cita');
      await page.getByRole('button', { name: 'Enviar consulta' }).click();
      await expect(page.getByRole('status')).toHaveText('Consulta enviada');
      await page.screenshot({ path: captura('n4-03-taller-final'), fullPage: true });
    });
  });
});
