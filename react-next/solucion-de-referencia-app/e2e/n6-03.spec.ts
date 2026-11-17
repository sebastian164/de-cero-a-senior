// Taller nivel-06/03: teclado, accesibilidad automática, diálogo nativo, rutas por idioma y error de hidratación, en navegador.
import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { PANTALLAS } from '../src/rutas';
import { guardarMedicion } from './registro';

const reglasDe = async (page: Page) => (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()).violations;
const resumen = (v: Awaited<ReturnType<typeof reglasDe>>) => v.map((x) => `${x.id} (${x.nodes.length})`);

test('punto 1: recorrido con teclado. El "div clicable" no se alcanza; el botón sí, con Enter y con Espacio', async ({ page }) => {
  // Safari/WebKit: Tab solo alcanza campos de texto; Opción+Tab recorre también botones y enlaces.
  const recorrer = async (n: number) => {
    const visitados: string[] = [];
    for (let i = 0; i < n; i++) { await page.keyboard.press(test.info().project.name === 'webkit' ? 'Alt+Tab' : 'Tab'); visitados.push(await page.evaluate(() => { const e = document.activeElement; return !e || e === document.body ? 'BODY' : (e.textContent || e.tagName).trim().slice(0, 40); })); }
    return visitados;
  };
  await page.goto('/n6/03-accesibilidad/antes', { waitUntil: 'networkidle' });
  const antes = await recorrer(6);
  expect(antes.join('|')).not.toContain('Guardar');                  // el div "Guardar" nunca recibe foco
  expect(antes.join('|')).not.toContain('Abrir diálogo casero');     // ni el span que abre el "diálogo"

  await page.goto('/n6/03-accesibilidad', { waitUntil: 'networkidle' });
  const boton = page.getByRole('button', { name: 'Guardar' });
  const despues = await recorrer(4);
  expect(despues).toContain('Guardar');                              // el botón sí está en el orden de tabulación
  await boton.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByText('Guardado', { exact: true })).toBeVisible();
  await page.reload({ waitUntil: 'networkidle' });
  await boton.focus();
  await page.keyboard.press('Space');
  await expect(page.getByText('Guardado', { exact: true })).toBeVisible();
});

test('punto 3: el diálogo nativo se abre con teclado, atrapa el foco, se cierra con Escape y devuelve el foco', async ({ page }) => {
  await page.goto('/n6/03-accesibilidad', { waitUntil: 'networkidle' });
  const abrir = page.getByRole('button', { name: 'Abrir diálogo' });
  await abrir.focus();
  await page.keyboard.press('Enter');
  const dialogo = page.getByRole('dialog', { name: '¿Eliminar el pedido?' });
  await expect(dialogo).toBeVisible();
  // "No sale": el foco está en el diálogo o en el documento mismo (la interfaz del navegador); nunca en el resto de la página, que queda inerte.
  const dentro = () => page.evaluate(() => !document.activeElement || document.activeElement === document.body || !!document.activeElement.closest('dialog'));
  expect(await page.evaluate(() => !!document.activeElement?.closest('dialog'))).toBe(true);   // el foco entró al diálogo
  for (let i = 0; i < 4; i++) { await page.keyboard.press('Tab'); expect(await dentro(), `Tab ${i + 1}: el foco no sale del diálogo`).toBe(true); }
  await page.keyboard.press('Escape');
  await expect(dialogo).toBeHidden();
  await expect(abrir).toBeFocused();                                 // y vuelve al botón que lo abrió
});

test('punto 2: la herramienta automática (axe, WCAG 2.1 A y AA) encuentra los fallos del "antes" y no encuentra nada en lo corregido', async ({ page }) => {
  await page.goto('/n6/03-accesibilidad/antes', { waitUntil: 'networkidle' });
  const antes = await reglasDe(page);
  await page.goto('/n6/03-accesibilidad', { waitUntil: 'networkidle' });
  const despues = await reglasDe(page);
  guardarMedicion('n6-03.json', 'axe', { antes: resumen(antes), despues: resumen(despues) });
  expect(antes.map((v) => v.id)).toContain('label');                 // campo sin etiqueta
  expect(despues).toEqual([]);
});

