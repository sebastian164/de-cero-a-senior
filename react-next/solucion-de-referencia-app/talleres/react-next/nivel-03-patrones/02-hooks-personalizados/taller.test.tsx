import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { act, render, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { useDebounce, useInterruptor, useVisibleEnPantalla } from './hooks';
import { filtrar, ordenar } from './tabla';
import { FormularioClave, FormularioClaveConBiblioteca, FormularioContacto } from './ui/formularios';
import { TablaPedidos } from './ui/tabla-pedidos';
import { paginasTotales, rebanar } from '@/react-next/n3-02-hooks/paginacion';
import { usePaginacion } from '@/react-next/n3-02-hooks/use-paginacion';
import { ListaOrdenada } from '@/react-next/n3-02-hooks/use-ordenado';
import { listarDto, estadoApi, PEDIDOS_INICIALES } from '../04-capa-de-datos-y-adaptadores/api-simulada';

const servidor = setupServer(http.get('*/api/n3-04/pedidos', ({ request }) => HttpResponse.json(listarDto(new URL(request.url).searchParams))));
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { servidor.resetHandlers(); estadoApi.pedidos = structuredClone(PEDIDOS_INICIALES); });
afterAll(() => servidor.close());

describe('nivel-03/02 · punto 1: hooks pequeños', () => {
  it('useInterruptor', () => {
    const { result } = renderHook(() => useInterruptor());
    act(() => result.current.alternar());
    expect(result.current.activo).toBe(true);
    act(() => result.current.desactivar());
    expect(result.current.activo).toBe(false);
  });
  it('useDebounce entrega el último valor tras el silencio', () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(({ v }) => useDebounce(v, 300), { initialProps: { v: 'a' } });
    rerender({ v: 'ab' }); rerender({ v: 'abc' });
    act(() => vi.advanceTimersByTime(299));
    expect(result.current).toBe('a');
    act(() => vi.advanceTimersByTime(1));
    expect(result.current).toBe('abc');
    vi.useRealTimers();
  });
  it('useVisibleEnPantalla observa y desconecta', () => {
    let avisar: (e: { isIntersecting: boolean }[]) => void = () => {};
    const desconectar = vi.fn();
    vi.stubGlobal('IntersectionObserver', class { constructor(cb: typeof avisar) { avisar = cb; } observe() {} disconnect = desconectar; });
    function Caja() { const [ref, visible] = useVisibleEnPantalla<HTMLDivElement>(); return <div ref={ref}>{visible ? 'visible' : 'oculto'}</div>; }
    const { unmount } = render(<Caja />);
    expect(screen.getByText('oculto')).toBeInTheDocument();
    act(() => avisar([{ isIntersecting: true }]));
    expect(screen.getByText('visible')).toBeInTheDocument();
    unmount();
    expect(desconectar).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});

describe('nivel-03/02 · punto 2: useFormulario en dos formularios', () => {
  it('contacto: errores solo de campos tocados, y todos al enviar', async () => {
    const u = userEvent.setup();
    render(<FormularioContacto />);
    await u.click(screen.getByLabelText('Nombre'));
    await u.tab();
    expect(screen.getByRole('alert')).toHaveTextContent('Escribe tu nombre');
    await u.click(screen.getByRole('button', { name: 'Enviar contacto' }));
    expect(screen.getAllByRole('alert')).toHaveLength(2);
    await u.type(screen.getByLabelText('Nombre'), 'Ana');
    await u.type(screen.getByLabelText('Correo de contacto'), 'ana@x.example');
    await u.click(screen.getByRole('button', { name: 'Enviar contacto' }));
    expect(screen.getByRole('status')).toHaveTextContent('Gracias, Ana');
  });
  it('clave: misma lógica, otras reglas; y la versión con biblioteca da el mismo resultado', async () => {
    const u = userEvent.setup();
    render(<><FormularioClave /><FormularioClaveConBiblioteca /></>);
    for (const [clave, repetir, boton, ok] of [['Clave nueva', 'Repite la clave', 'Cambiar clave', 'Clave cambiada'], ['Clave (biblioteca)', 'Repite (biblioteca)', 'Cambiar con biblioteca', 'Clave cambiada con biblioteca']] as const) {
      await u.type(screen.getByLabelText(clave), 'corta');
      await u.tab();
      expect(await screen.findByText('Mínimo 12 caracteres')).toBeInTheDocument();
      await u.clear(screen.getByLabelText(clave));
      await u.type(screen.getByLabelText(clave), 'una-clave-larga');
      await u.type(screen.getByLabelText(repetir), 'una-clave-larga');
      await u.click(screen.getByRole('button', { name: boton }));
      expect(await screen.findByText(ok, { exact: true })).toBeInTheDocument();
    }
  });
});

describe('nivel-03/02 · punto 4: lógica pura sin React y hook con renderHook', () => {
  const filas = [{ id: '1', cliente: 'Luis', total: 5 }, { id: '2', cliente: 'Ana', total: 9 }, { id: '3', cliente: 'Eva', total: 1 }];
  it('funciones puras', () => {
    expect(paginasTotales(0, 10)).toBe(1);
    expect(paginasTotales(21, 10)).toBe(3);
    expect(rebanar([1, 2, 3, 4, 5], 2, 2)).toEqual([3, 4]);
    expect(filtrar(filas, ' an ').map((f) => f.id)).toEqual(['2']);
    expect(ordenar(filas, { campo: 'total', sentido: 'desc' }).map((f) => f.id)).toEqual(['2', '1', '3']);
  });
  it('usePaginacion del README: no queda fuera de rango si la lista se acorta', () => {
    const { result, rerender } = renderHook(({ lista }) => usePaginacion(lista, 2), { initialProps: { lista: [1, 2, 3, 4, 5] } });
    act(() => result.current.irA(3));
    expect(result.current.elementos).toEqual([5]);
    rerender({ lista: [1, 2] });
    expect(result.current.pagina).toBe(1);
    act(() => result.current.irA(99));
    expect(result.current.pagina).toBe(1);
  });
  it('useOrdenado del README ordena con la estrategia recibida', () => {
    render(<ListaOrdenada pedidos={[{ id: 'viejo', creadoEn: new Date(2020, 0) }, { id: 'nuevo', creadoEn: new Date(2026, 0) }]} />);
    expect(screen.getAllByRole('listitem').map((li) => li.textContent)).toEqual(['nuevo', 'viejo']);
  });
});

describe('nivel-03/02 · punto 5: la biblioteca de datos queda detrás de hooks de dominio', () => {
  it('la tabla muestra los pedidos del hook usePedidos (TanStack Query por debajo)', async () => {
    const u = userEvent.setup();
    render(<QueryClientProvider client={new QueryClient()}><TablaPedidos /></QueryClientProvider>);
    expect(await screen.findByText('Ana Gómez')).toBeInTheDocument();
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Total' }));
    expect(screen.getAllByRole('row')[1]).toHaveTextContent('Juan Díaz');
    await u.type(screen.getByLabelText('Filtrar por cliente'), 'eva');
    expect(screen.getAllByRole('row')).toHaveLength(2);
  });
  it('ningún componente de ui/ importa @tanstack/react-query', () => {
    const dir = join(__dirname, 'ui');
    for (const archivo of readdirSync(dir)) expect(readFileSync(join(dir, archivo), 'utf8')).not.toMatch(/@tanstack\/react-query/);
  });
});
