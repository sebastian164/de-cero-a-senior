import { Injectable } from '@angular/core';
import { Factura, total, valorLinea } from '../dominio/factura';

// El formato del archivo (lo pide Contabilidad) es una función pura; la descarga es un detalle del navegador.
export const facturaACsv = (f: Factura): string =>
  ['descripcion;cantidad;valor', ...f.lineas.map((l) => `${l.descripcion.replace(/;/g, ',')};${l.cantidad};${valorLinea(l)}`), `total;;${total(f)}`]
    .join('\n') + '\n';

@Injectable({ providedIn: 'root' })
export class Descargador {
  descargar(nombre: string, contenido: string, tipo: string): void {
    const url = URL.createObjectURL(new Blob([contenido], { type: tipo }));
    const a = document.createElement('a');
    a.href = url; a.download = nombre; a.click();
    URL.revokeObjectURL(url);
  }
}
