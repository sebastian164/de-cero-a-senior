import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** 4. Calificación por estrellas con ControlValueAccessor completo. */
@Component({
  selector: 'app-estrellas',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => EstrellasComponent), multi: true }],
  template: `
    <div role="radiogroup" [attr.aria-label]="etiqueta()" (focusout)="alSalir()">
      @for (n of [1, 2, 3, 4, 5]; track n) {
        <button type="button" role="radio" [attr.aria-checked]="valor() === n" [attr.aria-label]="n + (n === 1 ? ' estrella' : ' estrellas')"
                [disabled]="deshabilitado()" (click)="elegir(n)">{{ n <= valor() ? '★' : '☆' }}</button>
      }
    </div>
  `,
})
export class EstrellasComponent implements ControlValueAccessor {
  readonly etiqueta = input('Calificación');
  protected readonly valor = signal(0);
  protected readonly deshabilitado = signal(false);
  private alCambiar: (v: number) => void = () => {};
  private alTocar: () => void = () => {};

  writeValue(v: number | null): void { this.valor.set(v ?? 0); }
  registerOnChange(fn: (v: number) => void): void { this.alCambiar = fn; }
  registerOnTouched(fn: () => void): void { this.alTocar = fn; }
  setDisabledState(deshabilitado: boolean): void { this.deshabilitado.set(deshabilitado); }

  protected elegir(n: number): void {
    if (this.deshabilitado()) return;     // defensa adicional: un control deshabilitado no acepta valores
    this.valor.set(n);
    this.alCambiar(n);
  }
  protected alSalir(): void { this.alTocar(); }
}
