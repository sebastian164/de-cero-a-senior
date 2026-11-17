'use server';
// Contexto: registrarEnServidor, la acción que usa el formulario con biblioteca. Valida de nuevo y no redirige.
import { EsquemaRegistro, type DatosRegistro } from './esquema';
import { crearUsuario, existeCorreo } from './usuarios';
import type { EstadoRegistro } from './acciones';

export async function registrarEnServidor(datos: DatosRegistro): Promise<EstadoRegistro> {
  const resultado = EsquemaRegistro.safeParse(datos);
  if (!resultado.success) return { errores: resultado.error.flatten().fieldErrors };
  if (await existeCorreo(resultado.data.correo)) return { errores: { correo: ['Ese correo ya está registrado'] } };
  await crearUsuario(resultado.data);
  return { mensaje: 'Registro completo' };
}
