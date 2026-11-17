import { Component } from '@angular/core';
import { GraficaVentasConectada } from './grafica-ventas-conectada';

@Component({
  imports: [GraficaVentasConectada],
  template: `
    <h2>Administración</h2>
    <p>La gráfica se descarga cuando llega a la vista o cuando se pide.</p>
    @defer (on viewport; on interaction(cargar)) {
      <app-grafica-ventas-conectada />
    } @placeholder { <button #cargar type="button">Cargar gráfica</button> }
      @loading { <p>Cargando gráfica…</p> }
  `,
})
export class AdminPagina {}
