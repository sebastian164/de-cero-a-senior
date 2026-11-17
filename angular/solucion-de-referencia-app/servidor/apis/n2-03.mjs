import { enrutador, estado } from '../util.mjs';
const e = estado({ avisos: [] });
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'POST /notificaciones': ({ cuerpo }) => {
    if (!cuerpo?.mensaje) return { estado: 422, cuerpo: { error: 'mensaje vacío' } };
    e.valor.avisos.push(cuerpo.mensaje);
    return { estado: 201, cuerpo: { id: e.valor.avisos.length } };
  },
});
