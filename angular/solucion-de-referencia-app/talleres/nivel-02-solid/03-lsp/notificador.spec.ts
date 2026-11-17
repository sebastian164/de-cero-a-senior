import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MensajeInvalido, NotificacionFallida, Notificador, NotificadorEnConsola, NotificadorEnPantalla, NotificadorRemoto } from './notificador';

/**
 * 1. Una sola batería de contrato. Cada implementación aporta cómo crearla, cómo observar la entrega y cómo provocar un fallo.
 */
interface Arnes {
  crear(): Notificador;
  entregados(): string[];
  completarEntrega?(): void;   // para el remoto: responder la petición pendiente
  provocarFallo(): void;
}

const contrato = (nombre: string, arnes: () => Arnes) => describe(`contrato de Notificador: ${nombre}`, () => {
  let a: Arnes;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    a = arnes();
  });

  it('entrega el mensaje recortado', async () => {
    const n = a.crear();
    const p = n.notificar('  Hola  ');
    a.completarEntrega?.();
    await p;
    expect(a.entregados()).toEqual(['Hola']);
  });

  it('rechaza un mensaje vacío con MensajeInvalido y no entrega nada', async () => {
    await expect(a.crear().notificar('   ')).rejects.toBeInstanceOf(MensajeInvalido);
    expect(a.entregados()).toEqual([]);
  });

  it('si el destino falla, rechaza con NotificacionFallida', async () => {
    const n = a.crear();
    a.provocarFallo();
    await expect(n.notificar('x')).rejects.toBeInstanceOf(NotificacionFallida);
  });
});

contrato('en pantalla', () => {
  let n: NotificadorEnPantalla;
  return {
    crear: () => (n = TestBed.inject(NotificadorEnPantalla)),
    entregados: () => [...(n?.avisos() ?? [])],
    provocarFallo: () => { (n as any).lista = { update: () => { throw new Error('vista destruida'); } }; },
  };
});

contrato('en consola', () => {
  const salida: string[] = [];
  let n: NotificadorEnConsola;
  return {
    crear: () => { n = TestBed.inject(NotificadorEnConsola); n.registrar = (t) => salida.push(t.replace('[aviso] ', '')); return n; },
    entregados: () => salida,
    provocarFallo: () => { n.registrar = () => { throw new Error('consola caída'); }; },
  };
});

contrato('remoto', () => {
  const recibidos: string[] = [];
  let fallar = false;
  const http = () => TestBed.inject(HttpTestingController);
  const responder = () => {
    for (const r of http().match('/api/notificaciones')) {
      if (fallar) r.flush('x', { status: 500, statusText: 'Error' });
      else { recibidos.push(r.request.body.mensaje); r.flush({ id: 1 }); }
    }
  };
  return {
    crear: () => TestBed.inject(NotificadorRemoto),
    entregados: () => recibidos,
    completarEntrega: responder,
    provocarFallo: () => { fallar = true; queueMicrotask(responder); },
  };
});
