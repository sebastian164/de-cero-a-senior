import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DetallePedidoComponent } from './detalle-pedido.component';

describe('n1-04 DetallePedidoComponent con httpResource (después)', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  it('pide, aplica parse y, si cambia id, cancela la petición anterior', async () => {
    const f = TestBed.createComponent(DetallePedidoComponent);
    f.componentRef.setInput('id', '1');
    TestBed.tick();
    const primera = http.expectOne('/api/pedidos/1');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Cargando…');
    f.componentRef.setInput('id', '2');
    TestBed.tick();
    expect(primera.cancelled).toBe(true);                    // la anterior se canceló
    http.expectOne('/api/pedidos/2').flush({ id: 2, cliente_nombre: 'Luis', valor_total: 120000 });
    await f.whenStable();
    expect(f.nativeElement.querySelector('h2').textContent).toBe('Pedido 2');
  });

  it('en error muestra el aviso y Reintentar vuelve a pedir', async () => {
    const f = TestBed.createComponent(DetallePedidoComponent);
    f.componentRef.setInput('id', '7');
    TestBed.tick();
    http.expectOne('/api/pedidos/7').flush('x', { status: 500, statusText: 'Error' });
    await f.whenStable();
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('No se pudo cargar el pedido.');
    f.nativeElement.querySelector('button').click();
    TestBed.tick();
    http.expectOne('/api/pedidos/7').flush({ id: 7, cliente_nombre: 'X', valor_total: 1 });
    await f.whenStable();
    expect(f.nativeElement.textContent).toContain('Pedido 7');
  });
});
