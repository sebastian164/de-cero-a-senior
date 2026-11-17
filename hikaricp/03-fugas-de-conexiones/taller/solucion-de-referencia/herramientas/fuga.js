// Tráfico del taller 03: RATE peticiones por segundo; una de cada 50 lleva un id negativo (el camino raro).
// Escribe en el registro de k6 la primera petición que falla, con su número global.
import http from 'k6/http';
import exec from 'k6/execution';

export const options = {
  scenarios: {
    trafico: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 25), timeUnit: '1s', duration: __ENV.DURACION || '30s',
      preAllocatedVUs: 50, maxVUs: 300,
    },
  },
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
};

let avisado = false;

export default function () {
  const n = exec.scenario.iterationInTest + 1;
  const id = n % 50 === 0 ? -1 : (n % 100) + 1;
  const r = http.get(`${__ENV.BASE}/${id}`, { timeout: '30s' });
  if (r.status !== 200 && !avisado) {
    avisado = true;
    console.log(`PRIMER_FALLO peticion=${n} estado=${r.status}`);
  }
}
