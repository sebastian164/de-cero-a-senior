import { NgTemplateOutlet } from '@angular/common';
import { Component, contentChildren, Directive, inject, input, TemplateRef } from '@angular/core';

/** Una columna: su encabezado y la plantilla de la celda. La plantilla recibe la fila como $implicit. */
@Directive({ selector: 'ng-template[appColumna]' })
export class ColumnaDirective<T> {
  readonly encabezado = input.required<string>({ alias: 'appColumna' });
  readonly plantilla = inject<TemplateRef<{ $implicit: T }>>(TemplateRef);
}

/** 1. Tabla genérica: no conoce los datos; solo recorre filas y columnas. */
@Component({
  selector: 'app-tabla',
  imports: [NgTemplateOutlet],
  template: `
    <table>
      <thead><tr>@for (c of columnas(); track $index) { <th scope="col">{{ c.encabezado() }}</th> }</tr></thead>
      <tbody>
        @for (fila of filas(); track $index) {
          <tr>@for (c of columnas(); track $index) {
            <td><ng-container [ngTemplateOutlet]="c.plantilla" [ngTemplateOutletContext]="{ $implicit: fila }" /></td>
          }</tr>
        } @empty { <tr><td [attr.colspan]="columnas().length">Sin filas</td></tr> }
      </tbody>
    </table>
  `,
})
export class TablaComponent<T> {
  readonly filas = input.required<readonly T[]>();
  protected readonly columnas = contentChildren(ColumnaDirective);
}
