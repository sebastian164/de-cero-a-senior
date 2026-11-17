import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TareasService } from './tareas.service';

// Segunda ruta (criterio 5): el id llega como entrada gracias a withComponentInputBinding().
@Component({
  selector: 'app-detalle-tarea',
  imports: [RouterLink],
  template: `
    @if (tarea(); as t) {
      <h1>Tarea {{ t.id }}</h1>
      <p>{{ t.titulo }} — {{ t.hecha ? 'hecha' : 'pendiente' }}</p>
    } @else {
      <h1>No existe la tarea {{ id() }}</h1>
    }
    <a routerLink="..">Volver a la lista</a>
  `,
})
export class DetalleTareaComponent {
  private readonly servicio = inject(TareasService);
  readonly id = input.required({ transform: numberAttribute });
  protected readonly tarea = computed(() => this.servicio.buscar(this.id()));
}
