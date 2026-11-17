import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse, delay } from 'msw';
import { setupServer } from 'msw/node';
import { ZodError } from 'zod';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { aPedido, listarPedidos } from '@/react-next/n3-04-datos/pedidos/datos/esquema';
import { NoEncontrado, SinPermiso, aErrorDeDominio } from '@/react-next/n3-04-datos/pedidos/datos/errores';
import { CLIENTES_INICIALES, PEDIDOS_INICIALES, cancelarDto, estadoApi, listarDto } from './api-simulada';
import { aCliente, listarClientes } from './clientes';
import { ContadorPendientes, ListaCancelable } from './ui';

const servidor = setupServer(
  http.get('*/api/n3-04/pedidos', async ({ request }) => { await delay(20); return HttpResponse.json(listarDto(new URL(request.url).searchParams)); }),
  http.delete('*/api/n3-04/pedidos/:id', async ({ params }) => {
    await delay(50);
    const r = cancelarDto(String(params.id));
    return new HttpResponse(null, { status: r === 'ok' ? 204 : r === 'no-existe' ? 404 : 503 });
  }),
  http.get('*/api/n3-04/clientes', () => HttpResponse.json(CLIENTES_INICIALES)),
);
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  servidor.resetHandlers();
  Object.assign(estadoApi, { pedidos: structuredClone(PEDIDOS_INICIALES), fallarCancelacion: false, malformado: false, peticiones: 0 });
});
afterAll(() => servidor.close());

const conCliente = (ui: React.ReactNode) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>);

describe('nivel-03/04 · punto 1: dos entidades con esquema, adaptador y acceso', () => {
  it('pedidos: el adaptador traduce el DTO al modelo', async () => {
    const pedidos = await listarPedidos({ estado: '1' });
    expect(pedidos.map((p) => p.id)).toEqual(['101', '103']);
    expect(pedidos[0]).toEqual({ id: '101', creadoEn: new Date('2026-04-01T10:00:00Z'), cliente: 'Ana Gómez', estado: 'pendiente', total: 120000 });
  });
  it('clientes: lo mismo con la segunda entidad', async () => {
    expect(await listarClientes()).toEqual([
      { documento: '1012', nombre: 'Ana Gómez', correo: 'ana@correo.example', activo: true },
      { documento: '2034', nombre: 'Luis Pérez', correo: 'luis@correo.example', activo: false },
    ]);
  });
  it('errores con significado según el estado HTTP', () => {
    expect(aErrorDeDominio(404)).toBeInstanceOf(NoEncontrado);
    expect(aErrorDeDominio(403)).toBeInstanceOf(SinPermiso);
    expect(aErrorDeDominio(500).message).toBe('Error 500');
  });
});

describe('nivel-03/04 · punto 2: cambiar un campo del DTO toca un solo archivo', () => {
  it('si el servidor renombra nombre_completo, solo cambia el adaptador; el modelo queda igual', () => {
    // Simulación del cambio: el adaptador (clientes.ts) es la única función que lee nombre_completo.
    const dtoNuevo = { documento: '1', nombre_completo: 'Ana', correo_electronico: 'a@x.example', activo: 1 as const };
    expect(Object.keys(aCliente(dtoNuevo))).toEqual(['documento', 'nombre', 'correo', 'activo']);
  });
});

describe('nivel-03/04 · punto 2 (cuenta real): en cuántos archivos del código de la aplicación aparece el nombre del campo del DTO', () => {
  it('nombre_completo solo se lee en clientes.ts; ni la interfaz ni los componentes lo conocen', () => {
    const archivos = readdirSync(__dirname).filter((a) => /\.(ts|tsx)$/.test(a) && a !== 'taller.test.tsx' && a !== 'api-simulada.ts');   // la API simulada es el servidor, no la aplicación
    const conElCampo = archivos.filter((a) => readFileSync(join(__dirname, a), 'utf8').includes('nombre_completo'));
    expect(conElCampo).toEqual(['clientes.ts']);
  });
});

describe('nivel-03/04 · punto 3: dato mal formado', () => {
  it('falla con un error claro que dice qué campo y qué se esperaba', async () => {
    estadoApi.malformado = true;
    const error = await listarPedidos({}).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ZodError);
    const primero = (error as ZodError).issues[0]!;
    expect(primero.path).toEqual([0, 'valor_centavos']);
    expect(primero.message).toMatch(/expected number, received string/);
  });
  it('aPedido con estado fuera de la unión no llega: el esquema lo rechaza antes', async () => {
    servidor.use(http.get('*/api/n3-04/pedidos', () => HttpResponse.json([{ ...PEDIDOS_INICIALES[0], estado: 9 }])));
    await expect(listarPedidos({})).rejects.toThrow(ZodError);
    expect(aPedido({ ...PEDIDOS_INICIALES[0]!, estado: 2 }).estado).toBe('pagado');
  });
});

describe('nivel-03/04 · punto 4: mutación optimista con reversa', () => {
  it('quita el pedido de inmediato y confirma', async () => {
    const u = userEvent.setup();
    conCliente(<ListaCancelable />);
    await u.click(await screen.findByRole('button', { name: 'Cancelar #101' }));
    expect(screen.queryByText(/#101/)).not.toBeInTheDocument(); // antes de que responda el servidor
    expect(await screen.findByRole('status')).toHaveTextContent('Pedido 101 cancelado');
    expect(estadoApi.pedidos.map((p) => p.id)).not.toContain(101);
  });
  it('si el servidor falla, la lista vuelve a como estaba', async () => {
    const u = userEvent.setup();
    estadoApi.fallarCancelacion = true;
    conCliente(<ListaCancelable />);
    await u.click(await screen.findByRole('button', { name: 'Cancelar #102' }));
    expect(screen.queryByText(/#102/)).not.toBeInTheDocument();
    expect(await screen.findByRole('status')).toHaveTextContent('No se pudo cancelar el pedido 102; se restauró la lista');
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'pedidos' })).getAllByRole('listitem')).toHaveLength(4));
  });
});

describe('nivel-03/04 · punto 5: dos componentes, una sola petición', () => {
  it('la misma clave de consulta se deduplica', async () => {
    conCliente(<><ListaCancelable /><ContadorPendientes /></>);
    expect(await screen.findByText('Pendientes: 2')).toBeInTheDocument();
    expect(estadoApi.peticiones).toBe(1);
  });
});
