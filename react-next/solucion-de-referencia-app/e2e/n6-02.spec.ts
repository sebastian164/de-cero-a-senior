// Taller nivel-06/02, puntos 1, 3, 4, 6, 7 y 8: lo que solo se ve con la aplicación de producción y un navegador.
import { expect, test, type Page } from '@playwright/test';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const ANA = '0b7c6a2e-1d3f-4e5a-9b8c-7d6e5f4a3b21';
const ANA_2 = '1c8d7b3f-2e4a-4f6b-8c9d-8e7f6a5b4c32';
const LUIS = '2d9e8c4a-3f5b-4a7c-9d0e-9f8a7b6c5d43';

test.beforeEach(async ({ request }) => { await request.post('/api/n6-02/reiniciar'); });

async function ingresar(page: Page, correo: string, clave: string, cabeceras: Record<string, string> = {}) {
  await page.context().setExtraHTTPHeaders(cabeceras);
  await page.goto('/n6/02-seguridad', { waitUntil: 'networkidle' });
  await page.getByLabel('Correo').fill(correo);
  await page.getByLabel('Clave').fill(clave);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}
const entrarComo = async (page: Page, quien: 'ana' | 'luis', ip: string) => {
  await ingresar(page, `${quien}@correo.example`, `clave-de-${quien}-123`, { 'x-forwarded-for': ip });
  await expect(page).toHaveURL(/\/privado$/);
};

/** Captura la petición HTTP de una acción de servidor que dispara un clic, para repetirla a mano con otro argumento. */
async function capturarAccion(page: Page, boton: string) {
  const [peticion] = await Promise.all([
    page.waitForRequest((r) => r.method() === 'POST' && !!r.headers()['next-action']),
    page.getByRole('button', { name: boton }).click(),
  ]);
  return { id: peticion.headers()['next-action']!, ruta: new URL(peticion.url()).pathname };
}
async function repetirAccion(page: Page, accion: { id: string; ruta: string }, argumento: string) {
  const r = await page.request.post(accion.ruta, { headers: { 'next-action': accion.id, 'content-type': 'text/plain;charset=UTF-8' }, data: JSON.stringify([argumento]) });
  return { estado: r.status(), cuerpo: await r.text() };
}
const idsVisibles = async (page: Page) => (await page.locator('a[href*="/privado/pedidos/"]').evaluateAll((a) => a.map((x) => x.getAttribute('href')!.split('/').pop()!)));

test('puntos 1 y 8: invocar la acción directamente con el id de otro usuario no hace nada', async ({ browser }) => {
  const ana = await (await browser.newContext()).newPage();
  await entrarComo(ana, 'ana', '10.0.0.1');
  const accion = await capturarAccion(ana, `Eliminar ${ANA_2.slice(0, 4)}`);          // Ana elimina uno suyo: legítimo
  await expect(ana.locator(`a[href$="${ANA_2}"]`)).toHaveCount(0);                  // desapareció de su lista
  const ataque = await repetirAccion(ana, accion, LUIS);                                // y repite la petición con el id de Luis
  expect(ataque.cuerpo).toContain('Pedido no encontrado');
  const luis = await (await browser.newContext()).newPage();
  await entrarComo(luis, 'luis', '10.0.0.2');
  expect(await idsVisibles(luis)).toContain(LUIS);                                      // el pedido de Luis sigue ahí
});

test('punto 1: la acción "Antes" del README sí permite borrar el pedido de otro (por eso se corrige)', async ({ browser }) => {
  const ana = await (await browser.newContext()).newPage();
  await entrarComo(ana, 'ana', '10.0.0.3');
  const accion = await capturarAccion(ana, `Eliminar (acción vulnerable) ${ANA.slice(0, 4)}`);
  const ataque = await repetirAccion(ana, accion, LUIS);
  expect(ataque.estado).toBe(200);
  const luis = await (await browser.newContext()).newPage();
  await entrarComo(luis, 'luis', '10.0.0.4');
  expect(await idsVisibles(luis)).not.toContain(LUIS);                                  // Ana borró el pedido de Luis
});

test('puntos 2, 3 y 8: la página no leída y la lista no filtran datos; el pedido ajeno no se puede ver', async ({ browser }) => {
  const ana = await (await browser.newContext()).newPage();
  await entrarComo(ana, 'ana', '10.0.0.5');
  const html = await ana.content();
  for (const interno of ['costoInterno', 'notaDeRiesgo', 'contracargo previo', 'clienteId', LUIS]) expect(html, interno).not.toContain(interno);
  expect(await idsVisibles(ana)).toEqual([ANA, ANA_2]);
  const propio = await ana.request.get(`/n6/02-seguridad/privado/pedidos/${ANA}`);
  expect(propio.status()).toBe(200);
  const ajeno = await ana.request.get(`/n6/02-seguridad/privado/pedidos/${LUIS}`);
  const textoAjeno = await ajeno.text();
  expect(textoAjeno).toContain('Pedido no encontrado');
  expect(textoAjeno).not.toContain('Prensa');
  const sinSesion = await (await browser.newContext()).newPage();
  await sinSesion.goto(`/n6/02-seguridad/privado/pedidos/${ANA}`);
  expect(await sinSesion.content()).toContain('Pedido no encontrado');                  // sin sesión: tampoco
});

