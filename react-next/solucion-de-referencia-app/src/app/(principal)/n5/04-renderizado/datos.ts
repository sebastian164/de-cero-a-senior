// Datos de la tienda del taller nivel-05/04. Cada función declara su propia estrategia de actualización.
import 'server-only';
import { cacheLife, cacheTag } from 'next/cache';

export const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));
export const PRODUCTOS = [
  { id: '1', nombre: 'Café de Huila', categoria: 'bebidas', precio: 32000 },
  { id: '2', nombre: 'Chocolate de mesa', categoria: 'bebidas', precio: 9000 },
  { id: '3', nombre: 'Panela', categoria: 'despensa', precio: 5000 },
] as const;

/** Regeneración por tiempo corto (listado de categoría). */
export async function listarCategoria(slug: string) {
  'use cache';
  cacheLife({ stale: 30, revalidate: 60, expire: 3600 });
  return PRODUCTOS.filter((p) => p.categoria === slug);
}
/** Estática con regeneración bajo demanda: se invalida con la etiqueta cuando cambia el precio. */
export async function obtenerFicha(id: string) {
  'use cache';
  cacheTag(`producto-${id}`);
  cacheLife('max');
  return PRODUCTOS.find((p) => p.id === id) ?? null;
}
/** Para demostrar la caché local de cada réplica: el valor se calcula una vez por instancia y etiqueta. */
export async function horaCacheada() {
  'use cache';
  cacheTag('hora');
  cacheLife('max');
  return { generadaEn: new Date().toISOString(), instancia: process.env.INSTANCIA ?? 'local' };
}
