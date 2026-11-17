// Carga mixta del taller 05: lectura, escritura y llamada externa, repartida entre instancias. Variables:
// BASES (http://localhost:p1,http://localhost:p2...), RATE (peticiones/s), DURACION, HIST y FUGA (porcentaje de peticiones a esos extremos).
import http from 'k6/http';

const bases = (__ENV.BASES || '').split(',');
const hist = Number(__ENV.HIST || 0);
const fuga = Number(__ENV.FUGA || 0);
export const options = {
  scenarios: {
    mixta: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 200), timeUnit: '1s', duration: __ENV.DURACION || '60s',
      preAllocatedVUs: 100, maxVUs: 1500,
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
  discardResponseBodies: true,
  thresholds: { 'http_req_duration{expected_response:false}': ['max>=0'] },
};

export default function () {
  const base = bases[Math.floor(Math.random() * bases.length)];
  const id = 1 + Math.floor(Math.random() * 100000);
  const sorteo = Math.random() * 100;
  const params = { timeout: '30s' };
  if (sorteo < hist) {
    http.get(`${base}/historial/${1 + Math.floor(Math.random() * 100000)}`, params);
  } else if (sorteo < hist + fuga) {
    http.get(`${base}/fuga/${Math.random() < 0.02 ? -id : id}`, params);   // una de cada cincuenta por el camino raro
  } else if (sorteo < hist + fuga + 10) {
    http.post(`${base}/externo/${id}`, null, params);
  } else if (sorteo < hist + fuga + 28) {
    http.post(`${base}/escritura/${id}`, null, params);
  } else {
    http.get(`${base}/lectura/${id}`, params);
  }
}
