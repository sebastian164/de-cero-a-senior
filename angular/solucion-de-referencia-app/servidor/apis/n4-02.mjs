import { enrutador, estado } from '../util.mjs';
const e = estado({ usuarios: ['ana@correo.co'], consultas: 0, registros: [], fallarRegistro: false });
export const reiniciar = e.reiniciar;
export const manejador = enrutador({
  'GET /usuarios/existe': ({ url }) => {
    e.valor.consultas++;
    const c = url.searchParams.get('correo') ?? '';
    if (c.startsWith('caido')) return { estado: 503, cuerpo: {}, demora: 100 };
    return { cuerpo: { existe: e.valor.usuarios.includes(c) }, demora: 100 };
  },
  'GET /consultas': () => ({ cuerpo: { consultas: e.valor.consultas } }),
  'POST /usuarios': ({ cuerpo }) => {
    if (e.valor.fallarRegistro) return { estado: 500, cuerpo: {}, demora: 100 };
    if (cuerpo?.correo === 'tomado@correo.co') return { estado: 422, cuerpo: { campo: 'correo', mensaje: 'El servidor rechazó este correo' }, demora: 100 };
    e.valor.registros.push(cuerpo); e.valor.usuarios.push(cuerpo.correo);
    return { estado: 201, cuerpo: { id: e.valor.registros.length }, demora: 100 };
  },
  'GET /registros': () => ({ cuerpo: { registros: e.valor.registros.length } }),
  'POST /fallo': ({ cuerpo }) => { e.valor.fallarRegistro = !!cuerpo?.activo; return { estado: 204 }; },
});
