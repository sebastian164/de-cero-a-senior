import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, Injectable, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

export interface Producto { id: number; nombre: string; precio: number; }

/** Tienda mínima que sirve de aplicación a la prueba E2E del README. */
@Injectable({ providedIn: 'root' })
export class Tienda {
  private readonly http = inject(HttpClient);
  readonly catalogo = signal<Producto[]>([]);
  readonly carrito = signal<Producto[]>([]);
  readonly total = computed(() => this.carrito().reduce((s, p) => s + p.precio, 0));
  readonly mensaje = signal('');
  readonly error = signal('');
  constructor() { this.http.get<Producto[]>('/api/catalogo').subscribe((c) => this.catalogo.set(c)); }
  agregar(p: Producto) { this.carrito.update((c) => [...c, p]); }
  confirmar() {
    this.error.set(''); this.mensaje.set('');
    this.http.post('/api/pedidos', { items: this.carrito().map((p) => p.id) }).subscribe({
      next: () => { this.carrito.set([]); this.mensaje.set('Pedido confirmado'); },
      error: () => this.error.set('No se pudo confirmar el pedido; intenta de nuevo'),
    });
  }
}

@Component({
  imports: [CurrencyPipe],
  template: `
    <h1>Catálogo</h1>
    <ul>
      @for (p of tienda.catalogo(); track p.id) {
        <li>{{ p.nombre }} — {{ p.precio | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}
          <button type="button" (click)="tienda.agregar(p)" [attr.aria-label]="'Agregar ' + p.nombre + ' al carrito'">Agregar</button></li>
      }
    </ul>
  `,
})
export class Catalogo { protected readonly tienda = inject(Tienda); }

@Component({
  imports: [CurrencyPipe],
  template: `
    <h1>Tu carrito</h1>
    <ul>@for (p of tienda.carrito(); track $index) { <li>{{ p.nombre }}</li> } @empty { <li>El carrito está vacío</li> }</ul>
    <p>Total: {{ tienda.total() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
    <button type="button" (click)="tienda.confirmar()" [disabled]="!tienda.carrito().length">Confirmar pedido</button>
    @if (tienda.error()) { <p role="alert">{{ tienda.error() }}</p> }
  `,
})
export class Carrito { protected readonly tienda = inject(Tienda); }

@Component({
  imports: [RouterLink, RouterOutlet],
  template: `
    <nav aria-label="Tienda"><a routerLink="catalogo">Catálogo</a> · <a routerLink="carrito">Carrito ({{ tienda.carrito().length }})</a></nav>
    <router-outlet />
    @if (tienda.mensaje()) { <p role="status">{{ tienda.mensaje() }}</p> }
  `,
})
export class Marco { protected readonly tienda = inject(Tienda); }
