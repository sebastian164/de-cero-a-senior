import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { aPedido, PedidosApi } from './adaptador';
import { pedidoDeLaRuta, requiereRol, requiereSesion } from './guardas';
import { interceptorDeAutenticacion, interceptorDeErrores } from './interceptores';
import { DetallePedido } from './pantallas';
import { Avisos, Sesion } from './sesion';

@Component({ template: 'x' }) class Vacio {}

describe('n3-05 ejemplo', () => {
  it('aPedido traduce el DTO al modelo', () => {
    expect(aPedido({ id: 7, fecha_creacion: '2026-03-14T10:00:00Z', cliente: { nombre_completo: 'Ana' }, estado: 2, valor_centavos: 12345 }))
      .toEqual({ id: '7', creadoEn: new Date('2026-03-14T10:00:00Z'), cliente: 'Ana', estado: 'pagado', total: 123.45 });
  });

  describe('interceptores', () => {
    let http: HttpTestingController;
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [provideRouter([{ path: '**', component: Vacio }]), provideHttpClient(withInterceptors([interceptorDeAutenticacion, interceptorDeErrores])), provideHttpClientTesting()],
      });
      http = TestBed.inject(HttpTestingController);
    });

    it('agrega el token solo con sesión', () => {
      const cliente = TestBed.inject(HttpClient);
      cliente.get('/api/a').subscribe();
      expect(http.expectOne('/api/a').request.headers.has('Authorization')).toBe(false);
      TestBed.inject(Sesion).ingresar('t-1', ['cliente']);
      cliente.get('/api/b').subscribe();
      expect(http.expectOne('/api/b').request.headers.get('Authorization')).toBe('Bearer t-1');
    });

    it('un 5xx avisa y deja pasar el error; un 401 lleva a /ingreso', async () => {
      const cliente = TestBed.inject(HttpClient);
      const errores: number[] = [];
      cliente.get('/api/a').subscribe({ error: (e) => errores.push(e.status) });
      http.expectOne('/api/a').flush({}, { status: 503, statusText: 'x' });
      expect(TestBed.inject(Avisos).ultimo()).toBe('El servicio no está disponible. Intenta más tarde.');
      cliente.get('/api/b').subscribe({ error: (e) => errores.push(e.status) });
      http.expectOne('/api/b').flush({}, { status: 401, statusText: 'x' });
      await TestBed.inject(Router).navigated;
      await new Promise((r) => setTimeout(r));
      expect(TestBed.inject(Router).url).toBe('/ingreso');
      expect(errores).toEqual([503, 401]);   // quien llamó también recibe el error
    });
  });

  describe('guardas y resolver', () => {
    beforeEach(() => TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        provideRouter([
          { path: 'admin', canActivate: [requiereSesion, requiereRol('admin')], component: Vacio },
          { path: 'pedidos/:id', resolve: { pedido: pedidoDeLaRuta }, component: DetallePedido },
          { path: '**', component: Vacio },
        ], withComponentInputBinding()),
      ],
    }));

    it('sin sesión lleva a /ingreso con volverA; sin rol, a /sin-permiso; con rol, entra', async () => {
      const arnes = await RouterTestingHarness.create();
      const router = TestBed.inject(Router);
      await arnes.navigateByUrl('/admin');
      expect(router.url).toBe('/ingreso?volverA=%2Fadmin');
      TestBed.inject(Sesion).ingresar('t', ['cliente']);
      await arnes.navigateByUrl('/admin');
      expect(router.url).toBe('/sin-permiso');
      TestBed.inject(Sesion).ingresar('t', ['admin']);
      await arnes.navigateByUrl('/admin');
      expect(router.url).toBe('/admin');
    });

    it('el resolver entrega el pedido como entrada y la navegación espera', async () => {
      const arnes = await RouterTestingHarness.create();
      const navegacion = arnes.navigateByUrl('/pedidos/2');
      await new Promise((r) => setTimeout(r));
      TestBed.inject(HttpTestingController).expectOne('/api/pedidos/2')
        .flush({ id: 2, fecha_creacion: '2026-01-01T00:00:00Z', cliente: { nombre_completo: 'Luis' }, estado: 1, valor_centavos: 100 });
      await navegacion;
      expect(arnes.routeNativeElement?.textContent).toContain('Pedido 2');
      expect(arnes.routeNativeElement?.textContent).toContain('Luis');
      expect(TestBed.inject(PedidosApi)).toBeTruthy();
    });
  });
});
