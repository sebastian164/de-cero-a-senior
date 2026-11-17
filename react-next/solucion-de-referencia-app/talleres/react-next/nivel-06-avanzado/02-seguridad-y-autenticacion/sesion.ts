// Sesión en cookie HttpOnly, Secure y SameSite=Lax, firmada; vida limitada y cierre efectivo en el servidor.
import 'server-only';
import { cache } from 'react';
import { cookies } from 'next/headers';
import { firmar, nuevaSesion, secretoDeSesion, verificar } from './firma';
import { buscarUsuarioPorId, type Usuario } from './usuarios';

export const COOKIE_SESION = 'sesion';
const MINUTOS = 30;
const global_ = globalThis as unknown as { sesionesRevocadas?: Set<string> };
const revocadas = (global_.sesionesRevocadas ??= new Set<string>());

export async function crearSesion(usuarioId: string): Promise<void> {
  const datos = nuevaSesion(usuarioId, MINUTOS);
  (await cookies()).set(COOKIE_SESION, firmar(datos, secretoDeSesion()), {
    httpOnly: true, secure: true, sameSite: 'lax', path: '/', maxAge: MINUTOS * 60,
  });
}

export async function cerrarSesion(): Promise<void> {
  const almacen = await cookies();
  const datos = verificar(almacen.get(COOKIE_SESION)?.value, secretoDeSesion());
  if (datos) revocadas.add(datos.sid);            // aunque alguien guardó la cookie, ya no sirve
  almacen.delete(COOKIE_SESION);
}

/** Memorizada por petición: el diseño, la página y la capa de datos la piden sin repetir el trabajo. */
export const obtenerUsuarioActual = cache(async (): Promise<Usuario | null> => {
  const datos = verificar((await cookies()).get(COOKIE_SESION)?.value, secretoDeSesion());
  if (!datos || revocadas.has(datos.sid)) return null;
  return buscarUsuarioPorId(datos.uid);
});
