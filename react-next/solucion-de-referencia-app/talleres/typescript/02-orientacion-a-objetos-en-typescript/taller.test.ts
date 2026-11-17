import { describe, expect, it } from 'vitest';
import { Curso, crearCurso, inscribir, retirar } from './curso';
import { CuentaConNumeral, CuentaConPrivate, escribirPorNombre, leerPorNombre } from './privados';
import { NotificadorCorreo, NotificadorSms, avisarATodos, notificadorConsola } from './notificador';
import { EmpleadoAsalariado, EmpleadoPorHoras, cambiarEsquema, nomina, type Empleado } from './empleados';
import { obtenerComoClase, obtenerYConstruir } from './respuesta-http';

describe('TS 02 · Curso', () => {
  it('versión clase: respeta el cupo y no expone el arreglo interno', () => {
    const curso = new Curso('TypeScript', 2);
    curso.inscribir('Ana');
    curso.inscribir('Luis');
    expect(() => curso.inscribir('Eva')).toThrow('El curso está lleno');
    expect(() => curso.inscribir('Ana')).toThrow('Ana ya está inscrito');
    (curso.inscritos as string[]).push('Intruso');
    expect(curso.inscritos).toEqual(['Ana', 'Luis']);
    curso.retirar('Ana');
    expect(curso.lleno).toBe(false);
  });
  it('versión inmutable: devuelve resultados y no modifica el original', () => {
    const vacio = crearCurso('TypeScript', 1);
    const r1 = inscribir(vacio, 'Ana');
    expect(vacio.inscritos).toEqual([]);
    if (!r1.ok) throw new Error('debía inscribir');
    expect(inscribir(r1.curso, 'Luis')).toEqual({ ok: false, error: 'El curso está lleno' });
    expect(retirar(r1.curso, 'Eva')).toEqual({ ok: false, error: 'Eva no está inscrito' });
  });
});

describe('TS 02 · private frente a #privado', () => {
  it('private se lee y se modifica en ejecución', () => {
    const cuenta = new CuentaConPrivate();
    expect(leerPorNombre(cuenta, 'saldo')).toBe(100);
    escribirPorNombre(cuenta, 'saldo', 1_000_000);
    expect(cuenta.consultar()).toBe(1_000_000);
    expect(Object.keys(cuenta)).toContain('saldo');
  });
  it('#privado no es accesible por nombre ni aparece en las claves', () => {
    const cuenta = new CuentaConNumeral();
    expect(leerPorNombre(cuenta, '#saldo')).toBeUndefined();
    escribirPorNombre(cuenta, '#saldo', 1_000_000);
    expect(cuenta.consultar()).toBe(100);
    expect(Object.keys(cuenta)).toEqual(['#saldo']); // solo la propiedad pública que acabamos de crear, distinta del campo privado
  });
});

describe('TS 02 · Notificador', () => {
  it('la función usa las tres implementaciones sin conocerlas', async () => {
    const correo = new NotificadorCorreo();
    const sms = new NotificadorSms();
    const fallidos = await avisarATodos([correo, sms, notificadorConsola], 'ana@correo.example', 'Hola');
    expect(fallidos).toBe(0);
    expect(correo.enviados).toHaveLength(1);
    expect(sms.enviados).toHaveLength(1);
    expect(notificadorConsola.registro).toHaveLength(1);
    expect(await avisarATodos([correo, sms], '3000000000', 'Hola')).toBe(1);
  });
});

describe('TS 02 · composición en lugar de herencia', () => {
  it('calcula lo mismo que la jerarquía y permite cambiar de esquema', () => {
    const heredados = [new EmpleadoPorHoras('Ana', 160, 20000), new EmpleadoAsalariado('Luis', 4_000_000)];
    const compuestos: Empleado[] = [
      { nombre: 'Ana', pago: { tipo: 'porHoras', horas: 160, tarifa: 20000 } },
      { nombre: 'Luis', pago: { tipo: 'asalariado', salario: 4_000_000 } },
    ];
    expect(nomina(compuestos)).toBe(heredados.reduce((t, e) => t + e.pagoMensual(), 0));
    const ana = cambiarEsquema(compuestos[0]!, { tipo: 'comision', base: 1_000_000, ventas: 10_000_000, porcentaje: 5 });
    expect(nomina([ana])).toBe(1_500_000);
  });
});

describe('TS 02 · respuesta HTTP tipada como clase', () => {
  const cuerpo = JSON.stringify({ id: 'p1', lineas: [{ precio: 1000, cantidad: 3 }] });
  it('el objeto plano no tiene los métodos aunque el tipo diga que sí', () => {
    const pedido = obtenerComoClase(cuerpo);
    expect(pedido.id).toBe('p1');
    expect(typeof pedido.total).toBe('undefined');
    expect(() => pedido.total()).toThrow(TypeError);
  });
  it('construyendo la instancia el método existe', () => {
    expect(obtenerYConstruir(cuerpo).total()).toBe(3000);
  });
});
