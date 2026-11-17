// Taller nivel-03/01, punto 5: un componente de cliente que recibe contenido de servidor como children. ¿Qué JavaScript llega?
import { expect, test } from '@playwright/test';
import { guardarMedicion } from './registro';

test('el código del componente de servidor no viaja al navegador, solo su resultado', async ({ page }) => {
  const scripts = new Map<string, string>();
  page.on('response', async (r) => {
    if (r.request().resourceType() === 'script') { try { scripts.set(r.url(), await r.text()); } catch { /* sin cuerpo */ } }
  });
  await page.goto('/n3/01-composicion', { waitUntil: 'networkidle' });
  const html = await page.content();
  const todoElJs = [...scripts.values()].join('\n');
  const MARCA = 'historial-solo-servidor-7c1f';                            // constante que solo existe en historial.tsx (server-only)
  guardarMedicion('n3-01.json', 'frontera', {
    scriptsCargados: scripts.size, bytesJs: todoElJs.length,
    marcaDelServidorEnElJs: todoElJs.includes(MARCA), codigoDelPanelDeClienteEnElJs: todoElJs.includes('aria-expanded'),
    resultadoDelServidorEnElHtml: (html.includes('H-1') && html.includes('2026-02-03')),
  });
  expect(html).toContain('H-1');                                           // el resultado del servidor ya está en el documento (dentro del panel cerrado)
  expect(html).toContain('2026-02-03');
  expect(todoElJs).not.toContain(MARCA);                                   // el código de HistorialDePedidos no llegó
  expect(todoElJs).toContain('aria-expanded');                             // el del panel (cliente) sí: lo necesita para abrir y cerrar
  await page.getByRole('button', { name: /Historial/ }).click();
  await expect(page.getByText('H-2 · 2026-02-03 · cliente C-7')).toBeVisible();
});
