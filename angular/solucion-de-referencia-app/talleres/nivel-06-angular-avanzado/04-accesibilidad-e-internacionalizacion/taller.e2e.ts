import AxeBuilder from '@axe-core/playwright';
import { spawn, type ChildProcess } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { captura, expect, test } from '../../../e2e/base';

const medidas: Record<string, unknown> = {};
const guardar = () => { mkdirSync('tmp', { recursive: true }); writeFileSync('tmp/medidas-n6-04.json', JSON.stringify(medidas, null, 2)); };
const axe = (page: import('@playwright/test').Page) => new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();

test.describe('n6-04 taller', () => {
  test('punto 1: solo con teclado, lo que NO se puede hacer en la pantalla inaccesible', async ({ page }) => {
    await page.goto('/n6-04/taller/antes');
    const enfocables: string[] = [];
    await page.getByRole('heading', { name: 'Pedido 7' }).click();                    // el recorrido empieza en la pantalla, tras el menú
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press('Tab');
      enfocables.push(await page.evaluate(() => { const e = document.activeElement!; return `${e.tagName.toLowerCase()}${e.className ? '.' + e.className : ''}`; }));
    }
    const noAlcanzables = ['div.boton', 'span.enlace', 'span.icono'].filter((x) => !enfocables.includes(x));
    medidas['teclado'] = { enfocablesEnOrden: [...new Set(enfocables)], noAlcanzables };
    expect(noAlcanzables).toEqual(['div.boton', 'span.enlace', 'span.icono']);        // ni el botón, ni el enlace, ni el ícono reciben el foco
    await page.getByText('Guardar', { exact: true }).focus().catch(() => {});
    await page.keyboard.press('Enter');
    await expect(page.getByText('Guardado')).toHaveCount(0);                          // y Enter no los activa
    guardar();
  });

  test('puntos 2 y 3: axe encuentra problemas en la pantalla inaccesible y ninguno en la corregida', async ({ page }) => {
    await page.goto('/n6-04/taller/antes');
    const antes = await axe(page);
    medidas['axeAntes'] = antes.violations.map((v) => ({ id: v.id, impacto: v.impact, nodos: v.nodes.length }));
    // axe acepta el placeholder como nombre del campo: ese fallo (README: "solo marcadores de posición") solo lo atrapa la revisión manual.
    expect(antes.violations.map((v) => v.id)).toEqual(expect.arrayContaining(['image-alt', 'color-contrast']));
    await page.goto('/n6-04/taller/despues');
    await page.getByRole('button', { name: 'Guardar' }).click();
    await expect(page.locator('[data-id=estado]')).toHaveText('Pedido guardado');
    const despues = await axe(page);
    medidas['axeDespues'] = despues.violations.map((v) => ({ id: v.id, impacto: v.impact, nodos: v.nodes.length }));
    expect(despues.violations).toEqual([]);
    // Todo es alcanzable y operable con teclado: botón, enlace y botón con nombre accesible
    await page.keyboard.press('Tab');
    await page.screenshot({ path: captura('n6-04-taller-despues'), fullPage: true });
    guardar();
  });

  test('punto 4: diálogo accesible: foco dentro, atrapado, Escape cierra y el foco vuelve', async ({ page }) => {
    await page.goto('/n6-04/taller/despues');
    const abrir = page.getByRole('button', { name: 'Eliminar pedido' });
    await abrir.focus();
    await page.keyboard.press('Enter');
    const dialogo = page.getByRole('alertdialog', { name: 'Eliminar pedido' });
    await expect(dialogo).toBeVisible();
    await expect(dialogo).toHaveAttribute('aria-modal', 'true');
    await expect(dialogo).toHaveAccessibleDescription(/¿Seguro que quieres eliminar el pedido 7\?/);
    await expect(page.getByRole('button', { name: 'Cancelar' })).toBeFocused();       // el foco entra
    for (let i = 0; i < 5; i++) {                                                      // Tab y Mayús+Tab no salen del diálogo
      await page.keyboard.press(i % 2 ? 'Shift+Tab' : 'Tab');
      expect(await page.evaluate(() => !!document.activeElement?.closest('[role=alertdialog]'))).toBe(true);
    }
    expect((await axe(page)).violations).toEqual([]);
    await page.screenshot({ path: captura('n6-04-taller-dialogo') });
    await page.keyboard.press('Escape');
    await expect(dialogo).toHaveCount(0);
    await expect(abrir).toBeFocused();                                                 // el foco vuelve a quien lo abrió
    await expect(page.locator('[data-id=resultado]')).toHaveText('Eliminación cancelada');
    await expect(page.locator('.cdk-live-announcer-element')).toHaveText('Eliminación cancelada');   // anunciado a los lectores de pantalla
    await abrir.click();
    await page.getByRole('button', { name: 'Eliminar', exact: true }).click();
    await expect(page.locator('[data-id=resultado]')).toHaveText('Pedido eliminado');
  });

  test('punto 5: formulario accesible: etiquetas, errores asociados y foco al primer error', async ({ page }) => {
    await page.goto('/n6-04/taller/despues');
    await page.getByLabel('Correo').fill('no-es-correo');
    await page.getByRole('button', { name: 'Enviar' }).click();
    await expect(page.getByRole('alert')).toContainText('Corrige estos campos');
    await expect(page.getByLabel('Nombre')).toBeFocused();                              // primer campo con error
    await expect(page.getByLabel('Nombre')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByLabel('Nombre')).toHaveAccessibleDescription('El nombre es obligatorio.');
    await expect(page.getByLabel('Correo')).toHaveAccessibleDescription('Escribe un correo válido.');
    expect((await axe(page)).violations).toEqual([]);
    await page.screenshot({ path: captura('n6-04-taller-formulario-errores') });
    await page.getByLabel('Nombre').fill('Ana');
    await page.getByLabel('Correo').fill('ana@correo.co');
    await page.getByRole('button', { name: 'Enviar' }).click();
    await expect(page.locator('[data-id=enviado]')).toHaveText('Formulario enviado');
  });
});

