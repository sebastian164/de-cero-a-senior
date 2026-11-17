// Firma y verificación de la sesión, sin Next.js: se prueba sola. HMAC-SHA256 con comparación en tiempo constante.
import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto';

export interface DatosSesion { readonly sid: string; readonly uid: string; readonly exp: number }

const b64 = (b: Buffer | string) => Buffer.from(b).toString('base64url');

export function firmar(datos: DatosSesion, secreto: string): string {
  const cuerpo = b64(JSON.stringify(datos));
  return `${cuerpo}.${b64(createHmac('sha256', secreto).update(cuerpo).digest())}`;
}

export function verificar(valor: string | undefined, secreto: string, ahora = Date.now()): DatosSesion | null {
  if (!valor) return null;
  const [cuerpo, firma] = valor.split('.');
  if (!cuerpo || !firma) return null;
  const esperada = createHmac('sha256', secreto).update(cuerpo).digest();
  const recibida = Buffer.from(firma, 'base64url');
  if (recibida.length !== esperada.length || !timingSafeEqual(recibida, esperada)) return null;
  try {
    const datos = JSON.parse(Buffer.from(cuerpo, 'base64url').toString()) as DatosSesion;
    return typeof datos.uid === 'string' && typeof datos.exp === 'number' && datos.exp > ahora ? datos : null;
  } catch {
    return null;
  }
}

export const nuevaSesion = (uid: string, minutos: number, ahora = Date.now()): DatosSesion => ({ sid: randomUUID(), uid, exp: ahora + minutos * 60_000 });

/** Exige un secreto de al menos 32 caracteres; sin él la aplicación no firma sesiones. */
export function secretoDeSesion(): string {
  const s = process.env.SESION_SECRETO;
  if (!s || s.length < 32) throw new Error('SESION_SECRETO debe tener al menos 32 caracteres');
  return s;
}
