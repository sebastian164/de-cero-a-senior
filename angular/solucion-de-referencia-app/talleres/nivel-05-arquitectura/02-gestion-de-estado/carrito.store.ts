import { computed } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { Linea } from './carrito.servicio';

/** El mismo carrito con NgRx SignalStore (peldaño 5). */
export const CarritoStore = signalStore(
  withState<{ lineas: readonly Linea[] }>({ lineas: [] }),
  withComputed(({ lineas }) => ({
    total: computed(() => lineas().reduce((s, l) => s + l.precio * l.cantidad, 0)),
    cantidad: computed(() => lineas().reduce((s, l) => s + l.cantidad, 0)),
  })),
  withMethods((store) => ({
    agregar(linea: Linea) {
      patchState(store, ({ lineas }) => ({
        lineas: lineas.some((l) => l.productoId === linea.productoId)
          ? lineas.map((l) => (l.productoId === linea.productoId ? { ...l, cantidad: l.cantidad + linea.cantidad } : l))
          : [...lineas, linea],
      }));
    },
    quitar(productoId: string) { patchState(store, ({ lineas }) => ({ lineas: lineas.filter((l) => l.productoId !== productoId) })); },
    vaciar() { patchState(store, { lineas: [] }); },
  })),
);
