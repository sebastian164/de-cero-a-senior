import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

// README, "Bien": plural con formato ICU, en vez de concatenar fragmentos.
@Component({
  selector: 'app-pedidos-i18n',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" (click)="cantidad.set(0)">Cero</button>
    <button type="button" (click)="cantidad.set(1)">Uno</button>
    <button type="button" (click)="cantidad.set(5)">Cinco</button>
    <p i18n data-id="plural">{cantidad(), plural, =0 {No tienes pedidos} =1 {Tienes un pedido} other {Tienes {{ cantidad() }} pedidos}}</p>
  `,
})
export class PedidosI18n { protected readonly cantidad = signal(0); }
