// Nota de versión de nivel-05/02: en Next.js 16 revalidateTag exige el perfil como segundo argumento.
// La forma de una sola llamada con un argumento, que enseñaban las versiones anteriores, YA NO COMPILA (TS2554).
'use server';
import { revalidateTag } from 'next/cache';

export async function invalidar(etiqueta: string) {
  revalidateTag(etiqueta);
}
