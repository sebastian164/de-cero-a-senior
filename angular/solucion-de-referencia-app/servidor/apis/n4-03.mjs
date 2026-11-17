import { enrutador, estado } from '../util.mjs';
// DTO del servidor: estado numérico y valor en centavos.
const e = estado({ pedidos: [
  { id: 7, estado: 1, valor_centavos: 12000, cliente: 'Ana' },
  { id: 8, estado: 2, valor_centavos: 8000, cliente: 'Luis' },
  { id: 9, estado: 1, valor_centavos: 5000, cliente: 'Marta' },
] });
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'GET /pedidos': ({ url }) => (url.searchParams.get('fallar') === '1' ? { estado: 500, cuerpo: {} } : { cuerpo: e.valor.pedidos, demora: 100 }),
  'GET /buscar': ({ url }) => {
    const q = (url.searchParams.get('q') ?? '').toLowerCase();
    return { cuerpo: e.valor.pedidos.filter((p) => p.cliente.toLowerCase().includes(q)).map((p) => p.cliente), demora: 50 };
  },
  'DELETE /pedidos/:id': ({ params }) => (params.id === '9' ? { estado: 409, cuerpo: {}, demora: 300 }
    : (e.valor.pedidos = e.valor.pedidos.filter((p) => String(p.id) !== params.id), { estado: 204, demora: 300 })),
});
