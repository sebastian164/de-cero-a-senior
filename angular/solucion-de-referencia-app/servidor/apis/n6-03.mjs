import { enrutador } from '../util.mjs';
const TOKEN_ADMIN = 'token-de-admin-emitido-por-el-servidor';
export const manejador = enrutador({
  'GET /comentarios': () => ({ cuerpo: [
    { autor: 'ana', texto: 'Muy buen producto' },
    { autor: 'mal', texto: '<img src=x onerror="window.__xss=(window.__xss||0)+1">Aviso' },
  ] }),
  // El servidor es quien decide: la guarda del cliente no cuenta.
  'POST /sesion': ({ cuerpo }) => cuerpo?.usuario === 'admin' && cuerpo?.clave === 'admin123' ? { cuerpo: { token: TOKEN_ADMIN } } : { estado: 401, cuerpo: { error: 'credenciales' } },
  'GET /secreto': ({ cabeceras }) => cabeceras.authorization === `Bearer ${TOKEN_ADMIN}` ? { cuerpo: { secreto: 'ventas del trimestre: 12 M' } } : { estado: 403, cuerpo: { error: 'prohibido' } },
});
