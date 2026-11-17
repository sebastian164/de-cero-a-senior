// Usuarios de la demostración con clave derivada con scrypt (nunca en claro).
import 'server-only';
import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

export interface Usuario { readonly id: string; readonly nombre: string; readonly correo: string; readonly esAdministrador: boolean }
interface Registro extends Usuario { readonly sal: Buffer; readonly hash: Buffer }

const derivar = (clave: string, sal: Buffer) => scryptSync(clave, sal, 32, { N: 1024 });
function registro(u: Usuario, clave: string): Registro {
  const sal = randomBytes(16);
  return { ...u, sal, hash: derivar(clave, sal) };
}
const USUARIOS: readonly Registro[] = [
  registro({ id: 'u-ana', nombre: 'Ana', correo: 'ana@correo.example', esAdministrador: false }, 'clave-de-ana-123'),
  registro({ id: 'u-luis', nombre: 'Luis', correo: 'luis@correo.example', esAdministrador: false }, 'clave-de-luis-123'),
  registro({ id: 'u-admin', nombre: 'Admin', correo: 'admin@correo.example', esAdministrador: true }, 'clave-de-admin-123'),
];
const publico = ({ id, nombre, correo, esAdministrador }: Registro): Usuario => ({ id, nombre, correo, esAdministrador });

export function buscarUsuarioPorId(id: string): Usuario | null {
  const r = USUARIOS.find((u) => u.id === id);
  return r ? publico(r) : null;
}
/** Compara siempre contra un hash, exista o no el correo, para no revelar qué correos existen por el tiempo de respuesta. */
export function comprobarCredenciales(correo: string, clave: string): Usuario | null {
  const r = USUARIOS.find((u) => u.correo === correo.toLowerCase());
  const sal = r?.sal ?? Buffer.alloc(16);
  const calculado = derivar(clave, sal);
  const ok = r ? timingSafeEqual(calculado, r.hash) : false;
  return ok && r ? publico(r) : null;
}
