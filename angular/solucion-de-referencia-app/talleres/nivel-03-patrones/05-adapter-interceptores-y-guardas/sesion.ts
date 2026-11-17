import { HttpClient, HttpContext, HttpContextToken } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { finalize, map, Observable, shareReplay, tap } from 'rxjs';

export type Rol = 'admin' | 'cliente';

@Injectable({ providedIn: 'root' })
export class Sesion {
  private readonly datos = signal<{ token: string; roles: readonly Rol[] } | null>(null);
  readonly token = computed(() => this.datos()?.token ?? null);
  readonly activa = computed(() => this.datos() !== null);
  tieneRol(rol: Rol) { return this.datos()?.roles.includes(rol) ?? false; }
  ingresar(token: string, roles: readonly Rol[]) { this.datos.set({ token, roles }); }
  cambiarToken(token: string) { this.datos.update((d) => (d ? { ...d, token } : d)); }
  salir() { this.datos.set(null); }
}

@Injectable({ providedIn: 'root' })
export class Avisos {
  readonly ultimo = signal<string | null>(null);
  error(m: string) { this.ultimo.set(m); }
}

/** Marca la petición de renovación para que el interceptor de 401 no la intercepte a sí misma. */
export const ES_RENOVACION = new HttpContextToken(() => false);

/**
 * 3. Renovación única: mientras hay una renovación en curso, todas las peticiones que reciben 401
 *    se suscriben al MISMO observable (shareReplay). Al terminar, se olvida para la próxima vez.
 */
@Injectable({ providedIn: 'root' })
export class RenovacionDeToken {
  private readonly http = inject(HttpClient);
  private readonly sesion = inject(Sesion);
  private enCurso: Observable<string> | null = null;

  renovar(): Observable<string> {
    this.enCurso ??= this.http.post<{ token: string }>('/api/token/renovar', {}, { context: new HttpContext().set(ES_RENOVACION, true) }).pipe(
      map((r) => r.token),
      tap((token) => this.sesion.cambiarToken(token)),
      finalize(() => (this.enCurso = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.enCurso;
  }
}
