import { Component, computed, inject, signal } from '@angular/core';
import { ElementoTareaComponent } from './elemento-tarea.component';
import { Filtro, filtrarTareas } from './tareas';
import { TareasService } from './tareas.service';

// Coordina (criterio 1) y filtra (criterio 3).
@Component({
  selector: 'app-lista-tareas',
  imports: [ElementoTareaComponent],
  template: `
    <h1>Tareas</h1>
    <form (submit)="agregar($event, nueva.value); nueva.value = ''">
      <label>Nueva tarea <input #nueva name="nueva" /></label>
      <button type="submit">Agregar</button>
    </form>
    <div role="group" aria-label="Filtro">
      @for (f of filtros; track f) {
        <button type="button" [attr.aria-pressed]="filtro() === f" (click)="filtro.set(f)">{{ f }}</button>
      }
    </div>
    <p role="status">Pendientes: {{ servicio.pendientes() }}</p>
    <ul>
      @for (t of visibles(); track t.id) {
        <app-elemento-tarea [tarea]="t" (alternar)="servicio.alternar($event)" (eliminar)="servicio.eliminar($event)" />
      } @empty { <li>No hay tareas.</li> }
    </ul>
  `,
})
export class ListaTareasComponent {
  protected readonly servicio = inject(TareasService);
  protected readonly filtros: readonly Filtro[] = ['todas', 'pendientes', 'hechas'];
  protected readonly filtro = signal<Filtro>('todas');
  protected readonly visibles = computed(() => filtrarTareas(this.servicio.tareas(), this.filtro()));

  protected agregar(evento: Event, titulo: string): void {
    evento.preventDefault();
    this.servicio.agregar(titulo);
  }
}
