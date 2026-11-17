import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { crearRutas } from './tienda/tienda.rutas';
import { precioConIva } from './tienda/catalogo';
import { Sesion } from './tienda/nucleo/sesion';

const montar = () => {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'tienda', loadChildren: () => Promise.resolve(crearRutas('/tienda')) }], withComponentInputBinding())] });
  return RouterTestingHarness.create();
};

describe('n5-01 tienda por funcionalidades', () => {
  it('la API pública de catálogo expone la regla pura', () => expect(precioConIva({ id: 'a', nombre: 'a', precio: 1000 })).toBe(1190));

  it('el almacén de pedidos se provee en las rutas de la funcionalidad y se comparte entre sus páginas', async () => {
    const h = await montar();
    await h.navigateByUrl('/tienda/pedidos');
    TestBed.inject(HttpTestingController).expectOne('/api/pedidos').flush([{ id: 'p1', estado: 'pendiente', lineas: [{ productoId: 'a', cantidad: 1 }] }]);
    const lista = h.routeNativeElement!.querySelector('code')!.textContent;
    await h.navigateByUrl('/tienda/pedidos/p1');
    expect(h.routeNativeElement!.querySelector('code')!.textContent).toBe(lista);       // misma instancia: la lista y el detalle comparten almacén
    expect(h.routeNativeElement!.textContent).toContain('1 líneas');                        // sin pedir de nuevo
    TestBed.inject(HttpTestingController).expectNone('/api/pedidos');
  });

  it('el servicio de las rutas NO es root: sin esas rutas no se puede inyectar', async () => {
    const { AlmacenPedidos } = await import('./tienda/pedidos/estado/almacen-pedidos');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    expect(() => TestBed.inject(AlmacenPedidos)).toThrow(/NG0201|No provider/);
  });

  it('canMatch: sin el rol de administración no se carga admin; con el rol, sí', async () => {
    const h = await montar();
    await h.navigateByUrl('/tienda/admin');
    expect(TestBed.inject(Router).url).toBe('/tienda/sin-permiso');
    TestBed.inject(Sesion).entrarComo('admin');
    await h.navigateByUrl('/tienda/admin');
    expect(h.routeNativeElement!.textContent).toContain('Administración');
  });

  it('HALLAZGO: el servicio de `providers` de la ruta sobrevive a salir de la funcionalidad (no se destruye al desactivarla)', async () => {
    const h = await montar();
    await h.navigateByUrl('/tienda/pedidos');
    TestBed.inject(HttpTestingController).expectOne('/api/pedidos').flush([]);
    const antes = h.routeNativeElement!.querySelector('code')!.textContent;
    await h.navigateByUrl('/tienda');                       // se sale de pedidos
    await h.navigateByUrl('/tienda/pedidos');               // y se vuelve
    TestBed.inject(HttpTestingController).match('/api/pedidos').forEach((r) => r.flush([]));
    expect(h.routeNativeElement!.querySelector('code')!.textContent).toBe(antes);   // la MISMA instancia
  });
});
