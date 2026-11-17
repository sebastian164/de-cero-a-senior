import { Injectable, Provider } from '@angular/core';
import { VALIDADORES_DE_DOCUMENTO, ValidadorDeDocumento } from './documentos';

// 2. Un país nuevo: una clase y un proveedor. ValidacionDeDocumentos no se toca.
@Injectable() export class RutChile implements ValidadorDeDocumento {
  readonly pais = 'CL';
  validar(rut: string) {
    const m = rut.replace(/\./g, '').toUpperCase().match(/^(\d{7,8})-([\dK])$/);
    if (!m) return 'El RUT tiene la forma 12345678-9';
    let suma = 0, factor = 2;
    for (const d of m[1].split('').reverse()) { suma += Number(d) * factor; factor = factor === 7 ? 2 : factor + 1; }
    const dv = 11 - (suma % 11);
    const esperado = dv === 11 ? '0' : dv === 10 ? 'K' : String(dv);
    return esperado === m[2] ? null : 'El dígito verificador del RUT no coincide';
  }
}
export const VALIDADOR_CHILE: Provider = { provide: VALIDADORES_DE_DOCUMENTO, useClass: RutChile, multi: true };
