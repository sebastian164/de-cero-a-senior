import { HttpClient } from '@angular/common/http';
import { Component, inject, Injectable, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, Observable } from 'rxjs';

@Component({
  selector: 'app-avatar',
  template: `<img [src]="fotoUrl()" [alt]="nombre()" />`,
})
export class AvatarComponent {
  readonly fotoUrl = input.required<string>();
  readonly nombre = input.required<string>();
}

// Contrato estrecho, definido por quien lo consume
export abstract class ConteoDePedidos {
  abstract delUsuarioActual(): Observable<number>;
}

@Component({ selector: 'app-contador-pedidos', template: `<p>Tienes {{ total() }} pedidos</p>` })
export class ContadorPedidosComponent {
  private readonly conteo = inject(ConteoDePedidos);
  protected readonly total = toSignal(this.conteo.delUsuarioActual(), { initialValue: 0 });
}

// Los otros dos contratos que el README nombra sin declarar.
export abstract class ConsultaDePedidos { abstract listar(): Observable<{ id: string; cliente: string }[]>; }
export abstract class GestionDePedidos { abstract cancelar(id: string): Observable<void>; }

// Una sola clase puede cumplir varios contratos estrechos
@Injectable({ providedIn: 'root' })
export class PedidosApi implements ConteoDePedidos, ConsultaDePedidos, GestionDePedidos {
  private readonly http = inject(HttpClient);
  delUsuarioActual() { return this.http.get<{ total: number }>('/api/pedidos/conteo').pipe(map((r) => r.total)); }
  listar() { return this.http.get<{ id: string; cliente: string }[]>('/api/pedidos'); }
  cancelar(id: string) { return this.http.post<void>(`/api/pedidos/${id}/cancelacion`, {}); }
}

// Registro
export const proveedoresIsp = [{ provide: ConteoDePedidos, useExisting: PedidosApi }];
