// Contexto: almacén de usuarios en memoria para la acción de registro.
import 'server-only';
import type { DatosRegistro } from './esquema';

const usuarios = new Map<string, Omit<DatosRegistro, 'clave' | 'confirmacion'>>([['ana@correo.example', { correo: 'ana@correo.example', documento: '1012345' }]]);

export async function existeCorreo(correo: string): Promise<boolean> {
  return usuarios.has(correo.toLowerCase());
}

export async function crearUsuario(datos: DatosRegistro): Promise<void> {
  usuarios.set(datos.correo.toLowerCase(), { correo: datos.correo, documento: datos.documento });
}
