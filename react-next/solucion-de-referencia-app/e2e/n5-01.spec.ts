// Taller nivel-05/01: JavaScript enviado antes y después (punto 1) y datos que llegan al navegador (punto 2).
import { expect, test } from '@playwright/test';
import { gzipSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';

async function medirJs(page: import('@playwright/test').Page, ruta: string) {
  const scripts: { url: string; bytes: number; gzip: number }[] = [];
  page.on('response', async (r) => {
    if (r.request().resourceType() !== 'script') return;
    const cuerpo = await r.body().catch(() => Buffer.alloc(0));
    scripts.push({ url: r.url(), bytes: cuerpo.length, gzip: gzipSync(cuerpo).length });
  });
  await page.goto(ruta, { waitUntil: 'networkidle' });
  return { ruta, archivos: scripts.length, bytes: scripts.reduce((s, x) => s + x.bytes, 0), gzip: scripts.reduce((s, x) => s + x.gzip, 0) };
}

test('punto 1: la página convertida envía menos JavaScript que la de cliente', async ({ browser }) => {
  const antes = await medirJs(await (await browser.newContext()).newPage(), '/n5/01-servidor-cliente/antes/1');
  const despues = await medirJs(await (await browser.newContext()).newPage(), '/n5/01-servidor-cliente/productos/1');
  mkdirSync('mediciones', { recursive: true });
  writeFileSync('mediciones/n5-01-javascript.json', JSON.stringify({ antes, despues, ahorroGzip: antes.gzip - despues.gzip }, null, 2));
  console.log(JSON.stringify({ antes, despues }));
  expect(despues.gzip).toBeLessThan(antes.gzip);
});

test('el HTML inicial: "antes" llega vacío, "después" ya trae el contenido (lo que el README afirma)', async ({ request }) => {
  const antes = await (await request.get('/n5/01-servidor-cliente/antes/1')).text();
  const despues = await (await request.get('/n5/01-servidor-cliente/productos/1')).text();
  expect(antes).toContain('Cargando…');
  expect(antes).not.toContain('Café de Huila 500 g');
  expect(despues).toContain('Café de Huila 500 g');
  expect(despues).toContain('<strong>cítricos</strong>');          // el Markdown ya viene convertido por el servidor
});

test('punto 2: el registro completo cruza la frontera; la proyección no', async ({ request }) => {
  const fuga = await (await request.get('/n5/01-servidor-cliente/fuga')).text();
  const proyectada = await (await request.get('/n5/01-servidor-cliente/proyectada')).text();
  const ficha = await (await request.get('/n5/01-servidor-cliente/productos/1')).text();
  for (const secreto of ['costoProveedor', 'Proveedor con retrasos en marzo']) {
    expect(fuga, `fuga contiene ${secreto}`).toContain(secreto);
    expect(proyectada, `proyectada contiene ${secreto}`).not.toContain(secreto);
    expect(ficha, `ficha contiene ${secreto}`).not.toContain(secreto);
  }
});

test('el endpoint del ejemplo "antes" ya recorta los campos internos', async ({ request }) => {
  const cuerpo = await (await request.get('/api/productos/1')).json();
  expect(cuerpo).not.toHaveProperty('costoProveedor');
  expect(cuerpo.nombre).toBe('Café de Huila 500 g');
});

test('la frontera pequeña funciona: el botón de cliente actualiza el indicador del carrito', async ({ page }) => {
  await page.goto('/n5/01-servidor-cliente/productos/1', { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Agregar al carrito' }).click();
  await expect(page.getByRole('status')).toHaveText('En el carrito: 1');
  await page.getByRole('button', { name: 'Imagen siguiente' }).click();
  await expect(page.getByText('2/2')).toBeVisible();
});
