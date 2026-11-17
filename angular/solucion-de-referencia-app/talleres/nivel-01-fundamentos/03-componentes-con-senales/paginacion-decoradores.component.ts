import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, ViewChild } from '@angular/core';
import { acotarPagina, numeroDePaginas } from './paginacion';

// 6. La misma interfaz pública con decoradores, para comparar (ver ANALISIS.md).
@Component({
  selector: 'app-paginacion-decoradores',
  template: `
    <nav aria-label="Paginación" #nav>
      <button type="button" (click)="primera()" [disabled]="pagina === 1">Ir a la primera</button>
      <button type="button" (click)="cambiar(pagina - 1)" [disabled]="pagina <= 1">Anterior</button>
      <span>{{ texto }}</span>
      <button type="button" (click)="cambiar(pagina + 1)" [disabled]="pagina >= paginas">Siguiente</button>
    </nav>
  `,
})
export class PaginacionDecoradoresComponent implements OnChanges {
  @Input({ required: true }) total!: number;          // el "!" promete algo que el compilador no comprueba dentro de la clase
  @Input() tamano: number | string = 10;               // sin transform: "5" llega como texto si nadie lo convierte
  @Input() pagina = 1;
  @Output() paginaChange = new EventEmitter<number>();
  @Output() irAPrimera = new EventEmitter<void>();
  @ViewChild('nav') nav?: ElementRef<HTMLElement>;     // undefined hasta ngAfterViewInit

  paginas = 1;
  texto = '';

  ngOnChanges(): void {                                // estado derivado mantenido a mano
    this.paginas = numeroDePaginas(this.total, Number(this.tamano));
    const valida = acotarPagina(this.pagina, this.paginas);
    if (valida !== this.pagina) this.cambiar(valida);
    this.texto = `Página ${this.pagina} de ${this.paginas}`;
  }

  cambiar(p: number): void {
    this.pagina = p;                                   // modifica una entrada
    this.paginaChange.emit(p);                         // y hay que acordarse de avisar
    this.texto = `Página ${this.pagina} de ${this.paginas}`;   // y de recalcular: si se olvida, el texto miente
  }

  primera(): void { this.cambiar(1); this.irAPrimera.emit(); }
}
