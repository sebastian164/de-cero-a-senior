// Carga genérica: VUS usuarios concurrentes durante DURACION contra K6_URL.
// K6_URL admite varias direcciones separadas por comas: cada usuario virtual usa una, en rueda (varias instancias).
// Si la dirección lleva {id}, cada petición lo cambia por un número al azar entre 1 e ID_MAX. METODO=POST envía POST.
import http from 'k6/http';

export const options = {
  vus: Number(__ENV.VUS || 50),
  duration: __ENV.DURACION || '60s',
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
  discardResponseBodies: true,
  // umbral que siempre se cumple: solo sirve para que el resumen traiga la duración de las peticiones fallidas
  thresholds: { 'http_req_duration{expected_response:false}': ['max>=0'] },
};

const urls = (__ENV.K6_URL || '').split(',');
const idMax = Number(__ENV.ID_MAX || 1000);

export default function () {
  const url = urls[(__VU - 1) % urls.length].replace('{id}', String(1 + Math.floor(Math.random() * idMax)));
  const params = { timeout: __ENV.TIMEOUT || '60s' };
  if (__ENV.METODO === 'POST') {
    http.post(url, null, params);
  } else {
    http.get(url, params);
  }
}
