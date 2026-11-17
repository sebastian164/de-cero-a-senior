// Taller nivel-03/05, punto 2: salto de diseño (CLS) con indicador giratorio frente a esqueleto, medido en el navegador.
import { expect, test } from '@playwright/test';
import { guardarMedicion } from './registro';

async function medirCls(page: import('@playwright/test').Page, modo: string) {
  await page.addInitScript(() => {
    const w = window as unknown as { __cls: number };
    w.__cls = 0;
    new PerformanceObserver((lista) => {
      for (const e of lista.getEntries() as unknown as { value: number; hadRecentInput: boolean }[]) if (!e.hadRecentInput) w.__cls += e.value;
    }).observe({ type: 'layout-shift', buffered: true });
  });
  await page.goto(`/n3/05-suspense/cls?modo=${modo}`, { waitUntil: 'load' });
  await expect(page.getByText('Contenido de 320 px')).toBeVisible();
  await page.waitForTimeout(500);                                          // deja registrar los últimos desplazamientos
  return Number((await page.evaluate(() => (window as unknown as { __cls: number }).__cls)).toFixed(4));
}

test('el esqueleto que reserva el alto elimina el salto; el giratorio lo provoca', async ({ browser }) => {
  const resultado: Record<string, number> = {};
  for (const modo of ['giratorio', 'esqueleto']) {
    const contexto = await browser.newContext({ baseURL: test.info().project.use.baseURL, viewport: { width: 1000, height: 700 } });
    resultado[modo] = await medirCls(await contexto.newPage(), modo);
    await contexto.close();
  }
  guardarMedicion('n3-05.json', 'cls', { ...resultado, nota: 'CLS acumulado (PerformanceObserver layout-shift); el bloque real mide 320 px y el giratorio reserva 24' });
  expect(resultado.giratorio!).toBeGreaterThan(0.01);                       // el párrafo de debajo baja 296 px (solo un párrafo se mueve: por eso el valor es pequeño)
  expect(resultado.esqueleto!).toBe(0);
  expect(resultado.esqueleto!).toBeLessThan(resultado.giratorio!);
});
