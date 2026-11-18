// Carga de lectura del reporte por cliente. Los clientes se eligen con sesgo (unos pocos concentran el tráfico),
// que es lo habitual y lo que hace útil una caché.
import http from 'k6/http';
import { check } from 'k6';
export const options = { vus: Number(__ENV.VUS || 16), duration: __ENV.DUR || '10s' };
export default function () {
  const c = 1 + Math.floor(Math.pow(Math.random(), 3) * Number(__ENV.CLIENTES || 50000));
  const r = http.get(`${__ENV.BASE}/api/pedidos/reporte/${c}`);
  check(r, { 'estado 200': (x) => x.status === 200 });
}
