import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** Botón del sistema de diseño. `variante` cambia la apariencia, no el comportamiento; siempre es un `<button>` real. */
@Component({
  selector: 'ds-boton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button [type]="tipo()" [disabled]="deshabilitado()" [class]="'ds-boton ds-boton--' + variante()" (click)="pulsado.emit()"><ng-content /></button>`,
  styleUrl: './ds-boton.css',
})
export class DsBoton {
  readonly variante = input<'primario' | 'secundario' | 'peligro'>('primario');
  readonly tipo = input<'button' | 'submit'>('button');
  readonly deshabilitado = input(false);
  readonly pulsado = output<void>();
}
