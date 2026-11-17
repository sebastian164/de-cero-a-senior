import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PUNTOS_GRAFICA } from './peso-grafica';

@Component({
  selector: 'app-grafica-pesada',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section aria-label="Gráfica"><h2>Gráfica</h2><p>{{ puntos }} puntos, suma {{ suma }}</p></section>`,
})
export class GraficaPesada {
  protected readonly puntos = PUNTOS_GRAFICA.length;
  protected readonly suma = Math.round(PUNTOS_GRAFICA.reduce((s, p) => s + p.valor, 0));
}
