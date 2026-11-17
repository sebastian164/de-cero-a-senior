import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BotonAgregar, ProveedorDeDependencias } from '@/react-next/n2-05-dip/dependencias';
import { agregarAlCarrito, type Dependencias, type LineaCarrito } from '@/react-next/n2-05-dip/carrito';
import { crearCancelarPedido, repositorioEnMemoria } from './casos-de-uso';

function dependenciasFalsas() {
  let guardado: LineaCarrito[] = [];
  const eventos: string[] = [];
  const deps: Dependencias = {
    almacen: { leer: () => guardado, guardar: (l) => { guardado = l; } },
    analitica: { productoAgregado: (id) => eventos.push(id) },
    ahora: () => 1_700_000_000_000,
  };
  return { deps, eventos, guardado: () => guardado };
}

describe('nivel-02/05 · punto 2: la lógica con sus dependencias, sin navegador', () => {
  it('acumula sobre lo guardado, marca la hora del reloj inyectado y avisa a la analítica', () => {
    const { deps, eventos, guardado } = dependenciasFalsas();
    agregarAlCarrito({ id: 'p1', nombre: 'Café', precio: 18000 }, deps);
    agregarAlCarrito({ id: 'p2', nombre: 'Pan', precio: 3500 }, deps);
    expect(guardado().map((l) => [l.id, l.agregadoEn])).toEqual([['p1', 1_700_000_000_000], ['p2', 1_700_000_000_000]]);
    expect(eventos).toEqual(['p1', 'p2']);
  });
});

describe('nivel-02/05 · punto 3: implementaciones por contexto, cambiadas en una prueba de componente', () => {
  it('BotonAgregar del README usa lo que provee el contexto', async () => {
    const { deps, eventos, guardado } = dependenciasFalsas();
    render(<ProveedorDeDependencias valor={deps}><BotonAgregar producto={{ id: 'p9', nombre: 'Té', precio: 9000 }} /></ProveedorDeDependencias>);
    await userEvent.click(screen.getByRole('button', { name: 'Agregar' }));
    expect(guardado()).toHaveLength(1);
    expect(eventos).toEqual(['p9']);
    expect(localStorage.getItem('carrito-dip')).toBeNull(); // no tocó el navegador real
  });
  it('sin proveedor falla con un mensaje claro', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<BotonAgregar producto={{ id: 'p', nombre: 'x', precio: 1 }} />)).toThrow('Falta ProveedorDeDependencias');
    vi.mocked(console.error).mockRestore();
  });
});

describe('nivel-02/05 · punto 4: caso de uso de servidor probado sin Next ni base de datos', () => {
  const ahora = new Date('2026-05-01T12:00:00Z');
  const repo = () => repositorioEnMemoria([
    { id: 'A', clienteId: 'u1', creadoEn: new Date('2026-05-01T08:00:00Z'), estado: 'pendiente' },
    { id: 'B', clienteId: 'u1', creadoEn: new Date('2026-04-29T08:00:00Z'), estado: 'pendiente' },
  ]);
  it('cancela un pedido propio dentro de las 24 horas', async () => {
    const r = repo();
    expect(await crearCancelarPedido(r, () => ahora)('A', 'u1')).toEqual({ ok: true });
    expect(r.todos().find((p) => p.id === 'A')?.estado).toBe('cancelado');
  });
  it('rechaza ajenos, vencidos y repetidos', async () => {
    const r = repo();
    const cancelar = crearCancelarPedido(r, () => ahora);
    expect(await cancelar('A', 'otro')).toEqual({ ok: false, error: 'Pedido no encontrado' });
    expect(await cancelar('B', 'u1')).toEqual({ ok: false, error: 'El pedido ya no se puede cancelar' });
    await cancelar('A', 'u1');
    expect(await cancelar('A', 'u1')).toEqual({ ok: false, error: 'El pedido ya estaba cancelado' });
  });
});
