import { enrutador, estado } from '../util.mjs';
// Catálogo y carrito de la tienda de la sesión de E2E.
const e = estado({
  catalogo: [{ id: 1, nombre: 'Teclado', precio: 120000 }, { id: 2, nombre: 'Mouse', precio: 60000 }],
  pedidos: 0,
});
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'GET /catalogo': () => ({ cuerpo: e.valor.catalogo, demora: 50 }),
  'POST /pedidos': ({ cuerpo, url }) => (url.searchParams.get('fallar') === '1' || cuerpo?.fallar
    ? { estado: 500, cuerpo: { error: 'falla simulada' }, demora: 100 }
    : (e.valor.pedidos++, { estado: 201, cuerpo: { id: e.valor.pedidos }, demora: 100 })),
  'GET /pedidos/total': () => ({ cuerpo: { total: e.valor.pedidos } }),
});
