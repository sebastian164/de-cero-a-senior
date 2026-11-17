import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { Producto, ProductosService } from './productos.service';

@Component({
  selector: 'app-buscador',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label>Buscar <input [value]="texto()" (input)="texto.set($any($event.target).value)" /></label>
    <p>{{ total() }} resultados</p>
    @for (producto of resultados(); track producto.id) {
      <p>{{ producto.nombre }}</p>
    }
  `,
})
export class BuscadorComponent {
  private readonly productos = inject(ProductosService);

  protected readonly texto = signal('');

  protected readonly resultados = toSignal(
    toObservable(this.texto).pipe(
      debounceTime(300),                       // espera a que deje de escribir
      distinctUntilChanged(),
      switchMap((texto) =>                     // cancela la petición anterior
        texto.length < 2 ? of([]) : this.productos.buscar(texto).pipe(catchError(() => of([])))),
    ),
    { initialValue: [] as Producto[] },
  );

  protected readonly total = computed(() => this.resultados().length);   // siempre al día
}
