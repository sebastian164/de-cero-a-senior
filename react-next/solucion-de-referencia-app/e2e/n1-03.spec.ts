// Taller nivel-01/03, puntos 3, 4 y 5: loading.tsx, error.tsx y not-found.tsx provocados; crear un libro y ver la lista revalidada
// (también sin JavaScript); y bytes de JavaScript de la versión de servidor frente a la toda-cliente.
import { expect, test, type Page } from '@playwright/test';
import { guardarMedicion } from './registro';

const R = '/n1/03-next';

test('(a) not-found.tsx: un libro inexistente muestra "Ese libro no existe" y se marca noindex', async ({ page, request }) => {
  // Hallazgo: con loading.tsx (y Cache Components) la página se envía en streaming, la cáscara sale con 200 antes de que notFound()
  // se ejecute y el estado ya no cambia a 404. Next lo compensa con <meta name="robots" content="noindex">. Ver ANALISIS.md.
  const html = await (await request.get(`${R}/libros/999`)).text();
  expect(html).toContain('<meta name="robots" content="noindex"/>');
  expect(html).toContain('NEXT_HTTP_ERROR_FALLBACK;404');
  await page.goto(`${R}/libros/999`);
  await expect(page.getByRole('heading', { name: 'Ese libro no existe' })).toBeVisible();
  await page.screenshot({ path: 'capturas/n1_03-next_libros_999.png' });
});

test('(b) error.tsx: ?fallar=1 muestra el límite de error con "Reintentar"', async ({ page }) => {
  const consola: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') consola.push(m.text()); });
  await page.goto(`${R}/libros/1?fallar=1`, { waitUntil: 'networkidle' });
  await expect(page.getByRole('heading', { name: 'No pudimos mostrar el libro' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
  await page.screenshot({ path: 'capturas/n1_03-next_libros_1_fallar.png' });
  expect(consola.length, 'el error provocado sí llega a la consola').toBeGreaterThan(0);
});

test('(c) loading.tsx: al navegar desde la lista se ve el esqueleto antes del detalle', async ({ page }) => {
  await page.goto(`${R}/libros`, { waitUntil: 'networkidle' });
  await page.getByRole('link', { name: 'Cien años de soledad' }).click();
  await expect(page.getByText('Cargando libro…')).toBeVisible();               // esqueleto de loading.tsx (la lectura tarda 400 ms)
  await expect(page.getByRole('heading', { name: 'Cien años de soledad' })).toBeVisible();
  await expect(page.getByText('Cargando libro…')).toHaveCount(0);
});

async function crearYComprobar(page: Page, titulo: string) {
  await page.getByLabel('Título').fill(titulo);
  await page.getByLabel('Autor').fill('Autora de Prueba');
  await page.getByLabel('Año').fill('2001');
  await page.getByRole('button', { name: 'Crear libro' }).click();
  await expect(page.getByRole('link', { name: titulo })).toBeVisible();        // la lista se revalidó con revalidatePath
}

test('(d) crear un libro lo deja en la lista tras revalidatePath, con JavaScript', async ({ page }) => {
  await page.goto(`${R}/libros`, { waitUntil: 'networkidle' });
  await crearYComprobar(page, `Con JS ${Date.now()}`);
  await expect(page.getByText(/^Creado: Con JS/)).toBeVisible();
});

test('(d) crear un libro lo deja en la lista también SIN JavaScript', async ({ browser }) => {
  const contexto = await browser.newContext({ javaScriptEnabled: false, baseURL: test.info().project.use.baseURL });
  const page = await contexto.newPage();
  await page.goto(`${R}/libros`);
  const titulo = `Sin JS ${Date.now()}`;
  await page.getByLabel('Título').fill(titulo);
  await page.getByLabel('Autor').fill('Autora de Prueba');
  await page.getByLabel('Año').fill('2002');
  await page.getByRole('button', { name: 'Crear libro' }).click();
  await page.waitForLoadState('load');
  await expect(page.getByRole('link', { name: titulo })).toBeVisible();
  await contexto.close();
});

test('(e) bytes de JavaScript: la página de servidor envía menos que la marcada toda con use client', async ({ browser }) => {
  const medir = async (ruta: string) => {
    const contexto = await browser.newContext({ baseURL: test.info().project.use.baseURL });
    const page = await contexto.newPage();
    const scripts = new Map<string, number>();
    page.on('response', async (r) => {
      if (r.request().resourceType() !== 'script') return;
      try { scripts.set(r.url(), (await r.body()).length); } catch { /* sin cuerpo */ }
    });
    await page.goto(ruta, { waitUntil: 'networkidle' });
    await contexto.close();
    return scripts;
  };
  const suma = (m: Map<string, number>, solo?: Set<string>) => [...m].filter(([u]) => !solo || solo.has(u)).reduce((a, [, b]) => a + b, 0);
  const servidor = await medir(`${R}/libros`);
  const cliente = await medir(`${R}/libros-cliente`);
  const soloServidor = new Set([...servidor.keys()].filter((u) => !cliente.has(u)));
  const soloCliente = new Set([...cliente.keys()].filter((u) => !servidor.has(u)));
  guardarMedicion('n1-03.json', 'javascript', {
    servidor: { archivos: servidor.size, bytes: suma(servidor), bytesSoloEnEstaPagina: suma(servidor, soloServidor) },
    cliente: { archivos: cliente.size, bytes: suma(cliente), bytesSoloEnEstaPagina: suma(cliente, soloCliente) },
    nota: 'bytes sin comprimir de los <script> cargados; "solo en esta página" excluye lo que comparten (Next, React y el diseño raíz)',
  });
  expect(suma(servidor)).toBeGreaterThan(0);
  // Misma página (lista con resumen en Markdown y formulario); solo cambia dónde se ejecuta marked: la versión toda-cliente lo envía al navegador.
  expect(suma(cliente, soloCliente), 'la página toda-cliente envía más JavaScript propio (marked)').toBeGreaterThan(suma(servidor, soloServidor));
  expect(suma(cliente)).toBeGreaterThan(suma(servidor));
});

test('(e) el HTML inicial: la lista de servidor ya trae los libros; la toda-cliente solo "Cargando…"', async ({ request }) => {
  const servidor = await (await request.get(`${R}/libros`)).text();
  const cliente = await (await request.get(`${R}/libros-cliente`)).text();
  guardarMedicion('n1-03.json', 'htmlInicial', {
    servidorTraeLibros: servidor.includes('La vorágine'), clienteTraeLibros: cliente.includes('La vorágine'), clienteDice: cliente.includes('Cargando…') ? 'Cargando…' : '(otro)',
    bytesHtmlServidor: servidor.length, bytesHtmlCliente: cliente.length,
  });
  expect(servidor).toContain('La vorágine');
  expect(cliente).not.toContain('La vorágine');
  expect(cliente).toContain('Cargando…');
});
