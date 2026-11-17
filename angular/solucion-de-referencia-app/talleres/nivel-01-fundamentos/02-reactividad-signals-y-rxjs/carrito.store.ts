import { computed, effect, Injectable, InjectionToken, inject, signal } from '@angular/core';
import { agregarLinea, cantidadDe, ivaDe, Linea, subtotalDe } from './carrito';

/** Dónde persiste: en pruebas se sustituye por un Storage en memoria. */
export const ALMACENAMIENTO = new InjectionToken<Storage>('almacenamiento', { factory: () => localStorage });
export const CLAVE_CARRITO = 'taller-n1-02-carrito';

@Injectable({ providedIn: 'root' })
export class CarritoStore {
  private readonly almacenamiento = inject(ALMACENAMIENTO);

  // 1. Estado: solo las líneas. Todo lo demás es computed.
  private readonly lineasInternas = signal<readonly Linea[]>(this.leer());
  readonly lineas = this.lineasInternas.asReadonly();
  readonly subtotal = computed(() => subtotalDe(this.lineas()));
  readonly iva = computed(() => ivaDe(this.subtotal()));
  readonly total = computed(() => this.subtotal() + this.iva());
  readonly cantidadDeItems = computed(() => cantidadDe(this.lineas()));

  constructor() {
    // 2. Un efecto SÍ corresponde: sincroniza el estado con algo externo (localStorage), no deriva estado.
    effect(() => this.almacenamiento.setItem(CLAVE_CARRITO, JSON.stringify(this.lineas())));
  }

  agregar(producto: Omit<Linea, 'cantidad'>): void { this.lineasInternas.update((l) => agregarLinea(l, producto)); }
  vaciar(): void { this.lineasInternas.set([]); }

  private leer(): readonly Linea[] {
    try { return JSON.parse(this.almacenamiento.getItem(CLAVE_CARRITO) ?? '[]'); } catch { return []; }
  }
}
