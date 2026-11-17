// Solución del taller de nivel-04/03 (Pruebas). Cada bloque describe un punto del enunciado.
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { reductorCarrito, totalCarrito, type EstadoCarrito } from '@/react-next/n3-03-carrito/reductor';
import { FormularioRegistroEnVivo } from '@/react-next/n4-02-formularios/registro/formulario-en-vivo';
import { PaginaPedidos } from '@/react-next/n4-03-pruebas/pagina-pedidos';
import { renderConProveedores, unProducto } from '@/react-next/n4-03-pruebas/fabricas';
import { crearCancelarPedido, sePuedeCancelar, type Dependencias, type PedidoRegistro } from './cancelar-pedido';

const registrarEnServidor = vi.hoisted(() => vi.fn());
vi.mock('@/react-next/n4-02-formularios/registro/acciones-en-vivo', () => ({ registrarEnServidor }));

const vacio: EstadoCarrito = { lineas: [] };

describe('punto 1: dominio y reductores', () => {
  it('agregar crea la línea y repetir suma cantidad', () => {
    const uno = reductorCarrito(vacio, { tipo: 'agregado', producto: unProducto() });
    expect(uno.lineas).toEqual([{ productoId: 'p1', nombre: 'Café', precio: 18000, cantidad: 1 }]);
    expect(reductorCarrito(uno, { tipo: 'agregado', producto: unProducto() }).lineas[0].cantidad).toBe(2);
  });
  it('cantidadCambiada actualiza, y con cero o menos quita la línea', () => {
    const uno = reductorCarrito(vacio, { tipo: 'agregado', producto: unProducto() });
    expect(reductorCarrito(uno, { tipo: 'cantidadCambiada', productoId: 'p1', cantidad: 5 }).lineas[0].cantidad).toBe(5);
    expect(reductorCarrito(uno, { tipo: 'cantidadCambiada', productoId: 'p1', cantidad: 0 }).lineas).toEqual([]);
  });
  it('quitado y vaciado', () => {
    let e = reductorCarrito(vacio, { tipo: 'agregado', producto: unProducto() });
    e = reductorCarrito(e, { tipo: 'agregado', producto: unProducto({ id: 'p2', precio: 3500 }) });
    expect(reductorCarrito(e, { tipo: 'quitado', productoId: 'p1' }).lineas.map((l) => l.productoId)).toEqual(['p2']);
    expect(reductorCarrito(e, { tipo: 'vaciado' }).lineas).toEqual([]);
  });
  it('es puro: no muta el estado de entrada', () => {
    const e = reductorCarrito(vacio, { tipo: 'agregado', producto: unProducto() });
    const copia = structuredClone(e);
    reductorCarrito(e, { tipo: 'agregado', producto: unProducto() });
    expect(e).toEqual(copia);
  });
  it('totalCarrito suma precio por cantidad', () => {
    let e = reductorCarrito(vacio, { tipo: 'agregado', producto: unProducto() });
    e = reductorCarrito(e, { tipo: 'agregado', producto: unProducto() });
    e = reductorCarrito(e, { tipo: 'agregado', producto: unProducto({ id: 'p2', precio: 3500 }) });
    expect(totalCarrito(e)).toBe(2 * 18000 + 3500);
    expect(totalCarrito(vacio)).toBe(0);
  });
  it('sePuedeCancelar: dentro y fuera de las 24 horas, y ya cancelado', () => {
    const p: PedidoRegistro = { id: 'x', clienteId: 'u-1', estado: 'pagado', creadoEn: new Date('2026-04-01T10:00:00Z') };
    expect(sePuedeCancelar(p, new Date('2026-04-02T10:00:00Z'))).toBe(true);
    expect(sePuedeCancelar(p, new Date('2026-04-02T10:00:01Z'))).toBe(false);
    expect(sePuedeCancelar({ ...p, estado: 'cancelado' }, p.creadoEn)).toBe(false);
  });
});

describe('punto 2: un formulario, como lo usa una persona', () => {
  it('escribir, salir del campo, ver el error, corregir y enviar', async () => {
    registrarEnServidor.mockResolvedValue({ mensaje: 'Registro completo' });
    const usuario = userEvent.setup();
    render(<FormularioRegistroEnVivo />);

    await usuario.type(screen.getByLabelText('Correo'), 'no-es-correo');
    await usuario.tab();
    expect(await screen.findByText('Escribe un correo válido')).toBeInTheDocument();

    await usuario.clear(screen.getByLabelText('Correo'));
    await usuario.type(screen.getByLabelText('Correo'), 'luis@correo.example');
    await usuario.type(screen.getByLabelText('Documento'), '1234567');
    await usuario.type(screen.getByLabelText('Clave'), 'una-clave-larga-1');
    await usuario.type(screen.getByLabelText('Confirma la clave'), 'otra-distinta-99');
    await usuario.click(screen.getByRole('button', { name: 'Registrarme' }));
    expect(await screen.findByText('Las claves no coinciden')).toBeInTheDocument();
    expect(registrarEnServidor).not.toHaveBeenCalled();

    await usuario.clear(screen.getByLabelText('Confirma la clave'));
    await usuario.type(screen.getByLabelText('Confirma la clave'), 'una-clave-larga-1');
    await usuario.click(screen.getByRole('button', { name: 'Registrarme' }));
    await waitFor(() => expect(registrarEnServidor).toHaveBeenCalledWith(
      { correo: 'luis@correo.example', documento: '1234567', clave: 'una-clave-larga-1', confirmacion: 'una-clave-larga-1' }));
  });
  it('un error del servidor se muestra junto a su campo', async () => {
    registrarEnServidor.mockResolvedValue({ errores: { correo: ['Ese correo ya está registrado'] } });
    const usuario = userEvent.setup();
    render(<FormularioRegistroEnVivo />);
    await usuario.type(screen.getByLabelText('Correo'), 'ana@correo.example');
    await usuario.type(screen.getByLabelText('Documento'), '1012345');
    await usuario.type(screen.getByLabelText('Clave'), 'una-clave-larga-1');
    await usuario.type(screen.getByLabelText('Confirma la clave'), 'una-clave-larga-1');
    await usuario.click(screen.getByRole('button', { name: 'Registrarme' }));
    expect(await screen.findByText('Ese correo ya está registrado')).toBeInTheDocument();
  });
});

