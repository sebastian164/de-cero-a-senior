import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Tarjeta con título (un encabezado real, para la jerarquía de la página) y contenido proyectado. */
@Component({
  selector: 'ds-tarjeta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<article class="ds-tarjeta"><h3>{{ titulo() }}</h3><ng-content /></article>`,
  styleUrl: './ds-tarjeta.css',
})
export class DsTarjeta { readonly titulo = input.required<string>(); }
