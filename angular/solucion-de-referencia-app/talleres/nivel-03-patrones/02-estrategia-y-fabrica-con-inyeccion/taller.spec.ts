import { TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { provideDocumentos, VALIDADORES_DE_DOCUMENTO, ValidacionDeDocumentos } from './documentos';
import { VALIDAR_DOCUMENTO } from './documentos-funciones';
import { VALIDADOR_CHILE } from './pais-nuevo';

describe('Taller n3-02 — estrategias por país', () => {
  it('1. valida con la estrategia del país', () => {
    TestBed.configureTestingModule({ providers: [provideDocumentos({ paises: ['CO', 'AR'] })] });
    const v = TestBed.inject(ValidacionDeDocumentos);
    expect(v.validar('CO', '1020304050')).toBeNull();
    expect(v.validar('CO', '12')).toMatch(/cédula/);
    expect(v.validar('AR', '30.123.456')).toBeNull();
    expect(v.validar('PE', '1')).toBe('No sabemos validar documentos de PE');
  });

  it('2. Chile se agrega con un proveedor; el servicio no cambió', () => {
    TestBed.configureTestingModule({ providers: [provideDocumentos({ paises: ['CO'], extra: [VALIDADOR_CHILE] })] });
    const v = TestBed.inject(ValidacionDeDocumentos);
    expect(v.paises()).toEqual(['CL', 'CO']);
    expect(v.validar('CL', '12.345.678-5')).toBeNull();
    expect(v.validar('CL', '12345678-9')).toMatch(/verificador/);
    expect(readFileSync('talleres/nivel-03-patrones/02-estrategia-y-fabrica-con-inyeccion/documentos.ts', 'utf8')).not.toContain('CL');
  });

  it('3. la versión con Record da los mismos resultados', () => {
    expect(VALIDAR_DOCUMENTO.CO('1020304050')).toBeNull();
    expect(VALIDAR_DOCUMENTO.AR('1')).toMatch(/DNI/);
    expect(VALIDAR_DOCUMENTO.CL('12.345.678-5')).toBeNull();
  });

  it('5. en una prueba se sustituye una estrategia por un doble', () => {
    TestBed.configureTestingModule({ providers: [
      provideDocumentos({ paises: [] }),
      { provide: VALIDADORES_DE_DOCUMENTO, multi: true, useValue: { pais: 'CO', validar: () => 'doble dice no' } },
    ] });
    expect(TestBed.inject(ValidacionDeDocumentos).validar('CO', '1020304050')).toBe('doble dice no');
  });
});
