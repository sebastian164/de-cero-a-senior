import { enrutador, estado } from '../util.mjs';

// Ejemplo n3-05: el servidor envía DTO con nombres propios (fecha_creacion, valor_centavos, estado numérico).
const PEDIDOS = [
  { id: 1, fecha_creacion: '2026-03-14T10:00:00Z', cliente: { nombre_completo: 'Ana Gómez' }, estado: 1, valor_centavos: 23800000 },
  { id: 2, fecha_creacion: '2026-03-15T10:00:00Z', cliente: { nombre_completo: 'Luis Pérez' }, estado: 2, valor_centavos: 12000000 },
];
// Taller n3-05: forma de JSONPlaceholder (/users, /posts), token que vence y renovación contada.
const USERS = [
  { id: 1, name: 'Leanne Graham', username: 'Bret', email: 'Sincere@april.biz', address: { city: 'Gwenborough' }, company: { name: 'Romaguera-Crona' } },
  { id: 2, name: 'Ervin Howell', username: 'Antonette', email: 'Shanna@melissa.tv', address: { city: 'Wisokyburgh' }, company: { name: 'Deckow-Crist' } },
];
const POSTS = [
  { userId: 1, id: 1, title: 'sunt aut facere', body: 'quia et suscipit\nsuscipit recusandae' },
  { userId: 1, id: 2, title: 'qui est esse', body: 'est rerum tempore\nvitae' },
  { userId: 2, id: 3, title: 'ea molestias', body: 'et iusto sed\nquo iure' },
];
const e = estado({ renovaciones: 0 });
export const reiniciar = e.reiniciar;
const tokenValido = (cabeceras) => /^Bearer valido-\d+$/.test(cabeceras.authorization ?? '');
export const manejador = enrutador({
  'GET /users': () => ({ cuerpo: USERS }),
  'GET /posts': () => ({ cuerpo: POSTS }),
  'GET /privado/:n': ({ params, cabeceras }) => (tokenValido(cabeceras) ? { cuerpo: { dato: Number(params.n) }, demora: 100 } : { estado: 401, cuerpo: {}, demora: 100 }),
  'POST /token/renovar': () => { e.valor.renovaciones++; return { cuerpo: { token: `valido-${e.valor.renovaciones}` }, demora: 300 }; },
  // Sin comprobar el token, a propósito: muestra que la guarda del frontend no protege el dato.
  'GET /admin/datos': () => ({ cuerpo: { sueldos: [4200000, 5100000] } }),
  // Con comprobación: así debe ser.
  'GET /admin/datos-protegidos': ({ cabeceras }) => (tokenValido(cabeceras) ? { cuerpo: { sueldos: [1] } } : { estado: 401, cuerpo: {} }),
  'GET /_contadores': () => ({ cuerpo: { renovaciones: e.valor.renovaciones } }),
  'GET /pedidos': () => ({ cuerpo: PEDIDOS }),
  'GET /pedidos/:id': ({ params }) => {
    const p = PEDIDOS.find((x) => String(x.id) === params.id);
    return p ? { cuerpo: p, demora: 300 } : { estado: 404, cuerpo: {} };
  },
  'GET /reportes': () => ({ estado: 503, cuerpo: {} }),
});
