// Lógica pura: no importa nada de Angular y se prueba con funciones normales (criterio 6).
export interface Tarea { readonly id: number; readonly titulo: string; readonly hecha: boolean; }
export type Filtro = 'todas' | 'pendientes' | 'hechas';

export const agregarTarea = (tareas: readonly Tarea[], titulo: string): readonly Tarea[] => {
  const limpio = titulo.trim();
  if (!limpio) return tareas;
  const id = tareas.reduce((max, t) => Math.max(max, t.id), 0) + 1;
  return [...tareas, { id, titulo: limpio, hecha: false }];
};
export const alternarTarea = (tareas: readonly Tarea[], id: number): readonly Tarea[] =>
  tareas.map((t) => (t.id === id ? { ...t, hecha: !t.hecha } : t));
export const eliminarTarea = (tareas: readonly Tarea[], id: number): readonly Tarea[] =>
  tareas.filter((t) => t.id !== id);
export const filtrarTareas = (tareas: readonly Tarea[], filtro: Filtro): readonly Tarea[] =>
  filtro === 'todas' ? tareas : tareas.filter((t) => t.hecha === (filtro === 'hechas'));
export const contarPendientes = (tareas: readonly Tarea[]): number => tareas.filter((t) => !t.hecha).length;
