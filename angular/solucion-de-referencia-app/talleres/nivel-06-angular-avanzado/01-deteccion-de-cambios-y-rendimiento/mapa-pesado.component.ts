import { ChangeDetectionStrategy, Component } from '@angular/core';
import { REGIONES_MAPA } from './peso-mapa';

@Component({
  selector: 'app-mapa-pesado',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section aria-label="Mapa"><h2>Mapa</h2><p>{{ regiones }} regiones, suma {{ suma }}</p></section>`,
})
export class MapaPesado {
  protected readonly regiones = REGIONES_MAPA.length;
  protected readonly suma = Math.round(REGIONES_MAPA.reduce((s, p) => s + p.valor, 0));
}
