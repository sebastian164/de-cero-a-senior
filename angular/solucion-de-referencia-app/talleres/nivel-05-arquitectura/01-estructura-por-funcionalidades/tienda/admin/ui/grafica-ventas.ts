import { Component, computed, input } from '@angular/core';

/** Presentación pura: recibe la serie por entrada (la interfaz no importa `datos`). */
@Component({
  selector: 'app-grafica-ventas',
  template: `<svg role="img" aria-label="Ventas históricas" viewBox="0 0 600 120" width="600" height="120"><polyline fill="none" stroke="#1d3557" [attr.points]="puntos()" /></svg><p>{{ serie().length }} puntos</p>`,
})
export class GraficaVentas {
  readonly serie = input.required<readonly number[]>();
  protected readonly puntos = computed(() => {
    const max = Math.max(...this.serie());
    return this.serie().filter((_, i) => i % 10 === 0).map((v, i) => `${i},${120 - (v / max) * 110}`).join(' ');
  });
}
