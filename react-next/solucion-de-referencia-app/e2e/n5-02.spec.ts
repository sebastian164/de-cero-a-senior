// Taller nivel-05/02, puntos 2 a 6: se modifica el origen y se observa cuándo cambia la página en producción (next build + next start).
import { expect, test, request as pw, type APIRequestContext } from '@playwright/test';

const BASE = `http://127.0.0.1:${process.env.PUERTO_APP ?? 49510}`;
import { guardarMedicion } from './registro';
const registro = new Proxy({} as Record<string, unknown>, { set: (_, k, v) => (guardarMedicion('n5-02-cache.json', String(k), v), true) });

const texto = async (r: APIRequestContext, ruta: string, id: string) => {
  const html = await (await r.get(ruta)).text();
  const interior = new RegExp(`data-testid="${id}"[^>]*>(.*?)</(?:span|p)>`, 's').exec(html)?.[1];
  return interior === undefined ? `(sin ${id})` : interior.replace(/<!--.*?-->/g, '');
};
const pausa = (ms: number) => new Promise((r) => setTimeout(r, ms));
const origen = (r: APIRequestContext, cuerpo: object) => r.post('/api/n5-02/origen', { data: cuerpo });

test.beforeEach(async ({ request }) => { await origen(request, { reiniciar: true }); });

test('lista por tiempo (revalidate 3 s): no cambia hasta vencer; tras vencer, la lectura siguiente ya trae el dato nuevo', async ({ request }) => {
  const secuencia: string[] = [];
  secuencia.push(`inicio=${await texto(request, '/n5/02-cache/catalogo', 'lista-1')}`);
  await origen(request, { id: '1', precio: 40000 });
  secuencia.push(`origen cambiado, lectura inmediata=${await texto(request, '/n5/02-cache/catalogo', 'lista-1')}`);
  await pausa(3500);
  secuencia.push(`tras vencer, 1.ª lectura=${await texto(request, '/n5/02-cache/catalogo', 'lista-1')}`);
  await pausa(800);
  secuencia.push(`2.ª lectura=${await texto(request, '/n5/02-cache/catalogo', 'lista-1')}`);
  registro.listaPorTiempo = secuencia;
  expect(secuencia[1]).toContain('=32000');                // dentro del tiempo: dato guardado
  expect(secuencia[2]).toContain('=40000');                // tras vencer, la primera lectura ya trae el dato nuevo (perfil corto: sin dato viejo)
  expect(secuencia[3]).toContain('=40000');                // ya regenerada
});

test('detalle por etiqueta: revalidateTag(etiqueta, "max") en un manejador sirve lo viejo una vez (stale-while-revalidate)', async ({ request }) => {
  const s: string[] = [];
  s.push(`inicio=${await texto(request, '/n5/02-cache/catalogo/2', 'detalle')}`);
  await origen(request, { id: '2', precio: 12000 });
  s.push(`origen cambiado=${await texto(request, '/n5/02-cache/catalogo/2', 'detalle')}`);
  const sinSecreto = await request.post('/api/n5-02/revalidar', { data: { etiqueta: 'articulo-2' } });
  expect(sinSecreto.status()).toBe(401);                   // el webhook exige el secreto compartido
  await request.post('/api/n5-02/revalidar', { data: { etiqueta: 'articulo-2' }, headers: { 'x-secreto-webhook': 'secreto-compartido-del-webhook' } });
  s.push(`1.ª lectura tras revalidateTag=${await texto(request, '/n5/02-cache/catalogo/2', 'detalle')}`);
  await pausa(800);
  s.push(`2.ª lectura=${await texto(request, '/n5/02-cache/catalogo/2', 'detalle')}`);
  registro.detallePorEtiqueta = s;
  expect(s[1]).toContain('=9000');
  expect(s[2]).toContain('=9000');                         // la afirmación de la nota de versión: la lectura siguiente aún recibe el dato viejo
  expect(s[3]).toContain('=12000');
});