describe('punto 3: una pantalla con datos y MSW (éxito, vacío, error y reintento)', () => {
  const servidor = setupServer();
  beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
  afterEach(() => servidor.resetHandlers());
  afterAll(() => servidor.close());
  const dto = { id: 1, estado: 2, valor_centavos: 12000, fecha_creacion: '2026-04-01T10:00:00Z', cliente: { nombre_completo: 'Ana Gómez' } };

  it('éxito', async () => {
    servidor.use(http.get('/api/pedidos', () => HttpResponse.json([dto])));
    renderConProveedores(<PaginaPedidos />);
    expect(screen.getByText('Cargando…')).toBeInTheDocument();
    expect(await screen.findByText('Ana Gómez · pagado')).toBeInTheDocument();
  });
  it('vacío: la lista no tiene elementos y no hay alerta', async () => {
    servidor.use(http.get('/api/pedidos', () => HttpResponse.json([])));
    renderConProveedores(<PaginaPedidos />);
    await waitFor(() => expect(screen.queryByText('Cargando…')).not.toBeInTheDocument());
    expect(screen.queryAllByRole('listitem')).toHaveLength(0);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
  it('error y reintento: falla, avisa, y al reintentar muestra los datos', async () => {
    let llamadas = 0;
    servidor.use(http.get('/api/pedidos', () => (++llamadas === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([dto]))));
    const usuario = userEvent.setup();
    renderConProveedores(<PaginaPedidos />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudieron cargar/i);
    await usuario.click(screen.getByRole('button', { name: /reintentar/i }));
    expect(await screen.findByText('Ana Gómez · pagado')).toBeInTheDocument();
    expect(llamadas).toBe(2);
  });
  it('respuesta con forma inesperada: la validación de la frontera la convierte en error', async () => {
    servidor.use(http.get('/api/pedidos', () => HttpResponse.json([{ id: 'uno' }])));
    renderConProveedores(<PaginaPedidos />);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});

describe('punto 4: acción de servidor con validación y autorización', () => {
  const ID = '3f1c2b6e-8a8e-4d0b-9a55-0d0c1d2e3f40';
  const pedido: PedidoRegistro = { id: ID, clienteId: 'u-1', estado: 'pagado', creadoEn: new Date('2026-04-01T10:00:00Z') };
  function armar(sobre: Partial<Dependencias> = {}) {
    const d: Dependencias = {
      usuarioActual: async () => ({ id: 'u-1' }), buscar: async (id) => (id === ID ? pedido : null),
      marcarCancelado: vi.fn(async () => {}), invalidar: vi.fn(), ahora: () => new Date('2026-04-01T12:00:00Z'), ...sobre,
    };
    return { d, cancelar: crearCancelarPedido(d) };
  }
  it('camino feliz: cancela e invalida la etiqueta del usuario', async () => {
    const { d, cancelar } = armar();
    expect(await cancelar(ID)).toEqual({ ok: true });
    expect(d.marcarCancelado).toHaveBeenCalledWith(ID);
    expect(d.invalidar).toHaveBeenCalledWith('pedidos-u-1');
  });
  it('sin sesión: no toca nada', async () => {
    const { d, cancelar } = armar({ usuarioActual: async () => null });
    expect(await cancelar(ID)).toEqual({ ok: false, error: 'Debes iniciar sesión' });
    expect(d.marcarCancelado).not.toHaveBeenCalled();
  });
  it.each([[42], [undefined], ['no-es-uuid'], [{ id: ID }]])('entrada inválida %j', async (entrada) => {
    const { d, cancelar } = armar();
    expect(await cancelar(entrada)).toEqual({ ok: false, error: 'Pedido inválido' });
    expect(d.marcarCancelado).not.toHaveBeenCalled();
  });
  it('pedido ajeno: responde igual que si no existiera', async () => {
    const { d, cancelar } = armar({ usuarioActual: async () => ({ id: 'u-2' }) });
    expect(await cancelar(ID)).toEqual({ ok: false, error: 'Pedido no encontrado' });
    expect(d.marcarCancelado).not.toHaveBeenCalled();
    expect(d.invalidar).not.toHaveBeenCalled();
  });
  it('fuera de plazo', async () => {
    const { d, cancelar } = armar({ ahora: () => new Date('2026-04-05T00:00:00Z') });
    expect(await cancelar(ID)).toEqual({ ok: false, error: 'El pedido ya no se puede cancelar' });
    expect(d.marcarCancelado).not.toHaveBeenCalled();
  });
});
