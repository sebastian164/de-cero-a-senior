import { Component, computed, ElementRef, input, output, viewChild } from '@angular/core';

// 7. Revisión a mano después de los esquemas: el ngOnChanges que recalculaba la etiqueta se vuelve un computed.
@Component({
  selector: 'app-contador-a-mano',
  template: `<p #parrafo>{{ etiqueta() }}</p><button type="button" (click)="sumar()">+</button>`,
})
export class ContadorAManoComponent {
  readonly valor = input(0);
  readonly unidad = input('unidades');
  readonly valorChange = output<number>();
  readonly parrafo = viewChild<ElementRef<HTMLParagraphElement>>('parrafo');
  protected readonly etiqueta = computed(() => `${this.valor()} ${this.unidad()}`);

  sumar(): void {
    this.valorChange.emit(this.valor() + 1);
  }
}
