import { computed, signal } from '@angular/core';

/** ANTES: el mismo dato (la cantidad de unidades) guardado en dos sitios que hay que sincronizar a mano. */
export class ResumenDuplicado {
  readonly lineas = signal<number[]>([]);
  readonly cantidad = signal(0);                       // copia
  agregar(n: number) { this.lineas.update((l) => [...l, n]); this.cantidad.update((c) => c + n); }
  quitarPrimera() { this.lineas.update((l) => l.slice(1)); /* se olvidó actualizar `cantidad` */ }
}

/** DESPUÉS: una sola fuente de verdad; la cantidad se deriva. */
export class ResumenUnico {
  readonly lineas = signal<number[]>([]);
  readonly cantidad = computed(() => this.lineas().reduce((s, n) => s + n, 0));
  agregar(n: number) { this.lineas.update((l) => [...l, n]); }
  quitarPrimera() { this.lineas.update((l) => l.slice(1)); }
}
