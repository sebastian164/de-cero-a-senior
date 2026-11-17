import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AlmacenCarrito, CarritoApi } from './almacen-carrito';

const PRODUCTOS = [
  { productoId: 't', nombre: 'Teclado', precio: 120000, categoria: 'perifericos' },
  { productoId: 'm', nombre: 'Mouse', precio: 40000, categoria: 'perifericos' },
  { productoId: 'p', nombre: 'Pantalla', precio: 700000, categoria: 'monitores' },
];

/** 2. Ícono con contador: en el encabezado. */
@Component({
  selector: 'app-icono-carrito',
  template: `<span aria-label="Carrito" role="img">🛒</span> <span data-testid="contador">{{ almacen.cantidad() }}</span>`,
})
export class IconoCarrito { protected readonly almacen = inject(AlmacenCarrito); }

/** 2. Página del carrito: otro lugar de la pantalla, los mismos datos. 5. El filtro de categoría vive en la URL. */
@Component({
  selector: 'app-pagina-carrito',
  imports: [IconoCarrito, CurrencyPipe, RouterLink],
  template: `
    <header><app-icono-carrito /></header>
    <h1>Tienda</h1>
    <nav aria-label="Categorías">
      <a routerLink="." [queryParams]="{ categoria: null }">Todas</a> ·
      <a routerLink="." [queryParams]="{ categoria: 'perifericos' }">Periféricos</a> ·
      <a routerLink="." [queryParams]="{ categoria: 'monitores' }">Monitores</a>
    </nav>
    <ul aria-label="Productos">
      @for (p of productos(); track p.productoId) {
        <li>{{ p.nombre }} <button type="button" (click)="almacen.agregar(p)">Agregar {{ p.nombre }}</button></li>
      }
    </ul>
    <h2>Tu carrito ({{ almacen.cantidad() }})</h2>
    <ul aria-label="Líneas">
      @for (l of almacen.lineas(); track l.productoId) {
        <li>{{ l.nombre }} × {{ l.cantidad }}
          <button type="button" (click)="almacen.cambiarCantidad(l.productoId, l.cantidad - 1)" [attr.aria-label]="'Quitar uno de ' + l.nombre">−</button>
          <button type="button" (click)="almacen.quitar(l.productoId)" [attr.aria-label]="'Quitar ' + l.nombre">Quitar</button></li>
      }
    </ul>
    <p>Subtotal {{ almacen.subtotal() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} · Total {{ almacen.total() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
    <button type="button" (click)="almacen.vaciar()">Vaciar</button>
    <label><input type="checkbox" (change)="api.fallar = $any($event.target).checked" /> Simular fallo del servidor</label>
    @if (almacen.error(); as e) { <p role="alert">{{ e }}</p> }
  `,
})
export class PaginaCarrito {
  protected readonly almacen = inject(AlmacenCarrito);
  protected readonly api = inject(CarritoApi);
  /** Llega del parámetro de consulta ?categoria= (withComponentInputBinding). Sin parámetro, undefined. */
  readonly categoria = input<string | undefined>();
  protected readonly productos = computed(() => PRODUCTOS.filter((p) => !this.categoria() || p.categoria === this.categoria()));
}
