import { expect, test as base } from '@playwright/test';

/**
 * Toda prueba falla si la página escribe un error en la consola o lanza una excepción no capturada.
 * Una prueba que provoca errores a propósito (un 500 simulado, por ejemplo) los declara en `erroresEsperados`.
 */
export const test = base.extend<{ erroresEsperados: RegExp[]; vigilarConsola: void }>({
  erroresEsperados: [[], { option: true }],
  vigilarConsola: [async ({ page, erroresEsperados, request }, usar) => {
    await request.post('/api/_reiniciar');
    const errores: string[] = [];
    page.on('console', (m) => { if (m.type() === 'error') errores.push(m.text()); });
    page.on('pageerror', (e) => errores.push(String(e)));
    page.on('response', (r) => { if (r.status() >= 400) errores.push(`HTTP ${r.status()} ${new URL(r.url()).pathname}`); });
    await usar();
    const inesperados = errores.filter((e) => !e.startsWith('Failed to load resource')).filter((e) => !erroresEsperados.some((r) => r.test(e)));
    expect(inesperados, 'errores en la consola').toEqual([]);
  }, { auto: true }],
});
export { expect };

export const captura = (nombre: string) => `capturas/${nombre}.png`;
