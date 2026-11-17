import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { InjectionToken } from '@angular/core';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ClinicaApi {
  private readonly http = inject(HttpClient);
  registrar(datos: unknown) { return firstValueFrom(this.http.post<{ id: number }>('/api/usuarios', datos)); }
}

/** Confirmación al salir con cambios sin guardar; se sustituye en las pruebas. */
export const CONFIRMAR_SALIDA = new InjectionToken<(mensaje: string) => boolean>('CONFIRMAR_SALIDA', {
  providedIn: 'root', factory: () => (mensaje) => window.confirm(mensaje),
});
