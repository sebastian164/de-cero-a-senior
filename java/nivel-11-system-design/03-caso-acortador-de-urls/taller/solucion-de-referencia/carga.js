// Redirecciones con sesgo hacia pocos enlaces populares (lo habitual). No sigue la redirección: mide el servicio.
import http from 'k6/http';
import { check } from 'k6';
export const options = { vus: Number(__ENV.VUS || 32), duration: __ENV.DUR || '10s' };
export default function () {
  const n = 1 + Math.floor(Math.pow(Math.random(), 4) * 100000);
  const r = http.get(`${__ENV.BASE}/k${n}`, { redirects: 0 });
  check(r, { 'redirige (302)': (x) => x.status === 302 });
}
