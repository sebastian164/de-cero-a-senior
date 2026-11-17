import { CurrencyPipe } from '@angular/common';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { DetallePedidoComponent, ListaConEstadosComponent, Sesion, SiTienePermisoDirective } from './composicion';

interface Pedido { id: string; cliente: string; total: number; }

@Component({
  imports: [SiTienePermisoDirective, ListaConEstadosComponent, CurrencyPipe],
  template: `
    <button *appSiTienePermiso="'pedidos.cancelar'" type="button">Cancelar pedido</button>
    <app-lista-con-estados [elementos]="pedidos()" [cargando]="cargando()" [identificar]="porId">
      <ng-template #elemento let-pedido>{{ pedido.cliente }} — {{ pedido.total | currency }}</ng-template>
      <p vacio>No hay pedidos todavía.</p>
    </app-lista-con-estados>
  `,
})
class Anfitrion {
  readonly pedidos = signal<Pedido[]>([]);
  readonly cargando = signal(true);
  readonly porId = (p: Pedido) => p.id;
}

describe('n3-06 ejemplos', () => {
  it('la directiva muestra el botón solo con el permiso y reacciona al cambio', async () => {
    const f = TestBed.createComponent(Anfitrion);
    await f.whenStable();
    expect(f.nativeElement.querySelector('button')).toBeNull();
    TestBed.inject(Sesion).permisos.set(['pedidos.cancelar']);
    await f.whenStable();
    expect(f.nativeElement.querySelector('button').textContent).toBe('Cancelar pedido');
  });

  it('la lista recorre cargando → vacío → elementos', async () => {
    const f = TestBed.createComponent(Anfitrion);
    await f.whenStable();
    expect(f.nativeElement.textContent).toContain('Cargando…');
    f.componentInstance.cargando.set(false);
    await f.whenStable();
    expect(f.nativeElement.textContent).toContain('No hay pedidos todavía.');
    f.componentInstance.pedidos.set([{ id: '1', cliente: 'Ana', total: 12 }]);
    await f.whenStable();
    expect(f.nativeElement.querySelector('li').textContent).toMatch(/^Ana — US\$\s12,00$/);   // currency sin argumentos: USD (con LOCALE_ID es-CO)
  });

  it('usarParametroDeRuta lee el parámetro como señal', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'pedidos/:id', component: DetallePedidoComponent }])] });
    const arnes = await RouterTestingHarness.create();
    await arnes.navigateByUrl('/pedidos/42');
    expect(arnes.routeNativeElement?.textContent).toBe('Pedido 42');
  });
});
