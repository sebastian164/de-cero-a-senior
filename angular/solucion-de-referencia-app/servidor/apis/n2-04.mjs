import { enrutador } from '../util.mjs';
export const manejador = enrutador({
  'GET /pedidos/conteo': () => ({ cuerpo: { total: 7 } }),
  'GET /pedidos': () => ({ cuerpo: [{ id: 'p1', cliente: 'Ana' }] }),
});
