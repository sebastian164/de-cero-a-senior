import { Component, input, output } from '@angular/core';

/** Genérico: no conoce ninguna funcionalidad ni contiene lógica de negocio (criterio de entrada a `compartido`). */
@Component({
  selector: 'app-boton-accion',
  template: `<button type="button" (click)="pulsado.emit()"><ng-content />{{ etiqueta() }}</button>`,
})
export class BotonAccion {
  readonly etiqueta = input.required<string>();
  readonly pulsado = output<void>();
}
