// Taller frontend-senior/02, puntos 3, 5 y 6, en el navegador: errores no controlados con traza real simbolizada,
// límites de error por sección y bandera con porcentaje que cambia sin desplegar.
import { expect, test } from '@playwright/test';
import { simbolizar } from '../talleres/frontend-senior/02-calidad-entrega-y-observabilidad/simbolizar';
import { guardarMedicion } from './registro';

const RUTA = '/fe/02-observabilidad';
interface Reporte { mensaje: string; ruta?: string; version?: string; pila?: string }
const errores = async (request: import('@playwright/test').APIRequestContext) => (await (await request.get('/api/errores')).json()) as Reporte[];

test('(a) el error no controlado llega al colector con versión y ruta, y su traza real vuelve al código original', async ({ page, request }) => {
  const provocados: string[] = [];
  page.on('pageerror', (e) => provocados.push(e.message));                         // los fallos son provocados: no se tratan como defectos
  await page.goto(RUTA, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Provocar error no controlado' }).click();
  await page.getByRole('button', { name: 'Provocar promesa rechazada' }).click();
  await expect.poll(async () => (await errores(request)).filter((e) => e.ruta === RUTA).length).toBeGreaterThanOrEqual(2);

  const lista = (await errores(request)).filter((e) => e.ruta === RUTA);
  const falla = lista.find((e) => e.mensaje === 'Fallo provocado desde el taller de observabilidad')!;
  const rechazo = lista.find((e) => e.mensaje === 'Promesa rechazada sin capturar');
  expect(falla, 'reporte del error no controlado').toBeDefined();
  expect(rechazo, 'reporte de la promesa rechazada').toBeDefined();
  expect(falla.version).toBe('1.0.0');                                             // NEXT_PUBLIC_VERSION de la construcción
  expect(falla.pila).toContain('/_next/static/');

  const original = await simbolizar(falla.pila!, '.next/static');                  // la pila REAL de producción, con los mapas de la construcción
  const fuentes = original.map((o) => `${o.fuente}:${o.linea}:${o.columna} ${o.nombre ?? ''}`.trim());
  guardarMedicion('fe-02.json', 'simbolizacionReal', { pilaMinificada: falla.pila!.split('\n').slice(0, 3), original: fuentes.slice(0, 4) });
  const enFalla = original.find((o) => o.fuente?.endsWith('src/fe/falla.ts'));
  expect(enFalla, 'algún marco vuelve a src/fe/falla.ts').toBeDefined();
  expect(enFalla!.linea).toBe(3);                                                  // la línea del throw dentro de provocarFallaNoControlada
  expect(provocados.length).toBeGreaterThanOrEqual(1);
});

test('(b) romper una sección deja viva la otra y "Reintentar" la recupera', async ({ page }) => {
  page.on('console', () => {});                                                    // React informa por consola el error del límite: es el provocado
  await page.goto(RUTA, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Romper Inventario' }).click();
  await expect(page.getByText('No pudimos mostrar Inventario. El resto de la página sigue funcionando.')).toBeVisible();
  await expect(page.getByText('Sección Recomendaciones: funcionando')).toBeVisible();   // la otra sección sigue funcionando
  await page.screenshot({ path: 'capturas/fe_02-observabilidad_seccion_rota.png', fullPage: true });
  await page.getByRole('button', { name: 'Reintentar Inventario' }).click();
  await expect(page.getByText('Sección Inventario: funcionando')).toBeVisible();
});

test('(c) la bandera cambia por POST sin desplegar, y el porcentaje se cumple en una muestra', async ({ page, request }) => {
  const poner = (porcentaje: number) => request.post('/api/banderas/nueva-ficha', { data: { porcentaje } });
  await poner(0);
  await page.goto(RUTA, { waitUntil: 'networkidle' });
  await expect(page.getByTestId('ficha')).toHaveText('Ficha clásica');
  await poner(100);
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.getByTestId('ficha')).toHaveText(/Ficha NUEVA/);                  // misma persona, mismo código: cambió solo la configuración

  await poner(30);
  let activas = 0;
  const N = 1000;
  for (let i = 0; i < N; i++) {
    const r = await (await request.get(`/api/banderas/nueva-ficha?usuario=uid-${i}`)).json() as { activa: boolean };
    if (r.activa) activas++;
  }
  guardarMedicion('fe-02.json', 'bandera30', { muestra: N, activas, proporcion: activas / N });
  expect(activas / N).toBeGreaterThan(0.25);
  expect(activas / N).toBeLessThan(0.35);
  await poner(0);
});

test('(d) un error marcado como esperado no aparece en el colector; uno inesperado sí', async ({ request }) => {
  const marca = `validacion-${Date.now()}`;
  expect((await request.post('/api/errores', { data: { mensaje: marca, ruta: RUTA, version: '1.0.0', esperado: true } })).status()).toBe(204);
  expect((await request.post('/api/errores', { data: { mensaje: `${marca}-inesperado`, ruta: RUTA, version: '1.0.0', esperado: false } })).status()).toBe(204);
  const lista = await errores(request);
  expect(lista.some((e) => e.mensaje === marca)).toBe(false);
  expect(lista.some((e) => e.mensaje === `${marca}-inesperado`)).toBe(true);
});
