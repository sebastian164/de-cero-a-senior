// Taller TS 03 — punto 1: cinco funciones que modifican sus argumentos y su versión pura.
export interface Tarea { readonly id: number; readonly titulo: string; readonly hecha: boolean; readonly etiquetas: readonly string[] }
export interface Perfil { nombre: string; direccion: { ciudad: string; calle: string } }

// --- Impuras (como se suelen encontrar) ---
export function marcarHechaImpura(tareas: Tarea[], id: number) {
  const t = tareas.find((x) => x.id === id) as { hecha: boolean } | undefined;
  if (t) t.hecha = true;
  return tareas;
}
export function ordenarImpura(tareas: Tarea[]) {
  return tareas.sort((a, b) => a.titulo.localeCompare(b.titulo));
}
export function quitarImpura(tareas: Tarea[], id: number) {
  tareas.splice(tareas.findIndex((t) => t.id === id), 1);
  return tareas;
}
export function etiquetarImpura(tarea: Tarea, etiqueta: string) {
  (tarea.etiquetas as string[]).push(etiqueta);
  return tarea;
}
export function mudarImpura(perfil: Perfil, ciudad: string) {
  perfil.direccion.ciudad = ciudad;
  return perfil;
}

// --- Puras ---
export const marcarHecha = (tareas: readonly Tarea[], id: number): Tarea[] =>
  tareas.map((t) => (t.id === id ? { ...t, hecha: true } : t));
export const ordenar = (tareas: readonly Tarea[]): Tarea[] =>
  tareas.toSorted((a, b) => a.titulo.localeCompare(b.titulo));
export const quitar = (tareas: readonly Tarea[], id: number): Tarea[] => tareas.filter((t) => t.id !== id);
export const etiquetar = (tarea: Tarea, etiqueta: string): Tarea => ({ ...tarea, etiquetas: [...tarea.etiquetas, etiqueta] });
export const mudar = (perfil: Perfil, ciudad: string): Perfil => ({ ...perfil, direccion: { ...perfil.direccion, ciudad } });

// --- Punto 2: la copia con ... es superficial ---
export const copiaSuperficial = (perfil: Perfil): Perfil => ({ ...perfil });
export const copiaDosNiveles = (perfil: Perfil): Perfil => ({ ...perfil, direccion: { ...perfil.direccion } });
