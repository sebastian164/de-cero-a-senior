import { StrictMode } from 'react';
import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, delay } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { useAlmacenLocal, useEnLinea } from './hooks';
import { buscarEnCatalogo, retardo } from './catalogo';
import {
  BuscadorConCarrera, BuscadorSinCarrera, Carrito, EditorClientes, SuscripcionConLimpieza, SuscripcionSinLimpieza, conexiones,
} from './componentes';
import { Buscador } from '@/react-next/n1-02-hooks-y-efectos/buscador';

const peticiones: string[] = [];
const servidor = setupServer(
  http.get('*/api/n1-02/productos', async ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q') ?? '';
    peticiones.push(q);
    await delay(retardo(q));
    return HttpResponse.json(buscarEnCatalogo(q, url.searchParams.get('c') ?? 'todas'));
  }),
);
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { servidor.resetHandlers(); peticiones.length = 0; localStorage.clear(); });
afterAll(() => servidor.close());

describe('nivel-01/02 · punto 1: useAlmacenLocal', () => {
  it('lee, escribe y se entera de cambios de otra pestaña', () => {
    localStorage.setItem('clave', JSON.stringify(5));
    const { result } = renderHook(() => useAlmacenLocal('clave', 0));
    expect(result.current[0]).toBe(5);
    act(() => result.current[1]((n) => n + 1));
    expect(result.current[0]).toBe(6);
    expect(localStorage.getItem('clave')).toBe('6');
    act(() => {
      localStorage.setItem('clave', '42');
      window.dispatchEvent(new StorageEvent('storage', { key: 'clave', newValue: '42' }));
    });
    expect(result.current[0]).toBe(42);
  });
  it('quita sus oyentes al desmontar', () => {
    const quitar = vi.spyOn(window, 'removeEventListener');
    const { unmount } = renderHook(() => useAlmacenLocal('x', ''));
    unmount();
    expect(quitar.mock.calls.map((c) => c[0])).toEqual(expect.arrayContaining(['storage', 'almacen-local']));
    quitar.mockRestore();
  });
  it('descarta valores que no pasan el validador', () => {
    localStorage.setItem('n', '"texto"');
    const esNumero = (v: unknown): v is number => typeof v === 'number';
    const { result } = renderHook(() => useAlmacenLocal('n', 7, esNumero));
    expect(result.current[0]).toBe(7);
  });
});

describe('nivel-01/02 · punto 2: useEnLinea', () => {
  it('cambia con los eventos online/offline y limpia', () => {
    const enLinea = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true);
    const quitar = vi.spyOn(window, 'removeEventListener');
    const { result, unmount } = renderHook(() => useEnLinea());
    expect(result.current).toBe(true);
    act(() => { enLinea.mockReturnValue(false); window.dispatchEvent(new Event('offline')); });
    expect(result.current).toBe(false);
    unmount();
    expect(quitar.mock.calls.map((c) => c[0])).toEqual(expect.arrayContaining(['online', 'offline']));
    enLinea.mockRestore(); quitar.mockRestore();
  });
});

describe('nivel-01/02 · punto 3: el aviso al padre sin efecto', () => {
  it('llama al padre en el mismo manejador', async () => {
    const alCambiar = vi.fn();
    render(<Carrito onCambio={alCambiar} />);
    await userEvent.click(screen.getByRole('button', { name: 'Agregar café' }));
    expect(alCambiar).toHaveBeenCalledWith(1);
    expect(screen.getByText('1 productos · 18000')).toBeInTheDocument();
    expect(document.title).toBe('Carrito (1)');
  });
});

describe('nivel-01/02 · punto 4: carrera entre peticiones', () => {
  it('sin cancelar, la respuesta de "c" (lenta) pisa a la de "caf"', async () => {
    const u = userEvent.setup();
    render(<BuscadorConCarrera />);
    await u.type(screen.getByLabelText('Buscar con carrera'), 'caf');
    await waitFor(() => expect(peticiones).toEqual(['c', 'ca', 'caf']));
    await new Promise((r) => setTimeout(r, 400));
    expect(screen.getByLabelText('resultados con carrera')).toHaveTextContent('Café de Huila, Café descafeinado, Cacao en polvo');
  });
  it('con AbortController queda el resultado del último término', async () => {
    const u = userEvent.setup();
    render(<BuscadorSinCarrera />);
    await u.type(screen.getByLabelText('Buscar sin carrera'), 'caf');
    await new Promise((r) => setTimeout(r, 400));
    expect(screen.getByLabelText('resultados sin carrera')).toHaveTextContent(/^Café de Huila, Café descafeinado$/);
  });
  it('el Buscador "Después" del README espera 300 ms, pide una vez y cuenta resultados', async () => {
    const u = userEvent.setup();
    render(<Buscador categoria="bebidas" />);
    await u.type(screen.getByLabelText('Buscar'), 'café');
    expect(await screen.findByText('2 resultados', {}, { timeout: 2000 })).toBeInTheDocument();
    expect(peticiones).toEqual(['café']);
  });
});

describe('nivel-01/02 · punto 5: reiniciar con key', () => {
  it('al cambiar de cliente, el formulario arranca con el nombre nuevo', async () => {
    const u = userEvent.setup();
    render(<EditorClientes />);
    await u.clear(screen.getByLabelText('Nombre'));
    await u.type(screen.getByLabelText('Nombre'), 'Ana María');
    await u.click(screen.getByRole('button', { name: 'Luis' }));
    expect(screen.getByLabelText('Nombre')).toHaveValue('Luis');
  });
});

describe('nivel-01/02 · punto 6: doble montaje del modo estricto', () => {
  it('sin limpieza quedan dos suscripciones abiertas; con limpieza, una', () => {
    conexiones.abiertas = 0;
    render(<StrictMode><SuscripcionSinLimpieza /></StrictMode>);
    expect(conexiones.abiertas).toBe(2);
    conexiones.abiertas = 0;
    render(<StrictMode><SuscripcionConLimpieza /></StrictMode>);
    expect(conexiones.abiertas).toBe(1);
  });
});
