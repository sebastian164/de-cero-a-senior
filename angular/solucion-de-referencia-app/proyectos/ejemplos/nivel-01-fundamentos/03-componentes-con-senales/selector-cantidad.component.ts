import { afterNextRender, Component, computed, ElementRef, input, model, numberAttribute, viewChild } from '@angular/core';

@Component({
  selector: 'app-selector-cantidad',
  template: `
    @let enElLimite = cantidad() >= maximo();
    <button type="button" (click)="fijar(cantidad() - 1)" [disabled]="cantidad() <= 1">−</button>
    <input #campo type="number" [attr.aria-label]="nombre()"
           [value]="cantidad()" (change)="fijar($any($event.target).valueAsNumber)" />
    <button type="button" (click)="fijar(cantidad() + 1)" [disabled]="enElLimite">+</button>
    <p>{{ etiqueta() }}</p>
    @if (enElLimite) { <p role="status">Llegaste al máximo.</p> }
  `,
})
export class SelectorCantidadComponent {
  readonly cantidad = model(1);                                          // se usa con [(cantidad)]
  readonly maximo = input(10, { transform: numberAttribute });           // "5" llega como 5
  readonly nombre = input.required<string>();                            // sin él no compila

  private readonly campo = viewChild.required<ElementRef<HTMLInputElement>>('campo');
  protected readonly etiqueta = computed(() => `${this.cantidad()} de ${this.maximo()}`);

  constructor() {
    afterNextRender(() => this.campo().nativeElement.focus());           // cuando el elemento ya está en pantalla
  }

  protected fijar(valor: number): void {
    this.cantidad.set(Math.min(this.maximo(), Math.max(1, valor)));      // un solo lugar decide el valor
  }
}
