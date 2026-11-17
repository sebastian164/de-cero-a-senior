import { afterNextRender, Component, computed, ElementRef, Injector, inject, signal, viewChildren } from '@angular/core';
import { PaginacionComponent } from './paginacion.component';

// Usa la paginación en una lista.
@Component({
  selector: 'app-lista-paginada',
  imports: [PaginacionComponent],
  template: `
    <h1>Lista paginada</h1>
    <label>Total de elementos <input type="number" [value]="total()" (change)="total.set($any($event.target).valueAsNumber)" /></label>
    <ul>
      @for (e of visibles(); track e) {
        <li #elemento tabindex="-1">Elemento {{ e }}</li>
      }
    </ul>
    <app-paginacion [total]="total()" tamano="5" [(pagina)]="pagina" (irAPrimera)="enfocarPrimero()" />
    <p>Página en el padre: {{ pagina() }}</p>
  `,
})
export class ListaPaginadaComponent {
  private readonly injector = inject(Injector);
  protected readonly total = signal(32);
  protected readonly pagina = signal(1);
  protected readonly visibles = computed(() => {
    // 5. Aquí NO se usa @let: el cálculo tiene lógica (rangos) y va en la clase, donde se prueba.
    const desde = (this.pagina() - 1) * 5;
    return Array.from({ length: Math.max(0, Math.min(5, this.total() - desde)) }, (_, i) => desde + i + 1);
  });

  // 4. Consulta con señales a los elementos de la propia plantilla.
  private readonly elementos = viewChildren<ElementRef<HTMLElement>>('elemento');

  protected enfocarPrimero(): void {
    afterNextRender(() => this.elementos()[0]?.nativeElement.focus(), { injector: this.injector });
  }
}
