import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet, Routes } from '@angular/router';
import { DetallePedidoComponent, ListaConEstadosComponent, Sesion, SiTienePermisoDirective } from './composicion';

interface Pedido { readonly id: string; readonly cliente: string; readonly total: number; }

@Component({
  imports: [SiTienePermisoDirective, ListaConEstadosComponent, CurrencyPipe, RouterLink, RouterOutlet],
  template: `
    <h1>Composición</h1>
    <button *appSiTienePermiso="'pedidos.cancelar'" type="button">Cancelar pedido</button>
    <button type="button" (click)="darPermiso()">Dar permiso de cancelar</button>

    <app-lista-con-estados [elementos]="pedidos()" [cargando]="cargando()" [identificar]="porId">
      <ng-template #elemento let-pedido>{{ pedido.cliente }} — {{ pedido.total | currency }}</ng-template>
      <p vacio>No hay pedidos todavía.</p>
    </app-lista-con-estados>
    <button type="button" (click)="cargar()">Cargar pedidos</button>

    <a routerLink="pedidos/42">Ver pedido 42</a>
    <router-outlet />
  `,
})
class Demo {
  private readonly sesion = inject(Sesion);
  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly cargando = signal(false);
  protected readonly porId = (p: Pedido) => p.id;
  protected darPermiso() { this.sesion.permisos.update((p) => [...p, 'pedidos.cancelar']); }
  protected cargar() {
    this.cargando.set(true);
    setTimeout(() => { this.pedidos.set([{ id: '1', cliente: 'Ana', total: 120000 }]); this.cargando.set(false); }, 300);
  }
}
export default [{ path: '', component: Demo, children: [{ path: 'pedidos/:id', component: DetallePedidoComponent }] }] satisfies Routes;
