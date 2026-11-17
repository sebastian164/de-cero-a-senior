import { enrutador, estado } from '../util.mjs';
const e = estado({ intentos: 0, pedidos: [{ id: 1, cliente_nombre: 'Ana Gómez', valor_total: 238000 }, { id: 2, cliente_nombre: 'Luis Pérez', valor_total: 120000 }] });
export const reiniciar = e.reiniciar;
const DEMORA = 300;
export const manejador = enrutador({
  'GET /pedidos/:id': ({ params }) => {
    if (params.id === '404') return { estado: 404, cuerpo: {}, demora: 50 };
    if (params.id === '500') return { estado: 500, cuerpo: {}, demora: 50 };
    if (params.id === 'inestable') { e.valor.intentos++; return e.valor.intentos < 3 ? { estado: 503, cuerpo: {}, demora: 20 } : { cuerpo: { id: 9, cliente_nombre: 'Tras reintentos', valor_total: 1 } }; }
    if (params.id === 'colgado') return { cuerpo: {}, demora: 12000 };
    const p = e.valor.pedidos.find((x) => String(x.id) === params.id);
    return p ? { cuerpo: p, demora: 50 } : { estado: 404, cuerpo: {} };
  },
  'GET /intentos': () => ({ cuerpo: { intentos: e.valor.intentos } }),
  'GET /pedidos': ({ url }) => (url.searchParams.get('vacio') === '1' ? { cuerpo: [], demora: DEMORA }
    : url.searchParams.get('fallar') === '1' ? { estado: 500, cuerpo: {}, demora: 100 } : { cuerpo: e.valor.pedidos, demora: DEMORA }),
  'POST /pedidos': ({ cuerpo }) => (cuerpo?.cliente ? { estado: 201, cuerpo: { id: 3 }, demora: 100 } : { estado: 422, cuerpo: { campos: { cliente: 'El cliente es obligatorio' } }, demora: 100 }),
  // Para la cascada: tres recursos que tardan DEMORA ms cada uno.
  'GET /usuario': () => ({ cuerpo: { id: 'u1', nombre: 'Ana' }, demora: DEMORA }),
  'GET /usuario/:id/pedidos': () => ({ cuerpo: [{ id: 1 }], demora: DEMORA }),
  'GET /recomendaciones': () => ({ cuerpo: ['Teclado'], demora: DEMORA }),
});
