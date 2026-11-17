import { ScrollingModule } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

export interface Fila { id: number; nombre: string; total: number; }
export const FILAS: readonly Fila[] = Array.from({ length: 10_000 }, (_, i) => ({ id: i, nombre: `Cliente ${i}`, total: (i * 37) % 1000 }));

@Component({
  selector: 'app-marco-n6-01',
  imports: [RouterLink, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div>
      <h1>Rendimiento</h1>
      <nav aria-label="Puntos del taller">
        <a routerLink="filas-planas">10.000 filas</a> |
        <a routerLink="filas-virtuales">Desplazamiento virtual</a> |
        <a routerLink="con-defer">Con &#64;defer</a> |
        <a routerLink="sin-defer">Sin &#64;defer</a>
      </nav>
      <router-outlet />
    </div>
  `,
})
export class Marco {}

// Punto 4, antes: 10.000 nodos en el DOM.
@Component({
  selector: 'app-filas-planas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>10.000 filas, todas en el DOM</h2>
    <div class="caja" role="list" aria-label="Filas">
      @for (f of filas; track f.id) { <div role="listitem" class="fila">{{ f.nombre }}: {{ f.total }}</div> }
    </div>
  `,
  styles: `.caja { height: 300px; overflow: auto; border: 1px solid #888 } .fila { height: 28px; }`,
})
export class FilasPlanas { protected readonly filas = FILAS; }

// Punto 4, después: solo las filas visibles.
@Component({
  selector: 'app-filas-virtuales',
  imports: [ScrollingModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>10.000 filas con desplazamiento virtual</h2>
    <cdk-virtual-scroll-viewport itemSize="28" class="caja" role="list" aria-label="Filas">
      <div *cdkVirtualFor="let f of filas; trackBy: porId" role="listitem" class="fila">{{ f.nombre }}: {{ f.total }}</div>
    </cdk-virtual-scroll-viewport>
  `,
  styles: `.caja { height: 300px; border: 1px solid #888 } .fila { height: 28px; }`,
})
export class FilasVirtuales {
  protected readonly filas = FILAS;
  protected porId = (_: number, f: Fila) => f.id;
}
