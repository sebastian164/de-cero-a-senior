import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

export interface Linea { readonly productoId: string; readonly nombre: string; readonly precio: number; readonly cantidad: number; }
export const IVA = 0.19;

/** La API del carrito: guarda el carrito completo en el servidor. */
@Injectable({ providedIn: 'root' })
export class CarritoApi {
  private readonly http = inject(HttpClient);
  fallar = false;   // interruptor de la pantalla de demostración para provocar el fallo
  guardar(lineas: readonly Linea[]): Observable<unknown> {
    return this.http.put('/api/carrito', lineas, { params: this.fallar ? { fallar: '1' } : {} });
  }
}

/** 1. Fachada de estado: estado privado, lectura con computed, escritura por intenciones. */
@Injectable({ providedIn: 'root' })
export class AlmacenCarrito {
  private readonly api = inject(CarritoApi);
  private readonly estado = signal<{ lineas: readonly Linea[]; error: string | null; guardando: boolean }>({ lineas: [], error: null, guardando: false });

  readonly lineas = computed(() => this.estado().lineas);
  readonly error = computed(() => this.estado().error);
  readonly guardando = computed(() => this.estado().guardando);
  readonly cantidad = computed(() => this.lineas().reduce((s, l) => s + l.cantidad, 0));
  readonly subtotal = computed(() => this.lineas().reduce((s, l) => s + l.precio * l.cantidad, 0));
  readonly total = computed(() => Math.round(this.subtotal() * (1 + IVA)));

  agregar(p: Omit<Linea, 'cantidad'>): void {
    this.cambiar((ls) => (ls.some((l) => l.productoId === p.productoId)
      ? ls.map((l) => (l.productoId === p.productoId ? { ...l, cantidad: l.cantidad + 1 } : l))
      : [...ls, { ...p, cantidad: 1 }]));
  }
  quitar(productoId: string): void { this.cambiar((ls) => ls.filter((l) => l.productoId !== productoId)); }
  cambiarCantidad(productoId: string, cantidad: number): void {
    if (cantidad <= 0) return this.quitar(productoId);
    this.cambiar((ls) => ls.map((l) => (l.productoId === productoId ? { ...l, cantidad } : l)));
  }
  vaciar(): void { this.cambiar(() => []); }

  /** 3. Actualización optimista con reversa: se aplica ya, se guarda, y si falla vuelve al estado anterior. */
  private cambiar(fn: (ls: readonly Linea[]) => readonly Linea[]): void {
    const anterior = this.estado().lineas;
    const nuevas = fn(anterior);
    this.estado.update((e) => ({ ...e, lineas: nuevas, error: null, guardando: true }));
    this.api.guardar(nuevas).subscribe({
      next: () => this.estado.update((e) => ({ ...e, guardando: false })),
      error: () => this.estado.update((e) => ({ ...e, lineas: anterior, guardando: false, error: 'No se pudo guardar el carrito; se deshizo el cambio' })),
    });
  }
}
