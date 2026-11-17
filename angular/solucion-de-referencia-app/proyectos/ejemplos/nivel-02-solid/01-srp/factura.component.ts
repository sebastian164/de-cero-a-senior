import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { FacturasApi } from './datos/facturas.api';
import { total } from './dominio/factura';
import { ExportadorCsv } from './exportacion/exportador-csv';

// factura.component.ts — coordina y presenta
@Component({
  selector: 'app-factura', changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe],
  template: `
    @if (factura(); as f) {
      <h2>Factura {{ f.id }} — {{ f.cliente }}</h2>
      <p>Emitida el {{ f.fecha | date: 'dd/MM/yyyy' : 'UTC' }}</p>
      <p>Total: {{ total() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p>
      <button type="button" (click)="descargar()">Exportar CSV</button>
    }
  `,
})
export class FacturaComponent {
  readonly id = input.required<string>();
  private readonly api = inject(FacturasApi);
  private readonly exportador = inject(ExportadorCsv);

  protected readonly factura = toSignal(toObservable(this.id).pipe(switchMap((id) => this.api.obtener(id))));
  protected readonly total = computed(() => { const f = this.factura(); return f ? total(f) : 0; });

  protected descargar(): void {
    const f = this.factura();
    if (f) URL.revokeObjectURL(URL.createObjectURL(this.exportador.exportar(f)));
  }
}
