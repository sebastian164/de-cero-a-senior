import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BuscadorRecursoComponent } from './buscador.component';
import { ContadorPedidosComponent, NombresPedidosComponent } from './compartido.component';
import { DetalleRecursoComponent } from './detalle.component';
import { ListaPedidosRecursoComponent } from './lista-pedidos.component';
import { urlDeBusqueda } from './pedidos';

const dto = (id: number, cliente_nombre: string) => ({ id, cliente_nombre, valor_total: 1000 * id });
const texto = (f: { nativeElement: HTMLElement }) => f.nativeElement.textContent ?? '';

describe('Taller n1-04', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => vi.useRealTimers());

  describe('6. los cuatro estados de la lista, forzados', () => {
    const crear = () => { const f = TestBed.createComponent(ListaPedidosRecursoComponent); TestBed.tick(); return f; };
    it('cargando', () => { const f = crear(); f.detectChanges(); expect(texto(f)).toContain('Cargando…'); });
    it('error con reintento', async () => {
      const f = crear();
      http.expectOne('/api/pedidos').flush('x', { status: 503, statusText: 'No' });
      await f.whenStable();
      expect(texto(f)).toContain('No se pudieron cargar');
      (f.nativeElement.querySelector('button') as HTMLButtonElement).click();
      TestBed.tick();
      http.expectOne('/api/pedidos').flush([dto(1, 'Ana')]);
      await f.whenStable();
      expect(texto(f)).toContain('Ana');
    });
    it('vacío', async () => { const f = crear(); http.expectOne('/api/pedidos').flush([]); await f.whenStable(); expect(texto(f)).toContain('No hay pedidos.'); });
    it('con datos, y 5. eliminar recarga la lista', async () => {
      const f = crear();
      http.expectOne('/api/pedidos').flush([dto(1, 'Ana'), dto(2, 'Luis')]);
      await f.whenStable();
      (f.nativeElement.querySelector('button[aria-label="Eliminar Ana"]') as HTMLButtonElement).click();
      const borrar = http.expectOne('/api/pedidos/1');
      expect(borrar.request.method).toBe('DELETE');
      borrar.flush(null);
      TestBed.tick();
      http.expectOne('/api/pedidos').flush([dto(2, 'Luis')]);    // recargó
      await f.whenStable();
      expect(texto(f)).not.toContain('Ana');
    });
  });

  it('2. al cambiar de pedido la petición vieja se cancela: la respuesta lenta no pisa a la rápida', async () => {
    const f = TestBed.createComponent(DetalleRecursoComponent);
    f.componentRef.setInput('id', '1');
    TestBed.tick();
    const lenta: TestRequest = http.expectOne('/api/pedidos/1');
    f.componentRef.setInput('id', '2');
    TestBed.tick();
    http.expectOne('/api/pedidos/2').flush(dto(2, 'Luis'));
    expect(lenta.cancelled).toBe(true);
    await f.whenStable();
    expect(texto(f)).toContain('Pedido 2: Luis');
  });

  it('3. la búsqueda no pide nada con menos de dos letras', () => {
    expect(urlDeBusqueda('a')).toBeUndefined();
    expect(urlDeBusqueda(' a ')).toBeUndefined();
    expect(urlDeBusqueda('an')).toBe('/api/pedidos?q=an');
  });

  it('4. las dos esperas (RxJS y debounced) piden una sola vez, 300 ms después de la última tecla', async () => {
    vi.useFakeTimers();
    const f = TestBed.createComponent(BuscadorRecursoComponent);
    TestBed.tick();
    for (const t of ['a', 'an', 'ana']) { f.componentInstance.texto.set(t); TestBed.tick(); await vi.advanceTimersByTimeAsync(100); }
    http.expectNone((r) => r.url.startsWith('/api/pedidos'));
    await vi.advanceTimersByTimeAsync(300);
    TestBed.tick();
    const peticiones = http.match('/api/pedidos?q=ana');
    expect(peticiones).toHaveLength(2);                          // una por cada versión, ninguna para 'a' ni 'an'
    peticiones.forEach((p) => p.flush([dto(1, 'Ana')]));
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    expect(f.componentInstance.conRxjs.value()).toHaveLength(1);
    expect(f.componentInstance.sinRxjs.value()).toHaveLength(1);
  });

  it('7. con el recurso en un servicio, dos componentes provocan UNA petición', async () => {
    @Component({ imports: [ContadorPedidosComponent, NombresPedidosComponent], template: `<app-contador-pedidos /><app-nombres-pedidos />` })
    class Dos {}
    const f = TestBed.createComponent(Dos);
    TestBed.tick();
    const reqs = http.match('/api/pedidos');
    expect(reqs).toHaveLength(1);
    reqs[0].flush([dto(1, 'Ana'), dto(2, 'Luis')]);
    await f.whenStable();
    expect(texto(f)).toContain('Total de pedidos: 2');
    expect(texto(f)).toMatch(/Ana;\s+Luis;/);
  });

  it('7. comparación: dos componentes con su propio recurso hacen DOS peticiones', () => {
    @Component({ imports: [ListaPedidosRecursoComponent], template: `<app-lista-pedidos-recurso /><app-lista-pedidos-recurso />` })
    class DosListas {}
    TestBed.createComponent(DosListas);
    TestBed.tick();
    expect(http.match('/api/pedidos')).toHaveLength(2);
  });
});
