import { Component, computed, inject, signal } from '@angular/core';
import { Catalogo, Inscripcion, resumen } from './estado';
import { FiltroCategorias, ResumenSeleccion, TarjetaCurso } from './ui';

/**
 * Contenedor: el único que inyecta servicios.
 * 4. Antes había un taladro de propiedades: Contenedor → ListaCursos → TarjetaCurso → BotonInscribir pasaban
 *    `puedeInscribirse` y `(alternar)` por tres niveles. Ahora el contenedor proyecta el botón dentro de la tarjeta.
 */
@Component({
  selector: 'app-pagina-cursos',
  imports: [FiltroCategorias, TarjetaCurso, ResumenSeleccion],
  template: `
    <h1>Cursos</h1>
    <app-filtro-categorias [categorias]="categorias()" [(elegida)]="categoria" />
    @for (c of visibles(); track c.id) {
      <app-tarjeta-curso [curso]="c">
        <button acciones type="button" [disabled]="!inscripcion.puedeInscribirse()" (click)="inscripcion.alternar(c.id)"
                [attr.aria-pressed]="inscripcion.elegidos().includes(c.id)">Elegir {{ c.nombre }}</button>
      </app-tarjeta-curso>
    }
    <app-resumen-seleccion [cantidad]="cifras().cantidad" [total]="cifras().total" (confirmar)="inscripcion.confirmar()" />
    @if (inscripcion.confirmada()) { <p role="status">Inscripción confirmada</p> }
  `,
})
export class PaginaCursos {
  private readonly catalogo = inject(Catalogo);
  protected readonly inscripcion = inject(Inscripcion);
  protected readonly categoria = signal('Frontend');
  protected readonly categorias = computed(() => [...new Set(this.catalogo.cursos().map((c) => c.categoria))]);
  protected readonly visibles = computed(() => this.catalogo.cursos().filter((c) => c.categoria === this.categoria()));
  protected readonly cifras = computed(() => resumen(this.catalogo.cursos(), this.inscripcion.elegidos()));
}
