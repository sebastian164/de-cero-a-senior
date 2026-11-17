import { ChangeDetectionStrategy, Component } from '@angular/core';
import { GraficaPesada } from './grafica-pesada.component';
import { MapaPesado } from './mapa-pesado.component';

@Component({
  selector: 'app-sin-defer',
  imports: [GraficaPesada, MapaPesado],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h2>Sin &#64;defer</h2><app-grafica-pesada /><app-mapa-pesado />`,
})
export class SinDefer {}
