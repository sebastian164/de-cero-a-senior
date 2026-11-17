// Punto 6: la lógica de la lista de tareas en funciones puras, sin React.
export interface Tarea {
  readonly id: string;
  readonly titulo: string;
  readonly hecha: boolean;
}

export type Filtro = 'todas' | 'pendientes' | 'hechas';

export const FILTROS: readonly Filtro[] = ['todas', 'pendientes', 'hechas'];

export function agregar(tareas: readonly Tarea[], id: string, titulo: string): readonly Tarea[] {
  const limpio = titulo.trim();
  if (limpio.length === 0) return tareas;
  return [...tareas, { id, titulo: limpio, hecha: false }];
}

export const alternar = (tareas: readonly Tarea[], id: string): readonly Tarea[] =>
  tareas.map((t) => (t.id === id ? { ...t, hecha: !t.hecha } : t));

export const eliminar = (tareas: readonly Tarea[], id: string): readonly Tarea[] => tareas.filter((t) => t.id !== id);

export function filtrar(tareas: readonly Tarea[], filtro: Filtro): readonly Tarea[] {
  switch (filtro) {
    case 'todas':
      return tareas;
    case 'pendientes':
      return tareas.filter((t) => !t.hecha);
    case 'hechas':
      return tareas.filter((t) => t.hecha);
  }
}

export const contarPendientes = (tareas: readonly Tarea[]): number => tareas.filter((t) => !t.hecha).length;
