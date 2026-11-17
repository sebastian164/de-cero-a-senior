import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Etiqueta de estado: el significado va en el texto, no solo en el color. */
@Component({
  selector: 'ds-etiqueta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="ds-etiqueta" [attr.data-tono]="tono()"><ng-content /></span>`,
  styleUrl: './ds-etiqueta.css',
})
export class DsEtiqueta { readonly tono = input<'neutro' | 'exito' | 'error'>('neutro'); }
