import { Component, computed, effect, input, model, numberAttribute, output } from '@angular/core';
import { acotarPagina, numeroDePaginas } from './paginacion';

@Component({
  selector: 'app-paginacion',
  template: `
    @let actual = paginaValida();
    <nav aria-label="Paginación">
      <button type="button" (click)="primera()" [disabled]="actual === 1">Ir a la primera</button>
      <button type="button" (click)="pagina.set(actual - 1)" [disabled]="!hayAnterior()">Anterior</button>
      <span>{{ texto() }}</span>
      <button type="button" (click)="pagina.set(actual + 1)" [disabled]="!haySiguiente()">Siguiente</button>
    </nav>
  `,
})
export class PaginacionComponent {
  // 1. Entradas y modelo
  readonly total = input.required<number>();
  readonly tamano = input(10, { transform: numberAttribute });
  readonly pagina = model(1);
  readonly irAPrimera = output<void>();

  // 2. Todo lo derivable es computed
  readonly paginas = computed(() => numeroDePaginas(this.total(), this.tamano()));
  readonly paginaValida = computed(() => acotarPagina(this.pagina(), this.paginas()));
  readonly hayAnterior = computed(() => this.paginaValida() > 1);
  readonly haySiguiente = computed(() => this.paginaValida() < this.paginas());
  readonly texto = computed(() => `Página ${this.paginaValida()} de ${this.paginas()}`);

  constructor() {
    // 3. Corrección automática. La vista nunca muestra una página inexistente porque usa paginaValida (computed).
    //    Pero la página pertenece al padre ([(pagina)]): para que el padre también se corrija hay que ESCRIBIR el modelo.
    //    Ni computed ni linkedSignal escriben en el padre; un effect sí, y aquí sincroniza con un dueño externo.
    //    El efecto lee las DOS señales: la pedida y la válida. Si leyera solo paginaValida, un cambio 2 → 3 → 2
    //    ocurrido entre dos ejecuciones (clic en Siguiente y en seguida bajar el total) no le llegaría, porque
    //    paginaValida termina con el mismo valor, y el padre se quedaría con la página 3 inexistente.
    effect(() => {
      const pedida = this.pagina();
      const valida = this.paginaValida();
      if (pedida !== valida) this.pagina.set(valida);
    });
  }

  protected primera(): void {
    this.pagina.set(1);
    this.irAPrimera.emit();
  }
}
