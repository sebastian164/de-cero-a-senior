import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Pedido, PedidosApi } from './adaptador';
import { Avisos, Sesion } from './sesion';

@Component({
  imports: [RouterLink, CurrencyPipe, DatePipe],
  template: `
    <h1>Pedidos (adaptador)</h1>
    <p>Sesión: {{ sesion.activa() ? 'activa' : 'sin sesión' }}</p>
    <ul>@for (p of pedidos(); track p.id) {
      <li><a [routerLink]="['pedidos', p.id]">{{ p.cliente }}</a> · {{ p.estado }} · {{ p.total | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} · {{ p.creadoEn | date: 'yyyy-MM-dd' }}</li>
    }</ul>
    <a routerLink="admin">Administración</a>
    <button type="button" (click)="reporte()">Pedir reporte</button>
    @if (avisos.ultimo(); as a) { <p role="alert">{{ a }}</p> }
  `,
})
export class ListaPedidos {
  protected readonly sesion = inject(Sesion);
  protected readonly avisos = inject(Avisos);
  private readonly http = inject(HttpClient);
  protected readonly pedidos = toSignal(inject(PedidosApi).listar(), { initialValue: [] });
  protected reporte() { this.http.get('/api/reportes').subscribe({ error: () => undefined }); }
}

@Component({
  imports: [CurrencyPipe, RouterLink],
  template: `<h1>Pedido {{ pedido().id }}</h1><p>{{ pedido().cliente }} · {{ pedido().total | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p><a routerLink="../..">Volver</a>`,
})
export class DetallePedido {
  // En el componente: el nombre de la entrada coincide con la clave de "resolve"
  readonly pedido = input.required<Pedido>();
}

@Component({ template: `<h1>Administración</h1><p>Solo para administradores.</p>` })
export class Admin {}

/** Pantallas de /ingreso y /sin-permiso, a las que apuntan la guarda y el interceptor. */
@Component({
  template: `
    <h1>Ingreso</h1>
    <button type="button" (click)="entrar(['cliente'])">Ingresar como cliente</button>
    <button type="button" (click)="entrar(['admin'])">Ingresar como admin</button>
  `,
})
export class Ingreso {
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);
  readonly volverA = input<string>();
  protected entrar(roles: ('cliente' | 'admin')[]) {
    this.sesion.ingresar('t-123', roles);
    this.router.navigateByUrl(this.volverA() ?? '/n3-05/ejemplo');
  }
}
@Component({ template: `<h1>Sin permiso</h1>` })
export class SinPermiso {}
