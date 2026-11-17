import {
  Component,
  ElementRef,
  OnChanges,
  SimpleChanges,
  input,
  output,
  viewChild,
} from '@angular/core';

// 7. Componente "mío" con ngOnChanges y decoradores, antes de migrar. verificar.sh lo copia y le aplica
//    los esquemas de migración oficiales de Angular; el resultado queda en migracion/resultado/.
@Component({
  selector: 'app-contador-migrado',
  template: `<p #parrafo>{{ etiqueta }}</p>
    <button type="button" (click)="sumar()">+</button>`,
})
export class ContadorMigradoComponent implements OnChanges {
  readonly valor = input(0);
  readonly unidad = input('unidades');
  readonly valorChange = output<number>();
  readonly parrafo = viewChild<ElementRef<HTMLParagraphElement>>('parrafo');
  etiqueta = '';

  ngOnChanges(cambios: SimpleChanges): void {
    if (cambios['valor'] || cambios['unidad'])
      this.etiqueta = `${this.valor()} ${this.unidad()}`;
  }

  sumar(): void {
    this.valorChange.emit(this.valor() + 1);
  }
}
