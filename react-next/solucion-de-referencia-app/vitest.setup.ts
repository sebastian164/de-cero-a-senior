import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Node 25+ trae su propio localStorage global (vacío sin --localstorage-file) y tapa al de jsdom.
// Se reponen los de jsdom para que las pruebas usen el almacenamiento del "navegador".
const dom = (globalThis as unknown as { jsdom?: { window: Window } }).jsdom;
if (dom) {
  for (const nombre of ['localStorage', 'sessionStorage'] as const) {
    Object.defineProperty(globalThis, nombre, { configurable: true, get: () => dom.window[nombre] });
  }
}

afterEach(() => cleanup());
