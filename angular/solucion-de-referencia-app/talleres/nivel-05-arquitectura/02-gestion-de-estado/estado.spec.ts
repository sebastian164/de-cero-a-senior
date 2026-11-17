import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ResumenDuplicado, ResumenUnico } from './duplicado';
import { PedidosUrlPagina } from './pedidos-url.pagina';

describe('n5-02 estado duplicado', () => {
  it('ANTES: dos copias se desincronizan; DESPUÉS: una fuente de verdad', () => {
    const antes = new ResumenDuplicado(); antes.agregar(2); antes.agregar(3); antes.quitarPrimera();
    expect(antes.lineas().reduce((s, n) => s + n, 0)).toBe(3);
    expect(antes.cantidad()).toBe(5);                                    // la copia mintió
    const despues = new ResumenUnico(); despues.agregar(2); despues.agregar(3); despues.quitarPrimera();
    expect(despues.cantidad()).toBe(3);
  });
});

describe('n5-02 estado en la URL (filtro, página y pestaña)', () => {
  const montar = async (url: string) => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'p', component: PedidosUrlPagina }], withComponentInputBinding())] });
    return RouterTestingHarness.create(url);
  };
  it('los tres parámetros son entradas con valores por defecto', async () => {
    const h = await montar('/p');
    const r = TestBed.inject(HttpTestingController).expectOne((x) => x.url === '/api/pedidos');
    expect([r.request.params.get('estado'), r.request.params.get('pagina')]).toEqual(['todos', '1']);
    r.flush({ items: [] });
    await h.fixture.whenStable();
    expect(h.routeNativeElement!.textContent).toContain('Página 1');
  });
  it('cambiar de página navega (la URL cambia) y conserva el resto de parámetros', async () => {
    const h = await montar('/p?estado=pagado&pestana=lista');
    TestBed.inject(HttpTestingController).expectOne((x) => x.url === '/api/pedidos').flush({ items: [] });
    await h.fixture.whenStable();
    (h.routeNativeElement!.querySelectorAll('button')[1] as HTMLButtonElement).click();   // Siguiente
    await h.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/p?estado=pagado&pestana=lista&pagina=2');
    const r = TestBed.inject(HttpTestingController).expectOne((x) => x.url === '/api/pedidos');
    expect(r.request.params.get('pagina')).toBe('2');
    r.flush({ items: [] });
  });
});
