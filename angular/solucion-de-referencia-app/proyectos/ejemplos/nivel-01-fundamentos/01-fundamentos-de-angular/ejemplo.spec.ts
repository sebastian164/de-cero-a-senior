import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Pedido, totalConIva } from './pedido';
import { PedidosComponent } from './pedidos.component';
import { RelojDeEjemplo } from './servicio-raiz';
import { TarjetaPedidoComponent } from './tarjeta-pedido.component';

const pedido = (id: string, cliente: string): Pedido => ({ id, cliente, items: [{ precio: 100, cantidad: 2 }] });

describe('n1-01 ejemplo después', () => {
  it('totalConIva es una función pura que aplica el 19 %', () => {
    expect(totalConIva(pedido('1', 'Ana'))).toBeCloseTo(238);
  });

  it('@Service() deja el servicio disponible sin registrarlo, como providedIn root', () => {
    TestBed.configureTestingModule({});
    expect(TestBed.inject(RelojDeEjemplo).ahora()).toBe(42);
  });

  it('en Angular 22 OnPush es la estrategia por defecto aunque no se declare', () => {
    @Component({ selector: 'app-sin-estrategia', template: '' })
    class SinEstrategia {}
    expect((SinEstrategia as any).ɵcmp.onPush).toBe(true);
    expect((TarjetaPedidoComponent as any).ɵcmp.onPush).toBe(true);
  });

  describe('PedidosComponent', () => {
    let http: HttpTestingController;
    beforeEach(() => {
      TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
      http = TestBed.inject(HttpTestingController);
    });

    it('muestra cargando, luego la lista, y elimina un pedido', async () => {
      const f = TestBed.createComponent(PedidosComponent);
      await f.whenStable();
      expect(f.nativeElement.textContent).toContain('Cargando…');
      http.expectOne('/api/pedidos').flush([pedido('1', 'Ana'), pedido('2', 'Luis')]);
      await f.whenStable();
      expect(f.nativeElement.querySelectorAll('app-tarjeta-pedido')).toHaveLength(2);
      (f.nativeElement.querySelector('button') as HTMLButtonElement).click();
      const borrar = http.expectOne('/api/pedidos/1');
      expect(borrar.request.method).toBe('DELETE');
      borrar.flush(null);
      await f.whenStable();
      expect(f.nativeElement.textContent).not.toContain('Ana');
      expect(f.nativeElement.textContent).toContain('Luis');
      http.verify();
    });

    it('muestra el error y el vacío', async () => {
      const f = TestBed.createComponent(PedidosComponent);
      http.expectOne('/api/pedidos').flush('x', { status: 500, statusText: 'Error' });
      await f.whenStable();
      expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('No se pudieron cargar');
      const g = TestBed.createComponent(PedidosComponent);
      http.expectOne('/api/pedidos').flush([]);
      await g.whenStable();
      expect(g.nativeElement.textContent).toContain('No hay pedidos.');
    });
  });
});