test.describe('n6-04 taller: punto 6, dos idiomas (compilación por idioma)', () => {
  let srv: ChildProcess;
  test.beforeAll(async () => {
    srv = spawn('node', ['servidor/servidor.mjs'], { env: { ...process.env, PUERTO: '49283', RAIZ: 'tmp/n6-04-i18n/browser' }, stdio: 'ignore' });
    await new Promise((r) => setTimeout(r, 800));
  });
  test.afterAll(() => srv.kill());

  const caso = [
    { loc: 'es-CO', cero: 'No tienes pedidos', uno: 'Tienes un pedido', cinco: 'Tienes 5 pedidos', fecha: 'jueves, 24 de diciembre de 2026', moneda: /^\$\s1\.234\.568$/, mezcla: 'Hola, Ana: tienes 5 pedidos activos.', botones: ['Ninguno', 'Uno', 'Cinco'] },
    { loc: 'en', cero: 'You have no orders', uno: 'You have one order', cinco: 'You have 5 orders', fecha: 'Thursday, December 24, 2026', moneda: /^\$1,234,568$/, mezcla: 'Hello, Ana: you have 5 active orders.', botones: ['None', 'One', 'Five'] },
  ];
  for (const c of caso) {
    test(`idioma ${c.loc}: textos, plurales, fecha y moneda`, async ({ page }) => {
      await page.goto(`http://localhost:49283/${c.loc}/index.html`);
      await expect(page.locator('[data-id=locale]')).toHaveText(c.loc);
      await expect(page.locator('[data-id=plural]')).toHaveText(c.cero);
      await page.getByRole('button', { name: c.botones[1], exact: true }).click();
      await expect(page.locator('[data-id=plural]')).toHaveText(c.uno);
      await page.getByRole('button', { name: c.botones[2], exact: true }).click();
      await expect(page.locator('[data-id=plural]')).toHaveText(c.cinco);
      await expect(page.locator('[data-id=fecha]')).toHaveText(c.fecha);
      expect((await page.locator('[data-id=moneda]').textContent())!.trim()).toMatch(c.moneda);
      await expect(page.locator('[data-id=mezcla]')).toHaveText(c.mezcla);          // la frase con variables se traduce entera, sin concatenar
      expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
      await page.screenshot({ path: captura(`n6-04-taller-i18n-${c.loc}`) });
    });
  }
});
