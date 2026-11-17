import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { aErrorDeDominio, ErrorDeServidor, ErrorDeValidacion, esTransitorio, mensajeParaElUsuario, PedidoNoEncontrado, SinConexion, TiempoAgotado } from './errores';
import { PedidosApi, TIEMPO_LIMITE_MS } from './pedidos.api';

const http = (status: number, error?: unknown) => new HttpErrorResponse({ status, error });

describe('n5-04 traducción de errores (función pura)', () => {
  it.each([
    [0, SinConexion], [404, PedidoNoEncontrado], [422, ErrorDeValidacion], [500, ErrorDeServidor], [503, ErrorDeServidor], [409, ErrorDeServidor],
  ] as const)('HTTP %i → %o', (estado, clase) => expect(aErrorDeDominio(http(estado))).toBeInstanceOf(clase));
  it('el 422 conserva los campos', () => expect((aErrorDeDominio(http(422, { campos: { cliente: 'x' } })) as ErrorDeValidacion).campos).toEqual({ cliente: 'x' }));
  it('un TimeoutError es TiempoAgotado', () => { const e = new Error('t'); e.name = 'TimeoutError'; expect(aErrorDeDominio(e)).toBeInstanceOf(TiempoAgotado); });
  it('solo se reintenta lo transitorio', () => { expect([0, 500, 503, 404, 422, 409, 401].map((s) => esTransitorio(http(s)))).toEqual([true, true, true, false, false, false, false]); });
  it('cada error tiene un mensaje distinto y ninguno es "Ocurrió un error"', () => {
    const mensajes = [0, 404, 422, 500].map((s) => mensajeParaElUsuario(http(s)));
    expect(new Set(mensajes).size).toBe(4);
    mensajes.forEach((m) => expect(m).not.toMatch(/ocurri/i));
  });
});

describe('n5-04 PedidosApi', () => {
  beforeEach(() => { vi.useFakeTimers(); TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TIEMPO_LIMITE_MS, useValue: 1000 }] }); });
  afterEach(() => vi.useRealTimers());

  it('un POST no se reintenta aunque falle con 503', () => {
    let error: unknown; TestBed.inject(PedidosApi).crear('Ana').subscribe({ error: (e) => (error = e) });
    const h = TestBed.inject(HttpTestingController);
    h.expectOne('/api/pedidos').flush('', { status: 503, statusText: 'x' });
    expect(error).toBeInstanceOf(ErrorDeServidor);
    h.verify();
  });
  it('un 422 del POST llega como ErrorDeValidacion', () => {
    let error: unknown; TestBed.inject(PedidosApi).crear('').subscribe({ error: (e) => (error = e) });
    TestBed.inject(HttpTestingController).expectOne('/api/pedidos').flush({ campos: { cliente: 'obligatorio' } }, { status: 422, statusText: 'x' });
    expect((error as ErrorDeValidacion).campos).toEqual({ cliente: 'obligatorio' });
  });
  it('el tiempo límite configurable corta la espera', async () => {
    let error: unknown; TestBed.inject(PedidosApi).obtener('1').subscribe({ error: (e) => (error = e) });
    TestBed.inject(HttpTestingController).expectOne('/api/pedidos/1');
    await vi.advanceTimersByTimeAsync(1000);                 // 1.er intento vencido: en un GET se reintenta
    expect(error).toBeUndefined();
    await vi.advanceTimersByTimeAsync(500 + 1000);
    await vi.advanceTimersByTimeAsync(1000 + 1000);          // tres intentos agotados
    expect(error).toBeInstanceOf(TiempoAgotado);
  });
  it('dos 503 y luego éxito: se reintenta y el usuario recibe el pedido', async () => {
    let resultado: unknown; TestBed.inject(PedidosApi).obtener('1').subscribe((p) => (resultado = p));
    const h = TestBed.inject(HttpTestingController);
    h.expectOne('/api/pedidos/1').flush('', { status: 503, statusText: 'x' });
    await vi.advanceTimersByTimeAsync(500); h.expectOne('/api/pedidos/1').flush('', { status: 503, statusText: 'x' });
    await vi.advanceTimersByTimeAsync(1000); h.expectOne('/api/pedidos/1').flush({ id: 1, cliente_nombre: 'Ana', valor_total: 2 });
    expect(resultado).toEqual({ id: '1', cliente: 'Ana', total: 2 });
  });
});
