import { enrutador } from '../util.mjs';
export const manejador = enrutador({ 'POST /recordatorios': () => ({ estado: 201, cuerpo: { ok: true } }) });
