import { HttpClient } from '@angular/common/http';
import { Component, inject, input, numberAttribute } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { combineLatest, map, switchMap } from 'rxjs';

type Estado = 'todos' | 'pendiente' | 'pagado' | 'enviado';
type Pestana = 'lista' | 'resumen';

/** Filtro, página y pestaña viven en la URL (los tres antes estaban en un servicio). */
@Component({
  selector: 'app-pedidos-url',
  imports: [RouterLink],
  template: `
    <h2>Pedidos (estado en la URL)</h2>
    <nav aria-label="Pestañas">
      <a [routerLink]="[]" [queryParams]="{ pestana: 'lista' }" queryParamsHandling="merge">Lista</a>
      <a [routerLink]="[]" [queryParams]="{ pestana: 'resumen' }" queryParamsHandling="merge">Resumen</a>
    </nav>
    <label>Estado
      <select [value]="estado()" (change)="ir({ estado: $any($event.target).value, pagina: 1 })">
        @for (e of estados; track e) { <option [value]="e">{{ e }}</option> }
      </select>
    </label>
    @if (pestana() === 'lista') {
      <ul>@for (p of items(); track p.id) { <li>{{ p.id }} ({{ p.estado }})</li> }</ul>
      <button type="button" (click)="ir({ pagina: pagina() - 1 })" [disabled]="pagina() <= 1">Anterior</button>
      <span>Página {{ pagina() }}</span>
      <button type="button" (click)="ir({ pagina: pagina() + 1 })">Siguiente</button>
    } @else { <p>Resumen: {{ items().length }} pedidos en esta página</p> }
  `,
})
export class PedidosUrlPagina {
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  protected readonly estados: Estado[] = ['todos', 'pendiente', 'pagado', 'enviado'];

  readonly estado = input<Estado, Estado | undefined>('todos', { transform: (v) => v ?? 'todos' });
  readonly pagina = input(1, { transform: (v: unknown) => numberAttribute(v, 1) });
  readonly pestana = input<Pestana, Pestana | undefined>('lista', { transform: (v) => v ?? 'lista' });

  protected readonly items = toSignal(
    combineLatest([toObservable(this.estado), toObservable(this.pagina)]).pipe(
      switchMap(([estado, pagina]) => this.http.get<{ items: { id: string; estado: string }[] }>('/api/pedidos', { params: { estado, pagina } })),
      map((r) => r.items)),
    { initialValue: [] as { id: string; estado: string }[] },
  );

  protected ir(params: Record<string, string | number>) { this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge' }); }
}
