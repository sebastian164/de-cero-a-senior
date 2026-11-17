import { CurrencyPipe, DatePipe, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, PLATFORM_ID, RESPONSE_INIT, afterNextRender, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AlmacenLocal } from './almacen';
import { Seo } from './seo';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ContadorRaiz { valor = 0; }

export interface Producto { id: number; nombre: string; precio: number; }

@Component({
  selector: 'app-inicio',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Tienda de prueba</h1>
    <p>Página pública prerenderizada al construir.</p>
    <ul>@for (id of ids; track id) { <li><a [routerLink]="['/producto', id]">Producto {{ id }}</a></li> }</ul>
  `,
})
export class Inicio {
  protected readonly ids = [1, 2, 3];
  constructor() { inject(Seo).fijar('Tienda de prueba', 'Catálogo público de la tienda de prueba'); }
}

@Component({
  selector: 'app-producto',
  imports: [CurrencyPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (producto(); as p) {
      <article><h1>{{ p.nombre }}</h1><p data-id="precio">{{ p.precio | currency: 'USD' }}</p></article>
      <p data-id="visto">Último producto visto: <output>{{ ultimo() ?? 'ninguno' }}</output></p>
    } @else if (noExiste()) { <h1>Producto no encontrado</h1> } @else { <p>Cargando</p> }
    <a routerLink="/">Inicio</a>
  `,
})
export class Detalle {
  private readonly http = inject(HttpClient);
  private readonly seo = inject(Seo);
  private readonly almacen = inject(AlmacenLocal);
  private readonly respuesta = inject(RESPONSE_INIT, { optional: true });
  protected readonly producto = signal<Producto | null>(null);
  protected readonly noExiste = signal(false);
  protected readonly ultimo = signal<string | null>(null);

  constructor() {
    const id = inject(ActivatedRoute).snapshot.paramMap.get('id');
    this.http.get<Producto>(`/api/productos/${id}`).subscribe({
      next: (p) => {
        this.producto.set(p);
        this.seo.fijar(`${p.nombre} | Tienda de prueba`, `Compra ${p.nombre} por ${p.precio} dólares`, {
          '@context': 'https://schema.org', '@type': 'Product', name: p.nombre, offers: { '@type': 'Offer', price: p.precio, priceCurrency: 'USD' },
        });
      },
      error: () => {
        this.noExiste.set(true);
        if (this.respuesta) this.respuesta.status = 404;                 // código de estado real, no una página 200 que dice "no encontrado"
        this.seo.fijar('Producto no encontrado', 'El producto no existe');
      },
    });
    afterNextRender(() => {                                              // solo en el navegador, tras el primer renderizado
      this.ultimo.set(this.almacen.leer('ultimo'));
      this.almacen.escribir('ultimo', String(id));
    });
  }
}

@Component({
  selector: 'app-privado',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h1>Área privada</h1><p data-id="ancho">Ancho de la ventana: {{ ancho() }}</p>`,
})
export class Privado {
  protected readonly ancho = signal(0);
  constructor() {
    inject(Seo).fijar('Área privada', 'Solo para usuarios con sesión');
    afterNextRender(() => this.ancho.set(window.innerWidth));            // en vez de leer `window` en el constructor
  }
}

/** Punto 3, versión con el error: contenido distinto en servidor y navegador. */
@Component({
  selector: 'app-descuadre',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Descuadre de hidratación</h1>
    <p data-id="fecha">Hora: {{ ahora | date: 'HH:mm:ss.SSS' }}</p>
    @if (esNavegador) { <p data-id="solo-navegador">Solo se dibuja en el navegador</p> }
    <table><tr><td>HTML inválido: sin tbody</td></tr></table>
    <p data-id="cierre">fin</p>
  `,
})
export class Descuadre {
  protected readonly ahora = new Date();
  protected readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));
}

/** Punto 3, versión corregida: lo que depende del navegador se pinta después del primer renderizado. */
@Component({
  selector: 'app-cuadre',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Hidratación correcta</h1>
    <p data-id="fecha">Hora: {{ ahora() | date: 'HH:mm:ss.SSS' }}</p>
    @if (esNavegador()) { <p data-id="solo-navegador">Solo se dibuja en el navegador</p> }
    <table><tbody><tr><td>HTML válido</td></tr></tbody></table>
    <p data-id="cierre">fin</p>
  `,
})
export class Cuadre {
  protected readonly ahora = signal(new Date(0));                       // valor estable en ambos lados
  protected readonly esNavegador = signal(false);
  constructor() { afterNextRender(() => { this.ahora.set(new Date()); this.esNavegador.set(true); }); }
}

@Component({ selector: 'app-no-encontrada', template: `<h1>Página no encontrada</h1>` })
export class NoEncontrada { constructor() { const r = inject(RESPONSE_INIT, { optional: true }); if (r) r.status = 404; } }

// Riesgo "estado en el servidor compartido entre peticiones": una variable de módulo vive tanto como el proceso;
// un servicio `providedIn: 'root'` se crea de nuevo en cada petición.
let contadorDeModulo = 0;
@Component({
  selector: 'app-visitas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h1>Visitas</h1><p data-id="del-servicio">servicio: {{ propio }}</p><p data-id="del-modulo">módulo: {{ global }}</p>`,
})
export class Visitas {
  protected propio = ++inject(ContadorRaiz).valor;
  protected global = ++contadorDeModulo;
}
