import { computed, Injectable, signal } from '@angular/core';
import { agregarTarea, alternarTarea, contarPendientes, eliminarTarea, Tarea } from './tareas';

// Criterio 2: servicio con las tareas en memoria. Estado privado, lectura pública.
@Injectable({ providedIn: 'root' })
export class TareasService {
  private readonly lista = signal<readonly Tarea[]>([
    { id: 1, titulo: 'Leer la sesión 01', hecha: true },
    { id: 2, titulo: 'Hacer el taller', hecha: false },
  ]);
  readonly tareas = this.lista.asReadonly();
  readonly pendientes = computed(() => contarPendientes(this.lista()));   // criterio 4: derivado

  agregar(titulo: string): void { this.lista.update((t) => agregarTarea(t, titulo)); }
  alternar(id: number): void { this.lista.update((t) => alternarTarea(t, id)); }
  eliminar(id: number): void { this.lista.update((t) => eliminarTarea(t, id)); }
  buscar(id: number): Tarea | undefined { return this.lista().find((t) => t.id === id); }
}
