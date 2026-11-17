// Carga contra la tienda del taller 06: "crear pedido" de un producto al azar, a una tasa fija de peticiones por segundo.
// Variables: BASE (http://localhost:puerto), RATE (peticiones/s), DURACION, PRODUCTOS (cuántos hay: p-0000 ...).
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const bien = new Counter('pedido_ok');
const mal = new Counter('pedido_error');
const conflicto = new Counter('pedido_409');
const duracion = new Trend('pedido_ms', true);

const productos = Number(__ENV.PRODUCTOS || 500);
export const options = {
  scenarios: {
    pedidos: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 300), timeUnit: '1s', duration: __ENV.DURACION || '20s',
      preAllocatedVUs: 200, maxVUs: 1500,
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
  discardResponseBodies: false,
  thresholds: { pedido_ms: ['max>=0'] },
};

export default function () {
  const n = Math.floor(Math.random() * productos);
  const clave = `${__VU}-${__ITER}-${Date.now()}-${Math.random()}`;
  const r = http.post(`${__ENV.BASE}/pedidos`, { cliente: `cliente-${__VU}`, producto: `p-${String(n).padStart(4, '0')}`, cantidad: 1 },
    { headers: { 'Idempotency-Key': clave }, timeout: '30s' });
  duracion.add(r.timings.duration);
  if (r.status === 200) bien.add(1); else { mal.add(1); if (r.status === 409) conflicto.add(1); }
}
