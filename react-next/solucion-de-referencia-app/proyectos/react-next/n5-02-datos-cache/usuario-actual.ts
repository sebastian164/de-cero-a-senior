// ---- react-next/nivel-05-arquitectura/02-datos-cache-y-mutaciones/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
import 'server-only';
import { cache } from 'react';

export const obtenerUsuarioActual = cache(async () => {
  const sesion = await leerSesion();
  return sesion ? buscarUsuario(sesion.usuarioId) : null;
});

import { buscarUsuario, leerSesion } from './contexto';
