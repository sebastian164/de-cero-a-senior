import { Injectable, InjectionToken, inject } from '@angular/core';

// README: "Bien: tras una abstracción inyectable (DIP); en el servidor, una implementación en memoria".
export interface AlmacenLocal { leer(clave: string): string | null; escribir(clave: string, valor: string): void; }
@Injectable({ providedIn: 'root' })
export class AlmacenEnMemoria implements AlmacenLocal {
  private readonly datos = new Map<string, string>();
  leer(clave: string) { return this.datos.get(clave) ?? null; }
  escribir(clave: string, valor: string) { this.datos.set(clave, valor); }
}
export class AlmacenDelNavegador implements AlmacenLocal {
  leer(clave: string) { return localStorage.getItem(clave); }
  escribir(clave: string, valor: string) { localStorage.setItem(clave, valor); }
}
export const ALMACEN_LOCAL = new InjectionToken<AlmacenLocal>('almacen', { factory: () => inject(AlmacenEnMemoria) });
