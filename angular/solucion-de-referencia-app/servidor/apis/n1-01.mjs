import { enrutador, estado } from '../util.mjs';

const e = estado({
  pedidos: [
    { id: 'p1', cliente: 'Ana', items: [{ precio: 50000, cantidad: 2 }] },
    { id: 'p2', cliente: 'Luis', items: [{ precio: 120000, cantidad: 1 }] },
  ],
});
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'GET /pedidos': () => ({ cuerpo: e.valor.pedidos, demora: 300 }),
  'DELETE /pedidos/:id': ({ params }) => {
    e.valor.pedidos = e.valor.pedidos.filter((p) => p.id !== params.id);
    return { estado: 204 };
  },
});
