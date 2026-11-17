import { CurrencyPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { aPedidos } from './pedidos';
import { PedidosEscritura } from './pedidos.service';

/** 1, 5 y 6. La lista de la sesión 01 con httpResource y los cuatro estados. */
@Component({
  selector: 'app-lista-pedidos-recurso',
  imports: [CurrencyPipe, RouterLink],
  template: `
    <h2>Pedidos</h2>
    @if (errorAlEliminar()) { <p role="alert">No se pudo eliminar el pedido. Vuelve a intentarlo.</p> }
    @if (pedidos.isLoading()) {
      <p>Cargando…</p>
    } @else if (pedidos.error()) {
      <p role="alert">No se pudieron cargar los pedidos.</p>
      <button type="button" (click)="pedidos.reload()">Reintentar</button>
    } @else if (pedidos.hasValue() && pedidos.value().length === 0) {
      <p>No hay pedidos.</p>
    } @else if (pedidos.hasValue()) {
      <ul>
        @for (p of pedidos.value(); track p.id) {
          <li><a [routerLink]="['pedidos', p.id]">{{ p.cliente }}</a> {{ p.total | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}
            <button type="button" (click)="eliminar(p.id)" [attr.aria-label]="'Eliminar ' + p.cliente">Eliminar</button></li>
        }
      </ul>
    }
  `,
})
export class ListaPedidosRecursoComponent {
  private readonly escritura = inject(PedidosEscritura);
  protected readonly errorAlEliminar = signal(false);
  protected readonly pedidos = httpResource(() => '/api/pedidos', { parse: aPedidos });

  protected eliminar(id: string): void {
    // Un recurso solo lee: se dispara cuando cambian sus dependencias. Eliminar es una orden explícita.
    this.errorAlEliminar.set(false);
    this.escritura.eliminar(id).subscribe({
      next: () => this.pedidos.reload(),
      error: () => this.errorAlEliminar.set(true),
    });
  }
}
