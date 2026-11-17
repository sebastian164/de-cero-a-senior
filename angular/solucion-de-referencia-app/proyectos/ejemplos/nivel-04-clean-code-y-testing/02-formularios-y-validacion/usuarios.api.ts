import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom, map, Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UsuariosApi {
  private readonly http = inject(HttpClient);
  existeCorreo(correo: string): Observable<boolean> {
    return this.http.get<{ existe: boolean }>('/api/usuarios/existe', { params: { correo } }).pipe(map((r) => r.existe));
  }
  registrar(datos: unknown): Promise<unknown> { return firstValueFrom(this.http.post('/api/usuarios', datos)); }
}
