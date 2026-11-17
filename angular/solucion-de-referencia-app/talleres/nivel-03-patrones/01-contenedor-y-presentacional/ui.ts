import { CurrencyPipe } from '@angular/common';
import { Component, input, model, output } from '@angular/core';

export interface Curso { readonly id: string; readonly nombre: string; readonly categoria: string; readonly precio: number; }

/** Presentación 1: filtro por categoría. */
@Component({
  selector: 'app-filtro-categorias',
  template: `
    <fieldset><legend>Categoría</legend>
      @for (c of categorias(); track c) {
        <label><input type="radio" name="cat" [checked]="elegida() === c" (change)="elegida.set(c)" /> {{ c }}</label>
      }
    </fieldset>
  `,
})
export class FiltroCategorias {
  readonly categorias = input.required<readonly string[]>();
  readonly elegida = model.required<string>();
}

/** Presentación 2: tarjeta de curso. Las acciones se proyectan: la tarjeta no sabe quién puede inscribirse. */
@Component({
  selector: 'app-tarjeta-curso',
  imports: [CurrencyPipe],
  template: `
    <article>
      <h3>{{ curso().nombre }}</h3>
      <p>{{ curso().categoria }} · {{ curso().precio | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
      <ng-content select="[acciones]" />
    </article>
  `,
})
export class TarjetaCurso { readonly curso = input.required<Curso>(); }

/** Presentación 3: resumen de lo elegido. */
@Component({
  selector: 'app-resumen-seleccion',
  imports: [CurrencyPipe],
  template: `
    <aside aria-label="Selección">
      <p>{{ cantidad() }} cursos · {{ total() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
      <button type="button" [disabled]="cantidad() === 0" (click)="confirmar.emit()">Confirmar inscripción</button>
    </aside>
  `,
})
export class ResumenSeleccion {
  readonly cantidad = input.required<number>();
  readonly total = input.required<number>();
  readonly confirmar = output<void>();
}
