// Taller nivel-05/02, puntos 1 y 3: una decisión de actualización por consulta.
import 'server-only';
import { cacheLife, cacheTag } from 'next/cache';
import { leerDetalle, leerLista } from '@app/origen-cache';

/** LISTA: tolera segundos de antigüedad y tiene mucho tráfico, así que se invalida por tiempo (revalidate 3 s). */
export async function listaPorTiempo() {
  'use cache';
  cacheLife({ stale: 1, revalidate: 3, expire: 60 });
  return leerLista();
}
/** DETALLE: sabemos exactamente cuándo cambia (al mutar), así que se invalida bajo demanda con una etiqueta. */
export async function detallePorEtiqueta(id: string) {
  'use cache';
  cacheTag(`articulo-${id}`);
  cacheLife('max');
  return leerDetalle(id);
}
/** EXACTO: el precio al pagar no puede estar viejo; sin caché (se lee en cada petición). */
export const precioExacto = leerDetalle;
