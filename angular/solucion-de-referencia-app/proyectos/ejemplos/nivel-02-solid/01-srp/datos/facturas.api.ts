import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Factura } from '../dominio/factura';

export interface FacturaDto { id: string; cliente: string; fecha_emision: string; lineas: { descripcion: string; valor: number }[]; }
export const aFactura = (dto: FacturaDto): Factura => ({ id: dto.id, cliente: dto.cliente, fecha: new Date(dto.fecha_emision), lineas: dto.lineas });

// datos/facturas.api.ts — habla con el servidor y traduce
@Injectable({ providedIn: 'root' })
export class FacturasApi {
  private readonly http = inject(HttpClient);
  obtener(id: string): Observable<Factura> {
    return this.http.get<FacturaDto>(`/api/facturas/${id}`).pipe(map(aFactura));
  }
}
