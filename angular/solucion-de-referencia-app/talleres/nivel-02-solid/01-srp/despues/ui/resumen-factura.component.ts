import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { descuentoComercial, Factura, iva, subtotal, total, valorLinea } from '../dominio/factura';

// Presentación: cambia cuando Diseño cambia cómo se ve una factura.
@Component({
  selector: 'app-resumen-factura',
  imports: [CurrencyPipe, DatePipe],
  template: `
    <h2>Factura {{ factura().id }} — {{ factura().cliente }}</h2>
    <p>Fecha: {{ factura().emitidaEn | date: 'dd/MM/yyyy' : 'UTC' }}</p>
    <table>
      @for (l of filas(); track l.descripcion) {
        <tr><td>{{ l.descripcion }}</td><td>{{ l.cantidad }}</td><td>{{ l.valor | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</td></tr>
      }
    </table>
    <p>Subtotal: {{ cifras().subtotal | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
    <p>Descuento: {{ cifras().descuento | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
    <p>IVA: {{ cifras().iva | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
    <p><strong>Total: {{ cifras().total | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</strong></p>
  `,
})
export class ResumenFacturaComponent {
  readonly factura = input.required<Factura>();
  protected readonly filas = computed(() => this.factura().lineas.map((l) => ({ ...l, valor: valorLinea(l) })));
  protected readonly cifras = computed(() => {
    const f = this.factura();
    return { subtotal: subtotal(f), descuento: descuentoComercial(f), iva: iva(f), total: total(f) };
  });
}