test('punto 6: axe sobre todas las pantallas de la aplicación', async ({ page }) => {
  test.slow(test.info().project.name !== 'chromium', 'recorrer todas las pantallas con axe tarda más en WebKit');
  const EXCLUIDAS = new Set(['/n6/03-accesibilidad/antes']);          // fallos a propósito
  const hallazgos: Record<string, string[]> = {};
  for (const p of PANTALLAS.filter((x) => !EXCLUIDAS.has(x.ruta))) {
    await page.goto(p.ruta, { waitUntil: 'networkidle' });
    const v = await reglasDe(page);
    if (v.length) hallazgos[p.ruta] = resumen(v);
  }
  guardarMedicion('n6-03.json', 'axeTodasLasPantallas', { pantallas: PANTALLAS.length - EXCLUIDAS.size, hallazgos });
  expect(hallazgos).toEqual({});
});

test('punto 4: rutas por idioma, con plurales, fechas y moneda', async ({ page, request }) => {
  for (const [idioma, titulo, precio, fecha, vacio, uno, varios] of [
    ['es', 'Productos', /\$\s?32\.000/, /1 de abril de 2026/, 'No hay artículos en el carrito', '1 artículo en el carrito', '2 artículos en el carrito'],
    ['en', 'Products', /\$8\.50/, /April 1, 2026/, 'No items in the cart', '1 item in the cart', '2 items in the cart'],
  ] as const) {
    await page.goto(`/${idioma}/productos`, { waitUntil: 'networkidle' });
    await expect(page.locator('html')).toHaveAttribute('lang', idioma);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(titulo);
    await expect(page.getByTestId('precio')).toHaveText(precio);
    await expect(page.getByTestId('fecha')).toHaveText(fecha);      // 02:30 UTC del 2 de abril = 1 de abril en Bogotá
    await expect(page.getByTestId('cantidad')).toHaveText(vacio);
    await page.getByRole('button', { name: idioma === 'es' ? 'Agregar' : 'Add' }).click();
    await expect(page.getByTestId('cantidad')).toHaveText(uno);
    await page.getByRole('button', { name: idioma === 'es' ? 'Agregar' : 'Add' }).click();
    await expect(page.getByTestId('cantidad')).toHaveText(varios);
  }
  // El idioma está en la URL: el HTML llega del servidor ya en ese idioma, y hay enlaces alternativos.
  const html = await (await request.get('/en/productos')).text();
  expect(html).toContain('<html lang="en"');
  expect(html).toContain('Huila coffee');
  expect(html).toMatch(/hrefLang="es"[^>]*href="\/es\/productos"|href="\/es\/productos"[^>]*hrefLang="es"/i);
  // El código que intercepta peticiones redirige según Accept-Language y cookie.
  const aEn = await request.get('/tienda', { headers: { 'accept-language': 'en-GB,en;q=0.9' }, maxRedirects: 0 });
  expect([307, 308]).toContain(aEn.status());
  expect(aEn.headers().location).toContain('/en/productos');
  const aEs = await request.get('/tienda', { headers: { 'accept-language': 'fr-FR' }, maxRedirects: 0 });
  expect(aEs.headers().location).toContain('/es/productos');
  const cookie = await request.get('/tienda', { headers: { 'accept-language': 'es', cookie: 'idioma=en' }, maxRedirects: 0 });
  expect(cookie.headers().location).toContain('/en/productos');
  expect((await request.get('/fr/productos')).status()).toBe(404);
});

test('punto 5: un error de hidratación por zona horaria, y su corrección', async ({ browser }) => {
  const errores = async (ruta: string) => {
    const contexto = await browser.newContext({ timezoneId: 'Asia/Tokyo', locale: 'es-CO' });
    const page = await contexto.newPage();
    const salida: string[] = [];
    page.on('console', (m) => { if (m.type() === 'error') salida.push(m.text()); });
    page.on('pageerror', (e) => salida.push(e.message));
    await page.goto(ruta, { waitUntil: 'networkidle' });
    const texto = await page.getByTestId('fecha').textContent();
    await contexto.close();
    return { salida, texto };
  };
  const mal = await errores('/n6/03-accesibilidad/hidratacion/mal');
  const bien = await errores('/n6/03-accesibilidad/hidratacion/bien');
  guardarMedicion('n6-03.json', 'hidratacion', { mal, bien });
  expect(mal.salida.some((e) => /hydrat|#418|#423|#425|did not match/i.test(e)), `errores: ${mal.salida.join(' | ')}`).toBe(true);
  expect(bien.salida).toEqual([]);
  expect(bien.texto).toMatch(/1 de abril de 2026/);                  // 21:30 en Bogotá, vea quien lo vea
});
