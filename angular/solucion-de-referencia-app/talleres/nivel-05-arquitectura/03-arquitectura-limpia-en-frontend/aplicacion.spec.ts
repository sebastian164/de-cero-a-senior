import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { AsistenteSolicitud } from './aplicacion/asistente';
import { MODO_DATOS, provideSolicitudes } from './datos/configuracion';
import { SolicitudesEnMemoria, SolicitudesHttp } from './datos/solicitudes-http';
import { RepositorioSolicitudes, SolicitudRechazada } from './dominio/puertos';
import { Solicitud } from './dominio/solicitud';

const montar = (repo: Partial<RepositorioSolicitudes>) => {
  TestBed.configureTestingModule({ providers: [AsistenteSolicitud, { provide: RepositorioSolicitudes, useValue: repo }] });
  return TestBed.inject(AsistenteSolicitud);
};
const llenar = (a: AsistenteSolicitud, extra: Partial<Solicitud> = {}) => a.actualizar({ nombre: 'Ana Gómez', ingresoMensual: 5_000_000, monto: 10_000_000, plazoMeses: 24, ...extra });

describe('n5-03 aplicación: el asistente con un repositorio falso', () => {
  it('no avanza con errores y los muestra; avanza cuando el paso es válido', () => {
    const a = montar({});
    a.avanzar();
    expect(a.paso()).toBe('personales'); expect(a.mostrarErrores()).toBe(true); expect(a.errores()).toHaveProperty('nombre');
    llenar(a); a.avanzar();
    expect(a.paso()).toBe('ingresos'); expect(a.mostrarErrores()).toBe(false);
  });
  it('el paso del codeudor aparece al pedir un monto grande', () => {
    const a = montar({});
    llenar(a, { monto: 30_000_000, ingresoMensual: 100_000_000 });
    expect(a.pasos()).toContain('codeudor');
  });
  it('envía por el puerto y guarda el id', () => {
    const enviar = vi.fn((): Observable<{ id: number }> => of({ id: 5 }));
    const a = montar({ enviar }); llenar(a);
    a.avanzar(); a.avanzar(); expect(a.esUltimo()).toBe(true);
    a.enviar();
    expect(enviar).toHaveBeenCalledTimes(1); expect(a.enviada()).toBe(5);
  });
  it('un rechazo del servidor lleva al paso de ingresos con su mensaje', () => {
    const a = montar({ enviar: () => throwError(() => new SolicitudRechazada({ monto: 'El monto máximo es 50.000.000' })) });
    llenar(a, { monto: 60_000_000, ingresoMensual: 500_000_000, conCodeudor: true, codeudor: { nombre: 'Luis Pérez', documento: '1234567' } });
    a.avanzar(); a.avanzar(); a.avanzar(); a.enviar();
    expect(a.paso()).toBe('ingresos'); expect(a.errores()['monto']).toBe('El monto máximo es 50.000.000'); expect(a.enviada()).toBeUndefined();
  });
  it('un rechazo en otro campo lleva al paso de ese campo (documento del codeudor)', () => {
    const a = montar({ enviar: () => throwError(() => new SolicitudRechazada({ codeudorDocumento: 'Documento reportado' })) });
    llenar(a, { monto: 30_000_000, ingresoMensual: 100_000_000, codeudor: { nombre: 'Luis Pérez', documento: '1234567' } });
    a.avanzar(); a.avanzar(); a.avanzar(); expect(a.paso()).toBe('revision');
    a.enviar();
    expect(a.paso()).toBe('codeudor'); expect(a.errores()['codeudorDocumento']).toBe('Documento reportado');
  });
  it('un 500 o la falta de red no se pierde: queda un error general y se puede reintentar', () => {
    let intento = 0;
    const a = montar({ enviar: () => (intento++ === 0 ? throwError(() => new Error('500')) : of({ id: 9 })) });
    llenar(a); a.avanzar(); a.avanzar();
    a.enviar();
    expect(a.errorGeneral()).toMatch(/No se pudo enviar/); expect(a.enviando()).toBe(false); expect(a.paso()).toBe('revision'); expect(a.enviada()).toBeUndefined();
    a.enviar();
    expect(a.errorGeneral()).toBeUndefined(); expect(a.enviada()).toBe(9);
  });
});

describe('n5-03 datos: dos adaptadores, uno por configuración', () => {
  const solicitud = { nombre: 'A' } as Solicitud;
  it('modo "http": el adaptador usa HttpClient y traduce el 422', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), ...provideSolicitudes(), { provide: MODO_DATOS, useValue: 'http' }] });
    const repo = TestBed.inject(RepositorioSolicitudes);
    expect(repo).toBeInstanceOf(SolicitudesHttp);
    let error: unknown; repo.enviar(solicitud).subscribe({ error: (e) => (error = e) });
    TestBed.inject(HttpTestingController).expectOne('/api/solicitudes').flush({ campos: { monto: 'x' } }, { status: 422, statusText: 'x' });
    expect(error).toBeInstanceOf(SolicitudRechazada);
    expect((error as SolicitudRechazada).campos).toEqual({ monto: 'x' });
  });
  it('modo "memoria": no hay HTTP', async () => {
    TestBed.configureTestingModule({ providers: [...provideSolicitudes(), { provide: MODO_DATOS, useValue: 'memoria' }] });   // sin provideHttpClient
    const repo = TestBed.inject(RepositorioSolicitudes);
    expect(repo).toBeInstanceOf(SolicitudesEnMemoria);
    const { firstValueFrom } = await import('rxjs');
    expect(await firstValueFrom(repo.enviar(solicitud))).toEqual({ id: -1 });
  });
});
