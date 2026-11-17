'use server';
// Ingreso con validación, límite de tasa por IP y por correo, y retorno validado.
import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import { z } from 'zod';
import { limiteDeIngreso } from './limite';
import { retornoSeguro } from './retorno';
import { cerrarSesion, crearSesion } from './sesion';
import { comprobarCredenciales } from './usuarios';

export interface EstadoIngreso { error?: string }

const Entrada = z.object({ correo: z.email(), clave: z.string().min(1).max(200) });

export async function ingresar(_previo: EstadoIngreso, datos: FormData): Promise<EstadoIngreso> {
  const ip = (await headers()).get('x-forwarded-for')?.split(',')[0]?.trim() || 'local';
  const entrada = Entrada.safeParse({ correo: datos.get('correo'), clave: datos.get('clave') });
  const correo = entrada.success ? entrada.data.correo.toLowerCase() : 'invalido';
  for (const clave of [`ip:${ip}`, `correo:${correo}`]) {
    const r = limiteDeIngreso().intentar(clave);
    if (!r.permitido) return { error: `Demasiados intentos. Intenta de nuevo en ${Math.ceil(r.reintentarEnMs / 1000)} s.` };
  }
  if (!entrada.success) return { error: 'Correo o clave incorrectos' };
  const usuario = comprobarCredenciales(entrada.data.correo, entrada.data.clave);
  if (!usuario) return { error: 'Correo o clave incorrectos' };          // mismo mensaje: no revela si el correo existe
  await crearSesion(usuario.id);
  redirect(retornoSeguro(datos.get('retorno')));
}

export async function salir(): Promise<void> {
  await cerrarSesion();
  redirect('/n6/02-seguridad');
}
