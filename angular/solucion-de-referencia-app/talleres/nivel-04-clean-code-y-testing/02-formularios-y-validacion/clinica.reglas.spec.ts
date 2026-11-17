import { cantidadContactosValida, clavesIguales, documentoValido, edadEn, esMenorDeEdad, fechaNacimientoValida } from './clinica.reglas';

const hoy = new Date(2026, 10, 15); // 15 de noviembre de 2026

describe('n4-02 reglas puras (sin TestBed)', () => {
  it.each([
    ['cedula', '123456', true], ['cedula', '12345', false], ['cedula', '12345678901', false], ['cedula', '12345a', false],
    ['pasaporte', 'AB1234', true], ['pasaporte', 'ab1234', false], ['pasaporte', 'AB12', false], ['pasaporte', 'ABCDEFGHIJ', false],
  ] as const)('documento %s %s → %s', (tipo, valor, ok) => expect(documentoValido(tipo, valor) === undefined).toBe(ok));

  it('el mensaje del documento depende del tipo', () => {
    expect(documentoValido('cedula', 'x')?.message).toBe('Entre 6 y 10 dígitos');
    expect(documentoValido('pasaporte', 'x')?.message).toBe('Entre 6 y 9 letras o números');
  });

  it('la edad se cumple el día del cumpleaños, no antes', () => {
    expect(edadEn('2008-11-15', hoy)).toBe(18);
    expect(edadEn('2008-11-16', hoy)).toBe(17);
    expect(esMenorDeEdad('2008-11-15', hoy)).toBe(false);
    expect(esMenorDeEdad('2008-11-16', hoy)).toBe(true);
    expect(esMenorDeEdad('', hoy)).toBe(false);
  });

  it('fecha de nacimiento', () => {
    expect(fechaNacimientoValida('', hoy)?.message).toBe('Escribe tu fecha de nacimiento');
    expect(fechaNacimientoValida('2027-01-01', hoy)?.message).toBe('La fecha no puede ser futura');
    expect(fechaNacimientoValida('1990-05-05', hoy)).toBeUndefined();
  });

  it('claves y contactos', () => {
    expect(clavesIguales('a', 'a')).toBeUndefined();
    expect(clavesIguales('a', 'b')?.kind).toBe('clavesDistintas');
    expect(cantidadContactosValida(0)?.message).toBe('Agrega al menos un contacto');
    expect(cantidadContactosValida(1)).toBeUndefined();
    expect(cantidadContactosValida(3)).toBeUndefined();
    expect(cantidadContactosValida(4)?.message).toBe('Máximo tres contactos');
  });
});
