import { enrutador } from '../util.mjs';
export const manejador = enrutador({
  'GET /pedidos/:id': ({ params }) => params.id === '99'
    ? { estado: 500, cuerpo: { error: 'falla' }, demora: 100 }
    : { cuerpo: { id: params.id, dueno: 'u1', cerrado: false, items: [['Teclado mecánico', 120000, 1], ['Teclado de viaje', 80000, 2], ['Monitor', 900000, 1]].map(([nombre, p, c]) => ({ nombre, p, c, precio: p, cantidad: c, activo: true })) }, demora: 200 },
});
