import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Tarea } from './tareas';

// Presenta: solo input() y output() (criterio 1).
@Component({
  selector: 'app-elemento-tarea',
  imports: [RouterLink],
  template: `
    <li>
      <label><input type="checkbox" [checked]="tarea().hecha" (change)="alternar.emit(tarea().id)" />
        <span [class.hecha]="tarea().hecha">{{ tarea().titulo }}</span></label>
      <a [routerLink]="[tarea().id]">detalle</a>
      <button type="button" (click)="eliminar.emit(tarea().id)" [attr.aria-label]="'Eliminar ' + tarea().titulo">Eliminar</button>
    </li>
  `,
  styles: `.hecha { text-decoration: line-through; }`,
})
export class ElementoTareaComponent {
  readonly tarea = input.required<Tarea>();
  readonly alternar = output<number>();
  readonly eliminar = output<number>();
}
