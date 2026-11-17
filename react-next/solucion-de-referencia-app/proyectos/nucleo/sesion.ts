// Contexto (nivel-05/03): núcleo de sesión. La sesión firmada de verdad está en el taller nivel-06/02.
import 'server-only';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';

export interface UsuarioSesion { readonly id: string; readonly nombre: string }
const USUARIOS: Record<string, string> = { 'u-1': 'Ana', 'u-2': 'Luis' };

export async function usuarioOpcional(): Promise<UsuarioSesion | null> {
  const id = (await cookies()).get('usuario-demo')?.value;
  return id && USUARIOS[id] ? { id, nombre: USUARIOS[id] } : null;
}
export async function exigirUsuario(): Promise<UsuarioSesion> {
  const usuario = await usuarioOpcional();
  if (!usuario) redirect('/n5/03-arquitectura?ingreso=requerido');
  return usuario;
}
