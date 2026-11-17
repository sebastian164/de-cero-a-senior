import { HttpClient } from '@angular/common/http';
import { Component, inject, input, numberAttribute } from '@angular/core';
import { CacheServidor } from './cache-servidor';
import { CarritoServicio } from './carrito.servicio';
import { CarritoStore } from './carrito.store';
import { PedidosUrlPagina } from './pedidos-url.pagina';

interface Producto { id: number; nombre: string; }

@Component({
  selector: 'app-lista-productos-cache',
  template: `<ul [attr.aria-label]="titulo()">@for (p of entrada().datos ?? []; track p.id) { <li>{{ p.nombre }}</li> }</ul>@if (entrada().cargando) { <span>Actualizando…</span> }`,
})
export class ListaProductosCache {
  readonly titulo = input('');
  private readonly http = inject(HttpClient);
  private readonly cache = inject(CacheServidor);
  protected readonly entrada = this.cache.leer('productos', () => this.http.get<Producto[]>('/api/productos'));
}

@Component({
  imports: [PedidosUrlPagina, ListaProductosCache],
  providers: [CarritoServicio, CarritoStore],
  template: `
    <h1>Gestión de estado</h1>
    <app-pedidos-url [estado]="estado()" [pagina]="pagina()" [pestana]="pestana()" />

    <h2>Carrito: servicio y SignalStore</h2>
    <button type="button" (click)="agregar()">Agregar teclado</button>
    <p>Servicio: <output id="serv">{{ servicio.cantidad() }} / {{ servicio.total() }}</output></p>
    <p>SignalStore: <output id="store">{{ store.cantidad() }} / {{ store.total() }}</output></p>

    <h2>Estado de servidor con caché</h2>
    <app-lista-productos-cache titulo="Productos A" />
    <app-lista-productos-cache titulo="Productos B" />
    <button type="button" (click)="crear()">Crear producto</button>
  `,
})
export default class Pagina {
  // Los parámetros de consulta llegan a la página de la ruta (withComponentInputBinding) y ella los pasa a su hija.
  readonly estado = input<'todos' | 'pendiente' | 'pagado' | 'enviado', 'todos' | 'pendiente' | 'pagado' | 'enviado' | undefined>('todos', { transform: (v) => v ?? 'todos' });
  readonly pagina = input(1, { transform: (v: unknown) => numberAttribute(v, 1) });
  readonly pestana = input<'lista' | 'resumen', 'lista' | 'resumen' | undefined>('lista', { transform: (v) => v ?? 'lista' });
  protected readonly servicio = inject(CarritoServicio);
  protected readonly store = inject(CarritoStore);
  private readonly cache = inject(CacheServidor);
  private readonly http = inject(HttpClient);
  protected agregar() { const l = { productoId: 'teclado', precio: 120000, cantidad: 1 }; this.servicio.agregar(l); this.store.agregar(l); }
  protected crear() { void this.cache.mutar(() => this.http.post('/api/productos', { nombre: `Producto ${Date.now() % 1000}` }), ['productos']); }
}
