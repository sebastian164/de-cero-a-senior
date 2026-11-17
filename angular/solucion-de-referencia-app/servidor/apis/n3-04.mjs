import { enrutador, estado } from '../util.mjs';
const e = estado({ guardados: [], envios: 0, sondeos: 0, fallar: { contacto: false, estado: false } });
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  // Falla al azar (30 %), salvo que se pida ?fallar=0 o ?fallar=1.
  'POST /documentos': ({ cuerpo, url }) => {
    const f = url.searchParams.get('fallar');
    const falla = f === null ? Math.random() < 0.3 : f === '1';
    if (falla) return { estado: 503, cuerpo: {}, demora: 150 };
    e.valor.guardados.push(cuerpo);
    return { cuerpo: { version: e.valor.guardados.length }, demora: 150 };
  },
  // Interruptor para las pruebas: { contacto: true } hace que POST /contacto y GET /estado respondan 500.
  'POST /_fallar': ({ cuerpo }) => { e.valor.fallar = { contacto: !!cuerpo.contacto, estado: !!cuerpo.estado }; return { cuerpo: e.valor.fallar }; },
  'POST /contacto': () => { if (e.valor.fallar.contacto) return { estado: 500, cuerpo: {}, demora: 100 }; e.valor.envios++; return { estado: 201, cuerpo: { numero: e.valor.envios }, demora: 800 }; },
  'GET /tablero/ventas': () => ({ cuerpo: { total: 1200000 }, demora: 300 }),
  'GET /tablero/clientes': () => ({ cuerpo: { nuevos: 14 }, demora: 200 }),
  'GET /tablero/recomendaciones': () => ({ estado: 500, cuerpo: {}, demora: 100 }),
  'GET /estado': () => { if (e.valor.fallar.estado) return { estado: 500, cuerpo: {}, demora: 50 }; e.valor.sondeos++; return { cuerpo: { sondeos: e.valor.sondeos } }; },
  'GET /_contadores': () => ({ cuerpo: { envios: e.valor.envios, sondeos: e.valor.sondeos, guardados: e.valor.guardados.length } }),
});
