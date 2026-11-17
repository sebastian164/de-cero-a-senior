import { HttpClient, HttpInterceptorFn, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { forkJoin } from 'rxjs';
import { aAutor, aPublicacion } from './adaptadores';
import { Confirmacion, requiereRol, requiereSesion, sinCambiosPerdidos } from './guardas';
import { INTERCEPTORES, interceptorDeAutenticacion, interceptorDeCorrelacion, interceptorDeErrores, interceptorDeRenovacion } from './interceptores';
import { PaginaEditor } from './pantallas';
import { Avisos, Sesion } from './sesion';

@Component({ template: 'x' }) class Vacio {}

const configurar = (interceptores: HttpInterceptorFn[]) => {
  TestBed.configureTestingModule({
    providers: [provideRouter([{ path: '**', component: Vacio }]), provideHttpClient(withInterceptors(interceptores)), provideHttpClientTesting()],
  });
  return { http: TestBed.inject(HttpTestingController), cliente: TestBed.inject(HttpClient), sesion: TestBed.inject(Sesion) };
};

describe('Taller n3-05', () => {
  it('1. adaptadores: traducen y validan lo que llega', () => {
    expect(aAutor({ id: 1, name: 'Ana', username: 'a', email: 'ANA@X.CO', address: { city: 'Cali' }, company: { name: 'ACME' } }))
      .toEqual({ id: '1', nombre: 'Ana', correo: 'ana@x.co', ciudad: 'Cali', empresa: 'ACME' });
    expect(aPublicacion({ userId: 1, id: 9, title: 'T', body: 'línea 1\nlínea 2' })).toEqual({ id: '9', autorId: '1', titulo: 'T', resumen: 'línea 1' });
    expect(() => aPublicacion({ id: 9, title: 'T', body: '' } as never)).toThrow('Respuesta inválida: falta userId');
  });

  it('2. autenticación solo hacia nuestra API, correlación en todas', () => {
    const { http, cliente, sesion } = configurar([interceptorDeCorrelacion, interceptorDeAutenticacion]);
    sesion.ingresar('valido-0', ['cliente']);
    cliente.get('/api/x').subscribe();
    cliente.get('https://terceros.example/y').subscribe();
    const propia = http.expectOne('/api/x').request;
    const ajena = http.expectOne('https://terceros.example/y').request;
    expect(propia.headers.get('Authorization')).toBe('Bearer valido-0');
    expect(ajena.headers.has('Authorization')).toBe(false);
    expect(propia.headers.get('X-Correlation-Id')).toMatch(/^[0-9a-f-]{36}$/);
    expect(ajena.headers.get('X-Correlation-Id')).not.toBe(propia.headers.get('X-Correlation-Id'));
  });

  it('3. tres peticiones simultáneas con 401 provocan UNA renovación y se repiten con el token nuevo (misma correlación)', () => {
    const { http, cliente, sesion } = configurar(INTERCEPTORES);
    sesion.ingresar('vencido', ['cliente']);
    const respuestas: number[][] = [];
    forkJoin([1, 2, 3].map((n) => cliente.get<number>(`/api/privado/${n}`))).subscribe((r) => respuestas.push(r));
    const correlaciones = [1, 2, 3].map((n) => {
      const p = http.expectOne(`/api/privado/${n}`);
      expect(p.request.headers.get('Authorization')).toBe('Bearer vencido');
      const id = p.request.headers.get('X-Correlation-Id');
      p.flush({}, { status: 401, statusText: 'No autorizado' });
      return id;
    });
    const renovar = http.match('/api/token/renovar');
    expect(renovar).toHaveLength(1);                         // una sola renovación
    renovar[0].flush({ token: 'valido-1' });
    [1, 2, 3].forEach((n, i) => {
      const p = http.expectOne(`/api/privado/${n}`);
      expect(p.request.headers.get('Authorization')).toBe('Bearer valido-1');
      expect(p.request.headers.get('X-Correlation-Id')).toBe(correlaciones[i]);
      p.flush(n);
    });
    expect(respuestas).toEqual([[1, 2, 3]]);
    expect(TestBed.inject(Router).url).toBe('/');            // el 401 recuperado no llegó al interceptor de errores
    // Una renovación posterior vuelve a pedir (la anterior se olvidó al terminar).
    sesion.cambiarToken('vencido');
    cliente.get('/api/privado/4').subscribe();
    http.expectOne('/api/privado/4').flush({}, { status: 401, statusText: 'x' });
    expect(http.match('/api/token/renovar')).toHaveLength(1);
  });

  it('2. el orden importa: con errores por dentro de la renovación, un 401 recuperable expulsa al usuario', async () => {
    const { http, cliente, sesion } = configurar([interceptorDeCorrelacion, interceptorDeRenovacion, interceptorDeErrores, interceptorDeAutenticacion]);
    sesion.ingresar('vencido', ['cliente']);
    let valor: unknown;
    cliente.get('/api/privado/1').subscribe((v) => (valor = v));
    http.expectOne('/api/privado/1').flush({}, { status: 401, statusText: 'x' });
    http.expectOne('/api/token/renovar').flush({ token: 'valido-1' });
    http.expectOne('/api/privado/1').flush(1);
    await new Promise((r) => setTimeout(r));
    expect(valor).toBe(1);                                    // la petición se recupera…
    expect(TestBed.inject(Router).url).toBe('/n3-05/taller/ingreso');   // …pero ya lo mandó a ingresar
  });

  it('2. si la renovación también falla, el interceptor de errores lleva al ingreso; un 5xx avisa', async () => {
    const { http, cliente, sesion } = configurar(INTERCEPTORES);
    sesion.ingresar('vencido', ['cliente']);
    cliente.get('/api/privado/1').subscribe({ error: () => undefined });
    http.expectOne('/api/privado/1').flush({}, { status: 401, statusText: 'x' });
    http.expectOne('/api/token/renovar').flush({}, { status: 401, statusText: 'x' });
    await new Promise((r) => setTimeout(r));
    expect(TestBed.inject(Router).url).toBe('/n3-05/taller/ingreso');
    cliente.get('/api/reporte').subscribe({ error: () => undefined });
    http.expectOne('/api/reporte').flush({}, { status: 503, statusText: 'x' });
    expect(TestBed.inject(Avisos).ultimo()).toContain('no está disponible');
  });

  describe('4. guardas', () => {
    let confirma = false;
    beforeEach(() => TestBed.configureTestingModule({
      providers: [
        { provide: Confirmacion, useValue: { preguntar: () => confirma } },
        provideRouter([
          { path: 'admin', canActivate: [requiereSesion, requiereRol('admin')], component: Vacio },
          { path: 'editor', canDeactivate: [sinCambiosPerdidos], component: PaginaEditor },
          { path: '**', component: Vacio },
        ]),
      ],
    }));

    it('sesión y rol', async () => {
      const arnes = await RouterTestingHarness.create();
      const router = TestBed.inject(Router);
      await arnes.navigateByUrl('/admin');
      expect(router.url).toBe('/n3-05/taller/ingreso?volverA=%2Fadmin');
      TestBed.inject(Sesion).ingresar('t', ['cliente']);
      await arnes.navigateByUrl('/admin');
      expect(router.url).toBe('/n3-05/taller/sin-permiso');
      TestBed.inject(Sesion).ingresar('t', ['admin']);
      await arnes.navigateByUrl('/admin');
      expect(router.url).toBe('/admin');
    });

    it('cambios sin guardar: pregunta y respeta la respuesta', async () => {
      const arnes = await RouterTestingHarness.create();
      const router = TestBed.inject(Router);
      await arnes.navigateByUrl('/editor');
      const input = arnes.routeNativeElement!.querySelector('input')!;
      input.value = 'borrador';
      input.dispatchEvent(new Event('input'));
      confirma = false;
      await arnes.navigateByUrl('/otra');
      expect(router.url).toBe('/editor');
      confirma = true;
      await arnes.navigateByUrl('/otra');
      expect(router.url).toBe('/otra');
    });
  });
});
