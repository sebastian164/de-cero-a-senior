import { NgTemplateOutlet } from '@angular/common';
import { Component, contentChild, input, TemplateRef } from '@angular/core';

/** 3. La lista con estados del ejemplo, sin cambios. */
@Component({
  selector: 'app-lista-con-estados',
  imports: [NgTemplateOutlet],
  template: `
    @if (cargando()) {
      <ng-container [ngTemplateOutlet]="plantillaCargando() ?? porDefectoCargando" />
    } @else if (elementos().length === 0) {
      <ng-content select="[vacio]" />
    } @else {
      <ul>
        @for (elemento of elementos(); track identificar()(elemento)) {
          <li><ng-container [ngTemplateOutlet]="plantillaElemento()" [ngTemplateOutletContext]="{ $implicit: elemento }" /></li>
        }
      </ul>
    }
    <ng-template #porDefectoCargando><p>Cargando…</p></ng-template>
  `,
})
export class ListaConEstadosComponent<T> {
  readonly elementos = input.required<readonly T[]>();
  readonly cargando = input(false);
  readonly identificar = input.required<(elemento: T) => unknown>();
  readonly plantillaElemento = contentChild.required<TemplateRef<{ $implicit: T }>>('elemento');
  readonly plantillaCargando = contentChild<TemplateRef<unknown>>('cargando');
}
