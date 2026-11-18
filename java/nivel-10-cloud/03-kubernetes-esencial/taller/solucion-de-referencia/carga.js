// Carga constante (peticiones de MS ms, por defecto 8 s en el taller: siempre hay alguna en vuelo al apagar un pod) contra el Service (BASE, por defecto http://localhost:8081) mientras se reinicia el Deployment.
import http from 'k6/http';
import { check } from 'k6';
export const options = { vus: 20, duration: __ENV.DURACION || '60s' };
export default function () {
  const r = http.get('' + (__ENV.BASE || 'http://localhost:8081') + '/api/lento?ms=' + (__ENV.MS || 2000), { timeout: '30s' });
  if (!check(r, { 'estado 200': (x) => x.status === 200 })) {
    console.log(`fallo estado=${r.status} error=${r.error_code} ${r.error} duracion_ms=${Math.round(r.timings.duration)}`);
  }
}
