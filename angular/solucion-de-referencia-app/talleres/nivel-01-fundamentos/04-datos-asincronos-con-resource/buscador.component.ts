import { httpResource } from '@angular/common/http';
import { Component, debounced, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { aPedidos, urlDeBusqueda } from './pedidos';

/** 3 y 4. Buscador con recurso: nada se pide con menos de dos letras; la espera, de dos maneras. */
@Component({
  selector: 'app-buscador-recurso',
  template: `
    <h2>Buscar pedidos</h2>
    <label>Cliente <input [value]="texto()" (input)="texto.set($any($event.target).value)" /></label>
    <p>Con RxJS: {{ conRxjs.hasValue() ? conRxjs.value().length + ' resultados' : 'sin búsqueda' }}</p>
    <p>Sin RxJS: {{ sinRxjs.hasValue() ? sinRxjs.value().length + ' resultados' : 'sin búsqueda' }}</p>
  `,
})
export class BuscadorRecursoComponent {
  readonly texto = signal('');

  // Con RxJS: debounceTime sobre el observable de la señal, y de vuelta a señal.
  private readonly textoConEspera = toSignal(toObservable(this.texto).pipe(debounceTime(300), distinctUntilChanged()), { initialValue: '' });
  readonly conRxjs = httpResource(() => urlDeBusqueda(this.textoConEspera()), { parse: aPedidos });

  // Sin RxJS: debounced() de @angular/core (experimental en la 22) devuelve un recurso con el valor retrasado.
  private readonly textoRetrasado = debounced(this.texto, 300);
  readonly sinRxjs = httpResource(
    () => urlDeBusqueda(this.textoRetrasado.hasValue() ? this.textoRetrasado.value() : ''),
    { parse: aPedidos },
  );
}
