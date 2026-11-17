import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';

// 7. Componente "mío" con ngOnChanges y decoradores, antes de migrar. verificar.sh lo copia y le aplica
//    los esquemas de migración oficiales de Angular; el resultado queda en migracion/resultado/.
@Component({
  selector: 'app-contador-antes',
  template: `<p #parrafo>{{ etiqueta }}</p><button type="button" (click)="sumar()">+</button>`,
})
export class ContadorAntesComponent implements OnChanges {
  @Input() valor = 0;
  @Input() unidad = 'unidades';
  @Output() valorChange = new EventEmitter<number>();
  @ViewChild('parrafo') parrafo?: ElementRef<HTMLParagraphElement>;
  etiqueta = '';

  ngOnChanges(cambios: SimpleChanges): void {
    if (cambios['valor'] || cambios['unidad']) this.etiqueta = `${this.valor} ${this.unidad}`;
  }

  sumar(): void {
    this.valorChange.emit(this.valor + 1);
  }
}
