import { inject, Injectable, signal } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class Sesion {
  readonly roles = signal<readonly string[]>([]);
  tieneRol(rol: string) { return this.roles().includes(rol); }
  entrarComo(rol: string) { this.roles.set([rol]); }
}

/** Guarda de carga: el código de `admin` ni se descarga sin el rol. */
export const requiereRol = (rol: string, destino: string): CanMatchFn => () => inject(Sesion).tieneRol(rol) || inject(Router).parseUrl(destino);
