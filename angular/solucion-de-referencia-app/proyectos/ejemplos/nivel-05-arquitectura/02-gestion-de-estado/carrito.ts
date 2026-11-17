import { computed, Injectable, signal } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';

export interface Linea { productoId: string; precio: number; cantidad: number; }

// Peldaño 4: servicio con señales (README, copiado)
@Injectable({ providedIn: 'root' })
export class Carrito {
  private readonly lineasInternas = signal<readonly Linea[]>([]);

  readonly lineas = this.lineasInternas.asReadonly();            // nadie de fuera puede escribir
  readonly total = computed(() => this.lineas().reduce((suma, l) => suma + l.precio * l.cantidad, 0));
  readonly cantidad = computed(() => this.lineas().reduce((suma, l) => suma + l.cantidad, 0));

  agregar(linea: Linea): void {
    this.lineasInternas.update((lineas) => [...lineas, linea]);
  }

  quitar(productoId: string): void {
    this.lineasInternas.update((lineas) => lineas.filter((l) => l.productoId !== productoId));
  }
}

// Peldaño 5: NgRx SignalStore (README, copiado)
export const CarritoStore = signalStore(
  { providedIn: 'root' },
  withState<{ lineas: readonly Linea[] }>({ lineas: [] }),
  withComputed(({ lineas }) => ({
    total: computed(() => lineas().reduce((suma, l) => suma + l.precio * l.cantidad, 0)),
    cantidad: computed(() => lineas().reduce((suma, l) => suma + l.cantidad, 0)),
  })),
  withMethods((store) => ({
    agregar(linea: Linea): void {
      patchState(store, (estado) => ({ lineas: [...estado.lineas, linea] }));
    },
    quitar(productoId: string): void {
      patchState(store, (estado) => ({ lineas: estado.lineas.filter((l) => l.productoId !== productoId) }));
    },
  })),
);