test('contraste: updateTag desde una acción de servidor da el dato nuevo en la primera lectura', async ({ page, request }) => {
  const s: string[] = [];
  s.push(`inicio=${await texto(request, '/n5/02-cache/catalogo/1', 'detalle')}`);
  await origen(request, { id: '1', precio: 7777 });
  s.push(`origen cambiado=${await texto(request, '/n5/02-cache/catalogo/1', 'detalle')}`);
  await page.goto('/n5/02-cache/catalogo/1', { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Invalidar con updateTag' }).click();
  await expect(page.getByTestId('detalle')).toHaveText('7777');                  // la propia respuesta de la acción ya trae el dato nuevo
  s.push(`respuesta de la propia acción (interfaz)=${await page.getByTestId('detalle').textContent()}`);
  s.push(`1.ª petición nueva (otro cliente)=${await texto(request, '/n5/02-cache/catalogo/1', 'detalle')}`);
  await pausa(800);
  s.push(`2.ª petición nueva=${await texto(request, '/n5/02-cache/catalogo/1', 'detalle')}`);
  await page.reload({ waitUntil: 'networkidle' });
  s.push(`recarga del mismo navegador=${await page.getByTestId('detalle').textContent()}`);
  const otra = await page.context().newPage();
  await otra.goto('/n5/02-cache/catalogo/1', { waitUntil: 'networkidle' });
  s.push(`pestaña nueva=${await otra.getByTestId('detalle').textContent()}`);
  registro.detallePorUpdateTag = s;
  expect(s[1]).toContain('=32000');
  // Quien muta ve su dato nuevo en la primera lectura (la respuesta de la propia acción): la diferencia con revalidateTag(etiqueta, "max").
  expect(s[2]).toContain('=7777');
  // Comportamiento OBSERVADO con Next 16.4.6 (no un contrato): en esta ruta prerenderizada con generateStaticParams, las peticiones posteriores,
  // la recarga de quien mutó y una pestaña nueva siguen recibiendo la copia anterior (32000). Si una versión nueva lo corrige, esta afirmación
  // falla a propósito para delatar el cambio y actualizar la nota de versión del README (ver ANALISIS.md, sección 8).
  expect(s[3]).toContain('=32000');
  expect(s[4]).toContain('=32000');
  expect(s[5]).toContain('32000');
  expect(s[6]).toContain('32000');
});

test('sin caché: el precio exacto cambia en la siguiente petición', async ({ request }) => {
  const antes = await texto(request, '/n5/02-cache/catalogo', 'exacto');
  await origen(request, { id: '1', precio: 55555 });
  const despues = await texto(request, '/n5/02-cache/catalogo', 'exacto');
  registro.sinCache = { antes, despues };
  expect(antes).toBe('32000');
  expect(despues).toBe('55555');
});

test('mutación con updateTag e interfaz optimista, con reversa ante fallo', async ({ browser }) => {
  const contexto = await browser.newContext({ baseURL: BASE });
  await contexto.addCookies([{ name: 'usuario-demo', value: 'u-1', url: BASE }]);
  const page = await contexto.newPage();
  await page.goto('/n5/02-cache/pedidos', { waitUntil: 'networkidle' });
  await expect(page.getByText('Café de Huila ·')).toBeVisible();

  // Éxito: desaparece de inmediato y, al recargar, sigue sin estar (la acción invalidó la etiqueta con updateTag).
  await page.getByRole('button', { name: 'Cancelar Café de Huila' }).click();
  await expect(page.getByText('Café de Huila ·')).toHaveCount(0);
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.getByText('Café de Huila ·')).toHaveCount(0);
  await expect(page.getByText('Chocolate de mesa ·')).toBeVisible();

  // Fallo: el pedido de hace 3 días no se puede cancelar: reaparece y se avisa.
  await page.getByRole('button', { name: 'Cancelar Molino manual (hace 3 días)' }).click();
  await expect(page.locator('p[role=alert]')).toHaveText('El pedido ya no se puede cancelar');
  await expect(page.getByText('Molino manual (hace 3 días) ·')).toBeVisible();
  await contexto.close();
});

test('cache() de React: una consulta por petición aunque tres componentes pidan al usuario', async ({ browser }) => {
  const r = await pw.newContext({ baseURL: BASE, extraHTTPHeaders: { cookie: 'usuario-demo=u-1' } });
  const num = async (ruta: string, id: string) => Number(/\d+/.exec(await texto(r, ruta, id))![0]);
  const a = await num('/n5/02-cache/usuario-actual', 'consulta');
  const b = await num('/n5/02-cache/usuario-actual', 'consulta');
  const html = await (await r.get('/n5/02-cache/usuario-actual?modo=sin-cache')).text();
  const sin = [...html.replace(/<!--.*?-->/g, '').matchAll(/consulta nº (\d+)/g)].map((m) => Number(m[1]));
  registro.cacheDeReact = { conCache: [a, b], sinCache: sin };
  expect(b - a).toBe(1);                                    // una sola consulta por petición
  expect(sin).toHaveLength(3);
  expect(sin[2]! - sin[0]!).toBe(2);                        // sin cache: tres consultas en la misma petición
  void browser;
});

test('fuga entre usuarios por clave mal elegida, y su corrección', async () => {
  const como = (u: string) => pw.newContext({ baseURL: BASE, extraHTTPHeaders: { cookie: `usuario-demo=${u}` } });
  const ana = await como('u-1'); const luis = await como('u-2');
  const mal = { ana: await texto(ana, '/n5/02-cache/por-usuario?modo=mal', 'pedidos'), luis: await texto(luis, '/n5/02-cache/por-usuario?modo=mal', 'pedidos') };
  const bien = { ana: await texto(ana, '/n5/02-cache/por-usuario?modo=bien', 'pedidos'), luis: await texto(luis, '/n5/02-cache/por-usuario?modo=bien', 'pedidos') };
  registro.fuga = { mal, bien };
  expect(mal.ana).toContain('Café de Huila');
  expect(mal.luis).toContain('Café de Huila');              // Luis ve los pedidos de Ana: la fuga
  expect(bien.luis).toBe('Prensa francesa');
  expect(bien.ana).toBe('Café de Huila, Chocolate');
});
