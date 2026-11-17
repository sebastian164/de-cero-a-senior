import { httpResource } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ErrorDeValidacion, mensajeParaElUsuario, aErrorDeDominio } from './errores';
import { aPedido, Pedido, PedidoDto, PedidosApi } from './pedidos.api';

/** Pantalla 1: los cuatro estados (cargando, éxito, vacío, error) de una lectura con `httpResource`. */
@Component({
  selector: 'app-lista',
  template: `
    <h2>Pedidos</h2>
    <button type="button" (click)="modo.set('')">Normal</button>
    <button type="button" (click)="modo.set('vacio=1')">Simular vacío</button>
    <button type="button" (click)="modo.set('fallar=1')">Simular error</button>
    @switch (estado()) {
      @case ('cargando') { <p role="status" aria-busy="true">Cargando pedidos…</p> }
      @case ('error') { <p role="alert">{{ mensaje() }} <button type="button" (click)="pedidos.reload()">Reintentar</button></p> }
      @case ('vacio') { <p>Aún no tienes pedidos.</p> }
      @default { <ul>@for (p of lista(); track p.id) { <li><a [routerLink]="['/n5-04/taller/pedido', p.id]">{{ p.cliente }}</a>: {{ p.total }}</li> }</ul> }
    }
  `,
  imports: [RouterLink],
})
export class ListaPedidos {
  protected readonly modo = signal('');
  protected readonly pedidos = httpResource<PedidoDto[]>(() => `/api/pedidos${this.modo() ? '?' + this.modo() : ''}`);
  protected readonly lista = computed<Pedido[]>(() => (this.pedidos.hasValue() ? this.pedidos.value().map(aPedido) : []));
  protected readonly estado = computed(() => this.pedidos.isLoading() ? 'cargando' : this.pedidos.error() ? 'error' : this.lista().length === 0 ? 'vacio' : 'listo');
  protected readonly mensaje = computed(() => mensajeParaElUsuario(this.pedidos.error()));
}

/** Pantalla 2: crear. Un 422 se muestra junto al campo; una caída de red conserva lo escrito. */
@Component({
  selector: 'app-nuevo',
  imports: [FormsModule],
  template: `
    <h2>Nuevo pedido</h2>
    <label for="cliente">Cliente</label>
    <input id="cliente" [(ngModel)]="cliente" [attr.aria-invalid]="errorCampo() ? true : null" aria-describedby="cliente-error" />
    <p id="cliente-error" role="alert">{{ errorCampo() }}</p>
    <button type="button" (click)="crear()" [disabled]="enviando()">Crear pedido</button>
    @if (general()) { <p role="alert">{{ general() }}</p> }
    @if (creado()) { <p role="status">Pedido {{ creado() }} creado</p> }
  `,
})
export class NuevoPedido {
  private readonly api = inject(PedidosApi);
  protected cliente = '';
  protected readonly errorCampo = signal('');
  protected readonly general = signal('');
  protected readonly creado = signal<number | undefined>(undefined);
  protected readonly enviando = signal(false);
  protected crear() {
    this.errorCampo.set(''); this.general.set(''); this.enviando.set(true);
    this.api.crear(this.cliente).subscribe({
      next: (r) => { this.creado.set(r.id); this.enviando.set(false); },
      error: (e: Error) => {
        this.enviando.set(false);
        if (e instanceof ErrorDeValidacion) this.errorCampo.set(e.campos['cliente'] ?? '');
        else this.general.set(mensajeParaElUsuario(e));
      },
    });
  }
}

/** Pantalla 3: detalle. 404 → "no existe"; 5xx → reintentos transitorios y mensaje genérico. */
@Component({
  selector: 'app-detalle',
  template: `
    @if (cargando()) { <p role="status">Cargando pedido…</p> }
    @if (pedido(); as p) { <h2>Pedido {{ p.id }}: {{ p.cliente }}</h2> }
    @if (mensaje()) { <p role="alert">{{ mensaje() }}</p> }
  `,
})
export class DetallePedido {
  private readonly api = inject(PedidosApi);
  protected readonly cargando = signal(true);
  protected readonly pedido = signal<Pedido | undefined>(undefined);
  protected readonly mensaje = signal('');
  constructor() {
    const id = location.pathname.split('/').pop() ?? '';
    this.api.obtener(id).subscribe({
      next: (p) => { this.pedido.set(p); this.cargando.set(false); },
      error: (e: unknown) => { this.mensaje.set(mensajeParaElUsuario(aErrorDeDominio(e))); this.cargando.set(false); },
    });
  }
}

/** Cascada frente a paralelo, con tiempos medidos en el navegador. */
@Component({
  selector: 'app-cascada',
  template: `
    <h2>Cascada de peticiones</h2>
    <button type="button" (click)="medir('cascada')">Cargar en cascada</button>
    <button type="button" (click)="medir('paralelo')">Cargar en paralelo</button>
    @for (m of mediciones(); track $index) { <p class="medicion">{{ m }}</p> }
  `,
})
export class Cascada {
  private readonly api = inject(PedidosApi);
  protected readonly mediciones = signal<string[]>([]);
  protected medir(modo: 'cascada' | 'paralelo') {
    const t0 = performance.now();
    this.api[modo]().subscribe((r) => this.mediciones.update((m) => [...m, `${modo}: ${Math.round(performance.now() - t0)} ms (${r.usuario}, ${r.pedidos} pedidos)`]));
  }
}

@Component({
  imports: [RouterLink, RouterOutlet],
  template: `<h1>Comunicación con el backend</h1><nav aria-label="Pantallas"><a routerLink="">Lista</a> · <a routerLink="nuevo">Nuevo</a> · <a routerLink="cascada">Cascada</a></nav><router-outlet />`,
})
export class Marco {}
