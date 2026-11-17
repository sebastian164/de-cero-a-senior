import { Component, computed, input, linkedSignal, signal } from '@angular/core';
import { SelectorCantidadComponent } from './selector-cantidad.component';

// El padre del README.
@Component({
  selector: 'app-padre-cantidad',
  imports: [SelectorCantidadComponent],
  template: `
    <app-selector-cantidad [(cantidad)]="unidades" [maximo]="existencias()" nombre="Cantidad" />
    <p>Vas a comprar {{ unidades() }}</p>
    <app-selector-cantidad nombre="Con texto" maximo="5" />
  `,
})
export class PadreCantidadComponent {
  protected readonly unidades = signal(1);
  readonly existencias = signal(3);
}

// Los fragmentos de "Lo esencial": @let y linkedSignal.
@Component({
  selector: 'app-fragmentos',
  template: `
    @let total = subtotal() + envio();
    <p>Total: {{ total }}</p>
    @if (total > limite()) { <p role="status">Supera tu cupo.</p> }
    <p>Elegida: {{ elegida() }}</p>
  `,
})
export class FragmentosComponent {
  readonly subtotal = input(0);
  readonly envio = input(0);
  readonly limite = input(100);
  readonly opciones = input.required<readonly string[]>();
  protected readonly elegida = linkedSignal(() => this.opciones()[0]);   // editable; vuelve a la primera si cambian las opciones
  elegir(o: string) { this.elegida.set(o); }
  readonly leida = computed(() => this.elegida());
}
