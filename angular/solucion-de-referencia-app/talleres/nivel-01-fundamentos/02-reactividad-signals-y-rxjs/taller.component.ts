import { HttpClient } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Subject, switchMap, tap } from 'rxjs';
import { CarritoStore } from './carrito.store';
import { buscador, guardarSinDobleEnvio, OPERADORES } from './flujos';

interface Producto { readonly id: number; readonly nombre: string; readonly precio: number; }

@Component({
  selector: 'app-taller-reactividad',
  imports: [CurrencyPipe],
  template: `
    <h1>Carrito y buscador</h1>
    <section aria-labelledby="t-busqueda">
      <h2 id="t-busqueda">Buscador</h2>
      <fieldset>
        <legend>Operador</legend>
        @for (op of operadores; track op) {
          <label><input type="radio" name="op" [checked]="operador() === op" (change)="operador.set(op)" /> {{ op }}</label>
        }
      </fieldset>
      <label>Buscar <input [value]="texto()" (input)="texto.set($any($event.target).value)" /></label>
      <button type="button" (click)="simularCarrera()">Simular carrera</button>
      <p data-testid="consulta">Resultados para: {{ consulta() }}</p>
      <ul>
        @for (p of resultados(); track p.id) {
          <li>{{ p.nombre }} <button type="button" (click)="carrito.agregar({ productoId: p.id, nombre: p.nombre, precio: p.precio })">Agregar {{ p.nombre }}</button></li>
        }
      </ul>
    </section>
    <section aria-labelledby="t-carrito">
      <h2 id="t-carrito">Carrito ({{ carrito.cantidadDeItems() }})</h2>
      <p>Subtotal {{ carrito.subtotal() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} ·
         IVA {{ carrito.iva() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }} ·
         Total {{ carrito.total() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
      <button type="button" (click)="clics.next()">Guardar</button>
      <p role="status">Guardados: {{ guardados() }} · Peticiones de guardado: {{ peticiones() }}</p>
    </section>
  `,
})
export class TallerReactividadComponent {
  private readonly http = inject(HttpClient);
  protected readonly carrito = inject(CarritoStore);
  protected readonly operadores = Object.keys(OPERADORES) as (keyof typeof OPERADORES)[];
  protected readonly operador = signal<keyof typeof OPERADORES>('switchMap');
  protected readonly texto = signal('');
  protected readonly resultados = signal<Producto[]>([]);
  protected readonly consulta = signal('');
  protected readonly guardados = signal(0);
  protected readonly peticiones = signal(0);
  protected readonly clics = new Subject<void>();

  constructor() {
    const destruir = inject(DestroyRef);
    // El operador se puede cambiar en caliente: cada cambio rearma el buscador.
    const textos = toObservable(this.texto);                      // en el contexto de inyección, no dentro del flujo
    toObservable(computed(() => OPERADORES[this.operador()])).pipe(
      switchMap((op) => buscador((q) => this.buscar(q), op)(textos)),
      takeUntilDestroyed(destruir),
    ).subscribe((r) => this.resultados.set(r));

    this.clics.pipe(
      guardarSinDobleEnvio(() => { this.peticiones.update((n) => n + 1); return this.http.post('/api/carrito', this.carrito.lineas()); }),
      takeUntilDestroyed(destruir),
    ).subscribe(() => this.guardados.update((n) => n + 1));
  }

  private buscar(q: string) {
    return this.http.get<Producto[]>('/api/productos', { params: { q } }).pipe(tap(() => this.consulta.set(q)));
  }

  /** Escribe 'te' (respuesta lenta) y 350 ms después 'tecl' (respuesta rápida). */
  protected simularCarrera(): void {
    this.texto.set('te');
    setTimeout(() => this.texto.set('tecl'), 350);
  }
}
