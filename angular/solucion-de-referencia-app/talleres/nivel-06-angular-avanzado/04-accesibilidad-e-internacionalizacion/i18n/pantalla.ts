import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, LOCALE_ID, inject, signal } from '@angular/core';

/** Punto 6: una pantalla en dos idiomas (compilación por idioma): plurales ICU, fecha y moneda con la configuración regional. */
@Component({
  selector: 'app-root',
  imports: [DatePipe, CurrencyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main>
      <h1 i18n="@@titulo">Tus pedidos</h1>
      <p><span i18n="@@idioma">Idioma de esta compilación:</span> <strong data-id="locale">{{ locale }}</strong></p>
      <button type="button" i18n="@@cero" (click)="cantidad.set(0)">Ninguno</button>
      <button type="button" i18n="@@uno" (click)="cantidad.set(1)">Uno</button>
      <button type="button" i18n="@@varios" (click)="cantidad.set(5)">Cinco</button>
      <p data-id="plural" i18n="@@plural">{cantidad(), plural, =0 {No tienes pedidos} =1 {Tienes un pedido} other {Tienes {{ cantidad() }} pedidos}}</p>
      <p><span i18n="@@fecha">Entrega:</span> <time data-id="fecha" [attr.datetime]="entrega.toISOString()">{{ entrega | date: 'fullDate' : 'UTC' }}</time></p>
      <p><span i18n="@@total">Total:</span> <output data-id="moneda">{{ total | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</output></p>
      <p data-id="mezcla" i18n="@@bienvenida">Hola, {{ nombre }}: tienes {{ cantidad() }} pedidos activos.</p>
    </main>
  `,
})
export class PantallaI18n {
  protected readonly locale = inject(LOCALE_ID);
  protected readonly cantidad = signal(0);
  protected readonly entrega = new Date(Date.UTC(2026, 11, 24, 12));
  protected readonly total = 1234567.5;
  protected readonly nombre = 'Ana';
}
