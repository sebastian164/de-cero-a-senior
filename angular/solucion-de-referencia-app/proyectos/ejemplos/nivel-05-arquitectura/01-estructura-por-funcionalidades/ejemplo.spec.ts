import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { rutas } from './rutas';
import { SesionDemo } from './requiere-rol';

describe('n5-01 ejemplo: rutas con carga diferida y canMatch', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'n5-01/ejemplo', children: rutas }])] }));
  it('inicio, pedidos con almacén compartido y admin protegido por canMatch', async () => {
    const h = await RouterTestingHarness.create('/n5-01/ejemplo');
    expect(h.routeNativeElement!.textContent).toContain('Estructura por funcionalidades');
    await h.navigateByUrl('/n5-01/ejemplo/pedidos');
    const almacen = h.routeNativeElement!.querySelector('p')!.textContent;
    await h.navigateByUrl('/n5-01/ejemplo/pedidos/42');
    expect(h.routeNativeElement!.textContent).toContain('Pedido 42');
    expect(h.routeNativeElement!.querySelector('p')!.textContent).toBe(almacen);     // el mismo almacén
    await h.navigateByUrl('/n5-01/ejemplo/admin');                                   // sin rol: canMatch redirige al inicio
    expect(TestBed.inject(Router).url).toBe('/n5-01/ejemplo');
  });
  it('con el rol, admin carga', async () => {
    TestBed.inject(SesionDemo).rol.set('admin');
    const h = await RouterTestingHarness.create('/n5-01/ejemplo/admin');
    expect(h.routeNativeElement!.textContent).toContain('Administración');
  });
});
