import { enrutador, estado } from '../util.mjs';

// Pedidos en formato del servidor (DTO). La latencia del detalle depende del id para provocar carreras.
const e = estado({
  pedidos: [
    { id: 1, cliente_nombre: 'Ana Gómez', valor_total: 238000 },
    { id: 2, cliente_nombre: 'Luis Pérez', valor_total: 120000 },
    { id: 3, cliente_nombre: 'Marta Ruiz', valor_total: 45000 },
  ],
});
const LATENCIA = { 1: 1500, 2: 100, 3: 400 };
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'GET /pedidos': ({ url }) => {
    const q = (url.searchParams.get('q') ?? '').toLowerCase();
    return { cuerpo: e.valor.pedidos.filter((p) => p.cliente_nombre.toLowerCase().includes(q)), demora: 200 };
  },
  'GET /pedidos/:id': ({ params }) => {
    const p = e.valor.pedidos.find((x) => String(x.id) === params.id);
    return p ? { cuerpo: p, demora: LATENCIA[p.id] ?? 200 } : { estado: 404, cuerpo: { error: 'no existe' } };
  },
  'GET /pedidos/:id/historial': ({ params }) => ({ cuerpo: [`Pedido ${params.id} creado`, `Pedido ${params.id} pagado`] }),
  'DELETE /pedidos/:id': ({ params }) => {
    e.valor.pedidos = e.valor.pedidos.filter((p) => String(p.id) !== params.id);
    return { estado: 204, demora: 200 };
  },
});
