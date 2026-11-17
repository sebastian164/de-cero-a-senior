import { Injectable } from '@angular/core';
import { Factura } from '../dominio/factura';

// exportacion/exportador-csv.ts — una sola tarea
@Injectable({ providedIn: 'root' })
export class ExportadorCsv {
  exportar(factura: Factura): Blob {
    const filas = [['descripcion', 'valor'], ...factura.lineas.map((l) => [l.descripcion, String(l.valor)])];
    return new Blob([filas.map((f) => f.join(';')).join('\n')], { type: 'text/csv' });
  }
}
