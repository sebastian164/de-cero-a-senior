import { enrutador, estado } from '../util.mjs';
const e = estado({
  pedidos: [
    { id: 'p1', cliente: 'Ana', estado: 'pendiente', total: 120000 },
    { id: 'p2', cliente: 'Luis', estado: 'pagado', total: 80000 },
    { id: 'p3', cliente: 'Marta', estado: 'pendiente', total: 30000 },
  ],
  carrito: [],
  fallarCarrito: false,
});
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'GET /pedidos': () => ({ cuerpo: e.valor.pedidos, demora: 200 }),
  'POST /pedidos/:id/cancelacion': ({ params }) => (params.id === 'p3'
    ? { estado: 409, cuerpo: { error: 'ya despachado' }, demora: 400 }
    : (e.valor.pedidos = e.valor.pedidos.filter((p) => p.id !== params.id), { estado: 204, demora: 400 })),
  'PUT /carrito': ({ cuerpo, url }) => (url.searchParams.get('fallar') === '1'
    ? { estado: 503, cuerpo: {}, demora: 300 }
    : (e.valor.carrito = cuerpo, { cuerpo, demora: 300 })),
});
