import { enrutador } from '../util.mjs';
export const manejador = enrutador({ 'GET /dato': () => ({ cuerpo: { valor: 'del servidor' }, demora: 50 }) });
