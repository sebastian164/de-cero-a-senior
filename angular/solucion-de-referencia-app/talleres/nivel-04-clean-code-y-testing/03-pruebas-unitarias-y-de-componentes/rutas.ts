import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of, Subject, switchMap } from 'rxjs';
import { Routes } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AlmacenPedidos, ListaPedidosComponent } from '../../../proyectos/ejemplos/nivel-04-clean-code-y-testing/03-pruebas-unitarias-y-de-componentes/pedidos';
import { BuscadorComponent } from './buscador.component';
import { ContactoComponent } from './contacto.component';

@Component({
  imports: [ListaPedidosComponent, BuscadorComponent, ContactoComponent],
  template: `
    <h1>Taller n4-03: componentes probados</h1>
    <app-buscador (buscar)="buscar($event)" />
    <p>Coincidencias: <strong id="coincidencias">{{ resultados().join(", ") }}</strong></p>
    <ul><app-lista-pedidos [pedidos]="almacen.visibles()" (cancelar)="almacen.cancelar($event)" /></ul>
    @if (almacen.error()) { <p role="alert">{{ almacen.error() }}</p> }
    <app-contacto />
  `,
})
class Pagina {
  protected readonly almacen = inject(AlmacenPedidos);
  private readonly http = inject(HttpClient);
  protected readonly resultados = signal<string[]>([]);
  private readonly consultas = new Subject<string>();
  constructor() {
    this.almacen.cargar();
    // switchMap cancela la búsqueda anterior (la carrera que enseña n1-02); catchError dentro mantiene vivo el flujo.
    this.consultas.pipe(
      switchMap((q) => this.http.get<string[]>('/api/buscar', { params: { q } }).pipe(catchError(() => of([] as string[])))),
      takeUntilDestroyed(),
    ).subscribe((r) => this.resultados.set(r));
  }
  protected buscar(q: string) { this.consultas.next(q); }
}
export default [{ path: '', component: Pagina }] satisfies Routes;
