import { enrutador, estado } from '../util.mjs';
const hace = (h) => new Date(Date.now() - h * 3600_000).toISOString();
// DTO del servidor: fecha_creacion ISO, estado numérico (1 pendiente, 2 pagado, 3 enviado, 4 cancelado).
const e = estado({
  pedidos: [
    { id: 1, fecha_creacion: hace(2), estado: 1, lineas: [{ producto_id: 'a', cantidad: 1 }] },
    { id: 2, fecha_creacion: hace(30), estado: 2, lineas: [{ producto_id: 'b', cantidad: 2 }] },
    { id: 3, fecha_creacion: hace(1), estado: 3, lineas: [{ producto_id: 'c', cantidad: 1 }] },
  ],
  solicitudes: [],
});
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'GET /pedidos': () => ({ cuerpo: e.valor.pedidos, demora: 50 }),
  'POST /pedidos/:id/cancelacion': ({ params }) => {
    const p = e.valor.pedidos.find((x) => String(x.id) === params.id);
    if (!p) return { estado: 404, cuerpo: {} };
    // El servidor vuelve a validar: no confía en la regla del cliente.
    if (Date.now() - new Date(p.fecha_creacion).getTime() > 24 * 3600_000 || p.estado > 2) return { estado: 409, cuerpo: { error: 'no cancelable' } };
    p.estado = 4;
    return { estado: 204 };
  },
  'POST /solicitudes': ({ cuerpo }) => {
    if (cuerpo.nombre === 'Error500') return { estado: 500, cuerpo: {} };   // para probar el error general
    if (cuerpo.monto > 50_000_000) return { estado: 422, cuerpo: { campos: { monto: 'El monto máximo es 50.000.000' } } };
    e.valor.solicitudes.push(cuerpo);
    return { estado: 201, cuerpo: { id: e.valor.solicitudes.length } };
  },
  'GET /solicitudes': () => ({ cuerpo: e.valor.solicitudes }),
});
