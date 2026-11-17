'use server';
import { updateTag } from 'next/cache';

/** Acción de servidor: quien muta sí espera ver su propio cambio, así que usa updateTag (expira la etiqueta ya, sin dato viejo). */
export async function invalidarConUpdateTag(datos: FormData) {
  updateTag(`articulo-${String(datos.get('id'))}`);
}
