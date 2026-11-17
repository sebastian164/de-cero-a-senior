import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Factura } from '../dominio/factura';

// Cambia cuando cambia el contrato del backend.
export interface FacturaDto {
  id: string; fecha_emision: string;
  cliente: { nombre_completo: string; tipo: 'minorista' | 'mayorista' };
  lineas: { descripcion: string; cantidad: number; valor_unitario: number }[];
}
export const aFactura = (dto: FacturaDto): Factura => ({
  id: dto.id, cliente: dto.cliente.nombre_completo, tipoCliente: dto.cliente.tipo, emitidaEn: new Date(dto.fecha_emision),
  lineas: dto.lineas.map((l) => ({ descripcion: l.descripcion, cantidad: l.cantidad, valorUnitario: l.valor_unitario })),
});

@Injectable({ providedIn: 'root' })
export class FacturasApi {
  private readonly http = inject(HttpClient);
  obtener(id: string): Observable<Factura> { return this.http.get<FacturaDto>(`/api/facturas/${id}`).pipe(map(aFactura)); }
  enviarPorCorreo(para: string, asunto: string, cuerpo: string): Observable<unknown> {
    return this.http.post('/api/correos', { para, asunto, cuerpo });
  }
}
