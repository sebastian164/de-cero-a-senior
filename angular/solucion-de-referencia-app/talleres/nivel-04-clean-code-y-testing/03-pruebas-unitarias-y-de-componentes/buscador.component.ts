import { Component, output } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { signal } from '@angular/core';
import { debounceTime, distinctUntilChanged } from 'rxjs';

/** Emite `buscar` 300 ms después de la última tecla, y solo si el texto cambió. */
@Component({
  selector: 'app-buscador',
  template: `<label>Buscar cliente <input type="search" [value]="texto()" #entrada (input)="texto.set(entrada.value)" /></label>`,
})
export class BuscadorComponent {
  readonly buscar = output<string>();
  protected readonly texto = signal('');
  constructor() {
    toObservable(this.texto).pipe(debounceTime(300), distinctUntilChanged()).subscribe((t) => this.buscar.emit(t));
  }
}
