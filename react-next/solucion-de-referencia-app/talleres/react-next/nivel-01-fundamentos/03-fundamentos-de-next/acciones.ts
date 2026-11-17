'use server';
// Punto 4: crear un registro con una acción de servidor y revalidar la lista.
import { revalidatePath } from 'next/cache';
import { z } from 'zod';
import { guardarLibro } from './datos';

const EsquemaLibro = z.object({
  titulo: z.string().trim().min(2, 'El título debe tener al menos 2 caracteres'),
  autor: z.string().trim().min(3, 'Escribe el autor'),
  anio: z.coerce.number().int().min(1450, 'Año inválido').max(2100, 'Año inválido'),
});

export interface EstadoCrearLibro { readonly errores?: Partial<Record<'titulo' | 'autor' | 'anio', string[]>>; readonly creado?: string }

export async function crearLibro(_previo: EstadoCrearLibro, datos: FormData): Promise<EstadoCrearLibro> {
  const resultado = EsquemaLibro.safeParse(Object.fromEntries(datos));
  if (!resultado.success) return { errores: z.flattenError(resultado.error).fieldErrors };
  const libro = await guardarLibro(resultado.data);
  revalidatePath('/n1/03-next/libros');
  return { creado: libro.titulo };
}
