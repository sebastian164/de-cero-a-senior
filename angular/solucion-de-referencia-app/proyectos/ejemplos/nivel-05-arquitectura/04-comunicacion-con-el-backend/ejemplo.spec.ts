import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PedidoNoEncontrado, PedidosApi, SinConexion } from './pedidos.api';

describe('n5-04 ejemplo: errores con significado', () => {
  beforeEach(() => { vi.useFakeTimers(); TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); });
  afterEach(() => vi.useRealTimers());
  const pedir = (id = '7') => { let resultado: unknown; const http = TestBed.inject(HttpTestingController); TestBed.inject(PedidosApi).obtener(id).subscribe({ next: (v) => (resultado = v), error: (e) => (resultado = e) }); return { http, leer: () => resultado }; };

  it('traduce el DTO', () => {
    const { http, leer } = pedir(); http.expectOne('/api/pedidos/7').flush({ id: 7, cliente_nombre: 'Ana', valor_total: 5 });
    expect(leer()).toEqual({ id: '7', cliente: 'Ana', total: 5 });
  });
  it('404 → PedidoNoEncontrado, sin reintentos (un 404 no mejora por insistir)', () => {
    const { http, leer } = pedir();
    http.expectOne('/api/pedidos/7').flush('', { status: 404, statusText: 'x' });
    expect(leer()).toBeInstanceOf(PedidoNoEncontrado);
    http.verify();
  });
  it('503 se reintenta dos veces con espera creciente y al final se entrega el error', async () => {
    const { http, leer } = pedir();
    http.expectOne('/api/pedidos/7').flush('', { status: 503, statusText: 'x' });
    expect(leer()).toBeUndefined();
    await vi.advanceTimersByTimeAsync(500); http.expectOne('/api/pedidos/7').flush('', { status: 503, statusText: 'x' });
    await vi.advanceTimersByTimeAsync(1000); http.expectOne('/api/pedidos/7').flush('', { status: 503, statusText: 'x' });
    expect((leer() as { status: number }).status).toBe(503);
  });
  it('503 y luego éxito: el usuario no ve el error', async () => {
    const { http, leer } = pedir();
    http.expectOne('/api/pedidos/7').flush('', { status: 503, statusText: 'x' });
    await vi.advanceTimersByTimeAsync(500);
    http.expectOne('/api/pedidos/7').flush({ id: 7, cliente_nombre: 'Ana', valor_total: 5 });
    expect(leer()).toEqual({ id: '7', cliente: 'Ana', total: 5 });
  });
  it('error de red (status 0) → SinConexion tras los reintentos', async () => {
    const { http, leer } = pedir();
    http.expectOne('/api/pedidos/7').error(new ProgressEvent('error'));
    await vi.advanceTimersByTimeAsync(500); http.expectOne('/api/pedidos/7').error(new ProgressEvent('error'));
    await vi.advanceTimersByTimeAsync(1000); http.expectOne('/api/pedidos/7').error(new ProgressEvent('error'));
    expect(leer()).toBeInstanceOf(SinConexion);
  });
  it('tiempo límite de 8 s: sin respuesta falla en vez de cargar eternamente (el TimeoutError no se reintenta con este filtro)', async () => {
    const { http, leer } = pedir();
    http.expectOne('/api/pedidos/7');
    await vi.advanceTimersByTimeAsync(8000);
    expect((leer() as Error).name).toBe('TimeoutError');
  });
});
