// ---- react-next/nivel-04-clean-code-y-testing/02-formularios-y-validacion/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
// registro/acciones.ts
'use server';

export interface EstadoRegistro {
  errores?: Partial<Record<keyof DatosRegistro, string[]>>;
  mensaje?: string;
  valores?: Partial<DatosRegistro>;
}

export async function registrar(_previo: EstadoRegistro, datos: FormData): Promise<EstadoRegistro> {
  const entrada = Object.fromEntries(datos);
  const resultado = EsquemaRegistro.safeParse(entrada);            // el servidor valida SIEMPRE

  if (!resultado.success) {
    return { errores: resultado.error.flatten().fieldErrors, valores: entrada as Partial<DatosRegistro> };
  }
  if (await existeCorreo(resultado.data.correo)) {
    return { errores: { correo: ['Ese correo ya está registrado'] }, valores: resultado.data };
  }
  await crearUsuario(resultado.data);
  redirect('/bienvenida');
}

import { redirect } from 'next/navigation';
import { EsquemaRegistro, type DatosRegistro } from './esquema';
import { crearUsuario, existeCorreo } from './usuarios';
