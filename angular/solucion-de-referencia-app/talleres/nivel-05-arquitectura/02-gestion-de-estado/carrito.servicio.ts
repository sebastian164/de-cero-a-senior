import { computed, Injectable, signal } from '@angular/core';

export interface Linea { productoId: string; precio: number; cantidad: number; }

/** Carrito como servicio con señales (peldaño 4). Se agrupan las líneas del mismo producto. */
@Injectable()
export class CarritoServicio {
  private readonly interno = signal<readonly Linea[]>([]);
  readonly lineas = this.interno.asReadonly();
  readonly total = computed(() => this.lineas().reduce((s, l) => s + l.precio * l.cantidad, 0));
  readonly cantidad = computed(() => this.lineas().reduce((s, l) => s + l.cantidad, 0));

  agregar(linea: Linea) {
    this.interno.update((ls) => ls.some((l) => l.productoId === linea.productoId)
      ? ls.map((l) => (l.productoId === linea.productoId ? { ...l, cantidad: l.cantidad + linea.cantidad } : l))
      : [...ls, linea]);
  }
  quitar(productoId: string) { this.interno.update((ls) => ls.filter((l) => l.productoId !== productoId)); }
  vaciar() { this.interno.set([]); }
}