test('punto 4: no hay secretos en los archivos que llegan al navegador', async () => {
  const secretos = ['secreto-de-pruebas-de-32-caracteres', 'clave-de-ana-123', 'clave-de-luis-123', 'clave-de-admin-123', 'contracargo previo', 'costoInterno', 'SESION_SECRETO'];
  const recorrer = (d: string): string[] => readdirSync(d).flatMap((n) => (statSync(join(d, n)).isDirectory() ? recorrer(join(d, n)) : [join(d, n)]));
  const archivos = recorrer('.next/static');
  expect(archivos.length).toBeGreaterThan(5);
  for (const a of archivos) {
    const contenido = readFileSync(a, 'utf8');
    for (const s of secretos) expect(contenido.includes(s), `${s} aparece en ${a}`).toBe(false);
  }
});

test('punto 6: cabeceras de seguridad y CSP con nonce, sin violaciones en la consola', async ({ page, request }) => {
  const errores: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errores.push(m.text()); });
  const r = await request.get('/n6/02-seguridad');
  const h = r.headers();
  expect(h['strict-transport-security']).toContain('max-age=63072000');
  expect(h['x-content-type-options']).toBe('nosniff');
  expect(h['referrer-policy']).toBe('strict-origin-when-cross-origin');
  expect(h['permissions-policy']).toContain('camera=()');
  expect(h['x-powered-by']).toBeUndefined();
  const csp = h['content-security-policy']!;
  expect(csp).toMatch(/script-src 'self' 'nonce-[A-Za-z0-9+/=]+' 'strict-dynamic'/);
  expect(csp).toContain("frame-ancestors 'none'");
  expect(csp).toContain("object-src 'none'");
  const nonce = /'nonce-([^']+)'/.exec(csp)![1]!;
  const html = await r.text();
  expect(html).toContain(`nonce="${nonce}"`);                                           // los scripts de Next.js llevan el nonce
  const otra = (await request.get('/n6/02-seguridad')).headers()['content-security-policy']!;
  expect(otra).not.toBe(csp);                                                           // un nonce distinto por petición
  await page.goto('/n6/02-seguridad', { waitUntil: 'networkidle' });
  await page.getByLabel('Correo').fill('x@y.example');                                  // la página sigue siendo interactiva con la CSP
  await expect(page.getByLabel('Correo')).toHaveValue('x@y.example');
  expect(errores.filter((e) => /Content Security Policy|violates/i.test(e))).toEqual([]);
});

test('punto 7: el ingreso se bloquea al sexto intento fallido y no revela si el correo existe', async ({ page }) => {
  await page.context().setExtraHTTPHeaders({ 'x-forwarded-for': '10.9.9.9' });
  await page.goto('/n6/02-seguridad', { waitUntil: 'networkidle' });
  const intentar = async (correo: string) => {
    await page.getByLabel('Correo').fill(correo);
    await page.getByLabel('Clave').fill('mala');
    await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST' && !!r.request().headers()['next-action']),
      page.getByRole('button', { name: 'Ingresar' }).click(),
    ]);
  };
  await intentar('ana@correo.example');
  await expect(page.locator('p[role=alert]')).toHaveText('Correo o clave incorrectos');
  const conExistente = await page.locator('p[role=alert]').textContent();
  await intentar('nadie@correo.example');
  await expect(page.locator('p[role=alert]')).toHaveText(conExistente!);                      // el mismo mensaje exista o no el correo
  for (let i = 0; i < 3; i++) await intentar(`otro${i}@correo.example`);                // 5 intentos en total desde la misma IP
  await intentar('ana@correo.example');                                                 // sexto
  await expect(page.locator('p[role=alert]')).toContainText('Demasiados intentos');
});

test('autenticación: retorno validado, y la cookie robada deja de servir al cerrar sesión', async ({ browser }) => {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/n6/02-seguridad?retorno=//evil.example', { waitUntil: 'networkidle' });
  await page.getByLabel('Correo').fill('ana@correo.example');
  await page.getByLabel('Clave').fill('clave-de-ana-123');
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page).toHaveURL(/127\.0\.0\.1:\d+\/n6\/02-seguridad\/privado$/);         // no sale a evil.example
  const cookies = await page.context().cookies();
  const sesion = cookies.find((c) => c.name === 'sesion')!;
  expect(sesion.httpOnly).toBe(true);
  expect(sesion.secure).toBe(true);
  expect(sesion.sameSite).toBe('Lax');
  expect(await page.evaluate(() => document.cookie)).not.toContain('sesion');           // JavaScript no puede leerla

  const antes = await (await browser.newContext({ extraHTTPHeaders: { cookie: `sesion=${sesion.value}` } })).newPage();
  await antes.goto('/n6/02-seguridad/privado');
  await expect(antes.getByRole('heading', { name: 'Pedidos de Ana' })).toBeVisible();   // la cookie copiada funciona mientras la sesión vive
  await page.goto('/n6/02-seguridad');
  await page.getByRole('button', { name: 'Salir' }).click();
  await expect(page.getByLabel('Correo')).toBeVisible();
  const despues = await (await browser.newContext({ extraHTTPHeaders: { cookie: `sesion=${sesion.value}` } })).newPage();
  await despues.goto('/n6/02-seguridad/privado');
  await expect(despues).toHaveURL(/\/n6\/02-seguridad\?retorno=/);                      // la copia ya no sirve: cierre efectivo en el servidor
});
