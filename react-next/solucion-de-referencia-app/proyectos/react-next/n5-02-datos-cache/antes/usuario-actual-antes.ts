// Apoyo (no está en el README): la sesión y la búsqueda, en memoria.
export const llamadas = { sesion: 0, buscar: 0 };
export const apoyo = { usuarioId: 'u-1' as string | null };
async function leerSesion() { llamadas.sesion++; return apoyo.usuarioId ? { usuarioId: apoyo.usuarioId } : null; }
async function buscarUsuario(id: string) { llamadas.buscar++; return { id, nombre: 'Ana' }; }

import 'server-only';
import { cache } from 'react';

export const obtenerUsuarioActual = cache(async () => {
  const sesion = await leerSesion();
  return sesion ? buscarUsuario(sesion.usuarioId) : null;
});
