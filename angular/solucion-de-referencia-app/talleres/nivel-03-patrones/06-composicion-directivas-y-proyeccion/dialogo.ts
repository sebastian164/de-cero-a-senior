import { Component, input, output } from '@angular/core';
import { AutofocoDirective, ClicFueraDirective } from './directivas';

/**
 * 2. Diálogo con proyección de título, cuerpo y acciones.
 * 5. hostDirectives: el diálogo ES "clic fuera" sin que quien lo usa lo pida; la salida se expone como (cerrar).
 */
@Component({
  selector: 'app-dialogo',
  imports: [AutofocoDirective],
  hostDirectives: [{ directive: ClicFueraDirective, outputs: ['appClicFuera: fuera'] }],
  host: { role: 'dialog', '[attr.aria-labelledby]': '"titulo-" + id()', class: 'dialogo' },
  template: `
    <header [id]="'titulo-' + id()"><ng-content select="[titulo]" /></header>
    <div class="cuerpo"><ng-content select="[cuerpo]" /><ng-content /></div>
    <footer>
      <ng-content select="[acciones]" />
      <button type="button" appAutofoco (click)="cerrar.emit()">Cerrar</button>
    </footer>
  `,
  styles: `:host { display: block; border: 2px solid #1d3557; padding: 1rem; max-width: 30rem; }`,
})
export class DialogoComponent {
  readonly id = input('dialogo');
  readonly cerrar = output<void>();
}
