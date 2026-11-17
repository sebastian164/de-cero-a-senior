import { computed, Injectable, signal } from '@angular/core';

// Piezas que el README usa sin declarar: Sesion, Rol y Avisos.
export type Rol = 'admin' | 'cliente';

@Injectable({ providedIn: 'root' })
export class Sesion {
  private readonly usuario = signal<{ token: string; roles: readonly Rol[] } | null>(null);
  readonly token = computed(() => this.usuario()?.token ?? null);
  readonly activa = computed(() => this.usuario() !== null);
  tieneRol(rol: Rol): boolean { return this.usuario()?.roles.includes(rol) ?? false; }
  ingresar(token: string, roles: readonly Rol[]): void { this.usuario.set({ token, roles }); }
  salir(): void { this.usuario.set(null); }
}

@Injectable({ providedIn: 'root' })
export class Avisos {
  readonly ultimo = signal<string | null>(null);
  error(mensaje: string): void { this.ultimo.set(mensaje); }
}
