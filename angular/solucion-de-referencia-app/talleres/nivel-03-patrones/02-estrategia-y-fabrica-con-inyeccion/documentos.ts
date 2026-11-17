import { inject, Injectable, InjectionToken, makeEnvironmentProviders, Provider } from '@angular/core';

// 1. Validadores de documento por país como estrategias registradas con multi: true.
export interface ValidadorDeDocumento {
  readonly pais: string;
  validar(documento: string): string | null;   // null si es válido; si no, el motivo
}
export const VALIDADORES_DE_DOCUMENTO = new InjectionToken<ValidadorDeDocumento[]>('validadores de documento');

@Injectable() export class CedulaColombia implements ValidadorDeDocumento {
  readonly pais = 'CO';
  validar(d: string) { return /^\d{6,10}$/.test(d) ? null : 'La cédula tiene entre 6 y 10 dígitos'; }
}
@Injectable() export class DniArgentina implements ValidadorDeDocumento {
  readonly pais = 'AR';
  validar(d: string) { return /^\d{7,8}$/.test(d.replace(/\./g, '')) ? null : 'El DNI tiene 7 u 8 dígitos'; }
}

/** El contexto: no conoce ningún país. */
@Injectable({ providedIn: 'root' })
export class ValidacionDeDocumentos {
  private readonly porPais = new Map(inject(VALIDADORES_DE_DOCUMENTO).map((v) => [v.pais, v]));
  paises(): string[] { return [...this.porPais.keys()].sort(); }
  validar(pais: string, documento: string): string | null {
    const v = this.porPais.get(pais);
    if (!v) return `No sabemos validar documentos de ${pais}`;
    return v.validar(documento.trim());
  }
}

// 4. Función de configuración con opciones.
export interface OpcionesDocumentos { paises: readonly ('CO' | 'AR')[]; extra?: readonly Provider[]; }
const CATALOGO = { CO: CedulaColombia, AR: DniArgentina } as const;
export function provideDocumentos(opciones: OpcionesDocumentos) {
  return makeEnvironmentProviders([
    ...opciones.paises.map((p) => ({ provide: VALIDADORES_DE_DOCUMENTO, useClass: CATALOGO[p], multi: true })),
    ...(opciones.extra ?? []),
    ValidacionDeDocumentos,   // se crea en el mismo inyector que las estrategias
  ]);
}
