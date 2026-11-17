import { enrutador } from '../util.mjs';
export const manejador = enrutador({
  'GET /catalogo': () => ({ cuerpo: [{ id: 'a', nombre: 'Teclado', precio: 100000 }, { id: 'b', nombre: 'Mouse', precio: 50000 }], demora: 50 }),
  'GET /pedidos': () => ({ cuerpo: [{ id: 'p1', estado: 'pendiente', lineas: [{ productoId: 'a', cantidad: 1 }, { productoId: 'b', cantidad: 2 }] }, { id: 'p2', estado: 'pagado', lineas: [{ productoId: 'b', cantidad: 1 }] }], demora: 50 }),
});
