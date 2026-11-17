import { ChangeDetectionStrategy, Component } from '@angular/core';
import { GraficaPesada } from './grafica-pesada.component';
import { MapaPesado } from './mapa-pesado.component';

// Punto 5: dos componentes pesados diferidos.
@Component({
  selector: 'app-con-defer',
  imports: [GraficaPesada, MapaPesado],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Con &#64;defer</h2>
    @defer (on viewport) { <app-grafica-pesada /> } @placeholder { <p>Gráfica pendiente</p> }
    <button type="button" #ver>Mostrar mapa</button>
    @defer (on interaction(ver)) { <app-mapa-pesado /> } @placeholder { <p>Mapa pendiente</p> }
  `,
})
export class ConDefer {}
