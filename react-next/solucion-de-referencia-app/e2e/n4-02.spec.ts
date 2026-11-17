// Taller nivel-04/02, punto 2: las versiones con acción de servidor funcionan SIN JavaScript (mejora progresiva).
// El contexto desactiva JavaScript: todo lo que ocurre es HTML + envío nativo del formulario + la acción de servidor.
import { expect, test, type Browser } from '@playwright/test';

async function sinJavaScript(browser: Browser) {
  const contexto = await browser.newContext({ javaScriptEnabled: false, baseURL: test.info().project.use.baseURL });
  return { contexto, page: await contexto.newPage() };
}

test('sin JavaScript el HTML del servidor trae los formularios con acción de servidor', async ({ request }) => {
  const html = await (await request.get('/n4/02-formularios')).text();
  expect(html).toContain('aria-label="Registro de paciente (acción de servidor)"');
  const conAccion = html.match(/<form[^>]*method="POST"[^>]*>/g) ?? [];
  expect(conAccion.length, 'formularios con method=POST').toBeGreaterThanOrEqual(2);
});

test('(a) "Agregar contacto" devuelve una fila más y conserva lo escrito', async ({ browser }) => {
  const { contexto, page } = await sinJavaScript(browser);
  await page.goto('/n4/02-formularios');
  const ficha = page.locator('section', { hasText: 'Taller: ficha de clínica con acción de servidor' });
  await expect(ficha.getByLabel(/Contacto \d: nombre/)).toHaveCount(1);
  await ficha.getByLabel('Nombre completo').fill('Marta Gómez');
  await ficha.getByLabel('Contacto 1: nombre').fill('Luis Gómez');
  await ficha.getByRole('button', { name: 'Agregar contacto' }).click();
  await page.waitForLoadState('load');
  const despues = page.locator('section', { hasText: 'Taller: ficha de clínica con acción de servidor' });
  await expect(despues.getByLabel(/Contacto \d: nombre/)).toHaveCount(2);
  await expect(despues.getByLabel('Nombre completo')).toHaveValue('Marta Gómez');
  await expect(despues.getByLabel('Contacto 1: nombre')).toHaveValue('Luis Gómez');
  await contexto.close();
});

test('(b) enviar vacío muestra los errores por campo', async ({ browser }) => {
  const { contexto, page } = await sinJavaScript(browser);
  await page.goto('/n4/02-formularios');
  const ficha = page.locator('section', { hasText: 'Taller: ficha de clínica con acción de servidor' });
  await ficha.getByRole('button', { name: 'Registrar paciente' }).click();
  await page.waitForLoadState('load');
  const despues = page.locator('section', { hasText: 'Taller: ficha de clínica con acción de servidor' });
  await expect(despues.getByText('Escribe el nombre completo')).toBeVisible();
  await expect(despues.getByText('Entre 6 y 10 dígitos')).toBeVisible();
  await expect(despues.getByText('Escribe un correo válido')).toBeVisible();
  await expect(despues.getByText('Debes autorizar el tratamiento de datos')).toBeVisible();
  await contexto.close();
});

test('(c) un registro válido termina en "Paciente registrado"', async ({ browser }) => {
  const { contexto, page } = await sinJavaScript(browser);
  await page.goto('/n4/02-formularios');
  const ficha = page.locator('section', { hasText: 'Taller: ficha de clínica con acción de servidor' });
  const documento = String(Math.floor(2_000_000_000 + Math.random() * 900_000_000));
  await ficha.getByLabel('Nombre completo').fill('Marta Gómez');
  await ficha.getByLabel('Documento').fill(documento);
  await ficha.getByLabel('Fecha de nacimiento').fill('1985-04-12');
  await ficha.getByLabel('Correo').fill('marta@correo.example');
  await ficha.getByLabel('Celular', { exact: true }).fill('3001234567');
  await ficha.getByLabel('Contacto 1: nombre').fill('Luis Gómez');
  await ficha.getByLabel('Contacto 1: celular').fill('3109876543');
  await ficha.getByLabel('Contacto 1: parentesco').selectOption('pareja');
  await ficha.getByLabel('Autorizo el tratamiento de mis datos').check();
  await ficha.getByRole('button', { name: 'Registrar paciente' }).click();
  await page.waitForLoadState('load');
  await expect(page.getByText('Paciente registrado: Marta Gómez')).toBeVisible();
  await contexto.close();
});

test('FormularioRegistro del README sin JavaScript: errores por campo y valores devueltos', async ({ browser }) => {
  const { contexto, page } = await sinJavaScript(browser);
  await page.goto('/n4/02-formularios');
  const registro = page.locator('section', { hasText: 'registro con acción de servidor (valida al enviar)' });
  await registro.getByLabel('Correo').fill('no-es-correo');
  await registro.getByLabel('Documento').fill('1012345');
  await registro.getByRole('button', { name: 'Registrarme' }).click();
  await page.waitForLoadState('load');
  const despues = page.locator('section', { hasText: 'registro con acción de servidor (valida al enviar)' });
  await expect(despues.getByRole('alert').first()).toBeVisible();
  expect(await despues.getByRole('alert').count()).toBeGreaterThanOrEqual(2);   // correo inválido y claves faltantes
  await expect(despues.getByLabel('Correo')).toHaveValue('no-es-correo');       // valores devueltos por la acción
  await expect(despues.getByLabel('Documento')).toHaveValue('1012345');
  await contexto.close();
});
