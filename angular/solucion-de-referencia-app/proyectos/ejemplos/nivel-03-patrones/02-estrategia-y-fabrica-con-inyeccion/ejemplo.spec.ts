import { TestBed } from '@angular/core/testing';
import { CALCULOS_ENVIO, CotizadorEnvio, EnvioEstandar, EnvioExpress, provideEnvios } from './envios';
import { CALCULOS, CONFIGURACION, PASARELA } from './pasarela';

describe('n3-02 Strategy y Factory (ejemplo)', () => {
  it('cotiza por clave y rechaza una modalidad desconocida', () => {
    TestBed.configureTestingModule({ providers: [provideEnvios()] });
    const c = TestBed.inject(CotizadorEnvio);
    expect(c.cotizar('estandar', 2)).toBe(9000);
    expect(c.cotizar('express', 2)).toBe(16800);
    expect(() => c.cotizar('dron', 1)).toThrow('Modalidad no soportada: dron');
  });
  it('olvidar multi: true sobrescribe la anterior (error común del README)', () => {
    TestBed.configureTestingModule({ providers: [
      { provide: CALCULOS_ENVIO, useClass: EnvioExpress },
      { provide: CALCULOS_ENVIO, useClass: EnvioEstandar },
    ] });
    expect(TestBed.inject(CALCULOS_ENVIO)).toBeInstanceOf(EnvioEstandar);   // queda la última y ya no es un arreglo
  });
  it('mezclar uno sin multi con otros multi en el mismo inyector es un error de Angular', () => {
    TestBed.configureTestingModule({ providers: [provideEnvios(), { provide: CALCULOS_ENVIO, useClass: EnvioEstandar }] });
    expect(() => TestBed.inject(CALCULOS_ENVIO)).toThrow('Cannot mix multi providers and regular providers');
  });
  it('la fábrica elige la pasarela según el país', () => {
    expect(TestBed.inject(PASARELA).nombre).toBe('PSE');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: CONFIGURACION, useValue: { pais: 'MX' } }] });
    expect(TestBed.inject(PASARELA).nombre).toBe('Internacional');
  });
  it('el Record de funciones', () => expect(CALCULOS.recogida(10)).toBe(0));
});
