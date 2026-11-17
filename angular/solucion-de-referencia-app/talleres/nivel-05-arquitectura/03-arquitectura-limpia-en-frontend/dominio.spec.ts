// Pruebas del dominio: sin Angular, sin TestBed, sin HTTP.
import { cuotaMensual, erroresDelPaso, pasosAplicables, puedeEnviar, requiereCodeudor, Solicitud, SOLICITUD_VACIA } from './dominio/solicitud';

const s = (extra: Partial<Solicitud> = {}): Solicitud => ({ ...SOLICITUD_VACIA, nombre: 'Ana Gómez', ingresoMensual: 5_000_000, monto: 10_000_000, plazoMeses: 24, ...extra });

describe('n5-03 dominio: pasos condicionales', () => {
  it('sin codeudor hay tres pasos', () => expect(pasosAplicables(s())).toEqual(['personales', 'ingresos', 'revision']));
  it('con codeudor voluntario, cuatro', () => expect(pasosAplicables(s({ conCodeudor: true }))).toEqual(['personales', 'ingresos', 'codeudor', 'revision']));
  it('un monto mayor a 20 millones lo exige', () => { expect(requiereCodeudor(s({ monto: 20_000_000 }))).toBe(false); expect(requiereCodeudor(s({ monto: 20_000_001 }))).toBe(true); });
});

describe('n5-03 dominio: cuota', () => {
  it('con tasa 0 es monto / plazo', () => expect(cuotaMensual(1200, 12, 0)).toBe(100));
  it('sistema francés al 1,5 % mensual (valor de referencia calculado aparte con Python)', () => expect(cuotaMensual(10_000_000, 24)).toBeCloseTo(499_241.02, 0));
});

describe('n5-03 dominio: errores por paso', () => {
  it('personales', () => { expect(erroresDelPaso('personales', s({ nombre: 'A' }))).toHaveProperty('nombre'); expect(erroresDelPaso('personales', s())).toEqual({}); });
  it('la cuota no puede superar el 40 % del ingreso', () => {
    expect(erroresDelPaso('ingresos', s({ ingresoMensual: 1_000_000 }))).toHaveProperty('monto');
    expect(erroresDelPaso('ingresos', s({ ingresoMensual: 1_300_000 }))).toEqual({});        // 499.241 / 1.300.000 = 38 %
  });
  it('los independientes, hasta 36 meses', () => expect(erroresDelPaso('ingresos', s({ tipoEmpleo: 'independiente', plazoMeses: 48, ingresoMensual: 50_000_000 }))).toHaveProperty('plazoMeses'));
  it('codeudor: solo se valida cuando hace falta', () => {
    expect(erroresDelPaso('codeudor', s())).toEqual({});
    const con = s({ conCodeudor: true });
    expect(Object.keys(erroresDelPaso('codeudor', con))).toEqual(['codeudorNombre', 'codeudorDocumento']);
    expect(erroresDelPaso('codeudor', { ...con, codeudor: { nombre: 'Luis Pérez', documento: '1234567' } })).toEqual({});
  });
  it('puedeEnviar exige todos los pasos aplicables', () => {
    expect(puedeEnviar(s())).toBe(true);
    expect(puedeEnviar(s({ conCodeudor: true }))).toBe(false);
    expect(puedeEnviar(s({ nombre: '' }))).toBe(false);
  });
});
