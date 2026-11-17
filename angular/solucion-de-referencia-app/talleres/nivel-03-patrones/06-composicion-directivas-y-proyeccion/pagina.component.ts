import { CurrencyPipe } from '@angular/common';
import { Component, signal } from '@angular/core';
import { Clientes, Productos, Resumen } from './despues';
import { DialogoComponent } from './dialogo';
import { AutofocoDirective, ClicFueraDirective } from './directivas';
import { ListaConEstadosComponent } from './lista-con-estados';

interface Pedido { readonly id: string; readonly cliente: string; readonly total: number; }
interface Usuario { readonly correo: string; readonly nombre: string; }

@Component({
  imports: [DialogoComponent, AutofocoDirective, ClicFueraDirective, ListaConEstadosComponent, CurrencyPipe, Clientes, Productos, Resumen],
  template: `
    <h1>Composición</h1>
    <section aria-labelledby="t1"><h2 id="t1">Autofoco y clic fuera</h2>
      <label>Buscar <input appAutofoco /></label>
      <div class="menu" (appClicFuera)="clicsFuera.update((n) => n + 1)"><button type="button">Dentro del menú</button></div>
      <p data-testid="clics-fuera">Clics fuera del menú: {{ clicsFuera() }}</p>
    </section>

    <section aria-labelledby="t2"><h2 id="t2">Diálogo</h2>
      <button type="button" (click)="abrir()">Eliminar cuenta</button>
      @if (abierto()) {
        <app-dialogo id="eliminar" (cerrar)="abierto.set(false)" (fuera)="abierto.set(false)">
          <h3 titulo>¿Eliminar la cuenta?</h3>
          <p cuerpo>Esta acción no se puede deshacer.</p>
          <button acciones type="button" (click)="eliminada.set(true); abierto.set(false)">Sí, eliminar</button>
        </app-dialogo>
      }
      @if (eliminada()) { <p role="status">Cuenta eliminada</p> }
    </section>

    <section aria-labelledby="t3"><h2 id="t3">Lista con estados, dos tipos</h2>
      <app-lista-con-estados [elementos]="pedidos()" [cargando]="buscando()" [identificar]="porId">
        <ng-template #elemento let-pedido>{{ pedido.cliente }} — {{ pedido.total | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</ng-template>
        <ng-template #cargando><p>Buscando pedidos…</p></ng-template>
        <p vacio>No hay pedidos todavía.</p>
      </app-lista-con-estados>
      <button type="button" (click)="cargarPedidos()">Cargar pedidos</button>
      <app-lista-con-estados [elementos]="usuarios" [identificar]="porCorreo">
        <ng-template #elemento let-u><a [href]="'mailto:' + u.correo">{{ u.nombre }}</a></ng-template>
        <p vacio>Sin usuarios.</p>
      </app-lista-con-estados>
    </section>

    <section aria-labelledby="t4"><h2 id="t4">Sin BaseComponent</h2>
      <app-clientes /> · <app-productos /> · <app-resumen />
    </section>
  `,
  styles: `:host ::ng-deep .resaltado { background: #ffe08a; }`,
})
export class PaginaComposicion {
  protected readonly clicsFuera = signal(0);
  protected readonly abierto = signal(false);
  protected readonly eliminada = signal(false);
  protected readonly buscando = signal(false);
  protected readonly pedidos = signal<Pedido[]>([]);
  protected readonly usuarios: Usuario[] = [{ correo: 'ana@x.co', nombre: 'Ana' }, { correo: 'luis@x.co', nombre: 'Luis' }];
  protected readonly porId = (p: Pedido) => p.id;
  protected readonly porCorreo = (u: Usuario) => u.correo;

  /**
   * Sin zone.js el diálogo se crea en la detección de cambios posterior al clic, cuando el evento ya terminó de
   * propagarse: el mismo clic que abre no llega a su escucha de document:click (lo comprueban la unitaria y la E2E).
   */
  protected abrir() { this.abierto.set(true); }
  protected cargarPedidos() {
    this.buscando.set(true);
    setTimeout(() => { this.pedidos.set([{ id: '1', cliente: 'Ana', total: 120000 }, { id: '2', cliente: 'Luis', total: 80000 }]); this.buscando.set(false); }, 300);
  }
}
