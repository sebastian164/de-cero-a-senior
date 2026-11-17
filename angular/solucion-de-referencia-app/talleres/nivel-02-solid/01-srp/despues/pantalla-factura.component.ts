import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { FacturasApi } from './datos/facturas.api';
import { aplicarCupon } from './dominio/cupones';
import { Factura, sePuedeEnviar, subtotal, total } from './dominio/factura';
import { Descargador, facturaACsv } from './exportacion/csv';
import { ResumenFacturaComponent } from './ui/resumen-factura.component';

type Carga = { estado: 'cargando' } | { estado: 'error'; mensaje: string } | { estado: 'lista'; factura: Factura };

// Contenedor: coordina la pantalla. Los cálculos viven en dominio/, el HTTP en datos/, el archivo en exportacion/.
@Component({
  selector: 'app-pantalla-factura',
  imports: [ResumenFacturaComponent, CurrencyPipe],
  template: `
    @switch (carga().estado) {
      @case ('cargando') { <p>Cargando…</p> }
      @case ('error') { <p role="alert">{{ $any(carga()).mensaje }}</p> }
      @case ('lista') {
        @let f = $any(carga()).factura;
        <app-resumen-factura [factura]="f" />
        <label>Correo <input [value]="correo()" (input)="correo.set($any($event.target).value)" /></label>
        <button type="button" (click)="enviar(f)" [disabled]="!puedeEnviar()">Enviar</button>
        <button type="button" (click)="exportar(f)">Exportar CSV</button>
        <label>Cupón <input [value]="cupon()" (input)="cupon.set($any($event.target).value)" /></label>
        @if (resultadoCupon(); as r) {
          <p>{{ r.valido ? 'Cupón aplicado: ' + (r.descuento | currency: 'COP' : 'symbol-narrow' : '1.0-0') : r.motivo }}</p>
        }
        @if (mensaje()) { <p role="status">{{ mensaje() }}</p> }
      }
    }
  `,
})
export class PantallaFacturaComponent {
  private readonly api = inject(FacturasApi);
  private readonly descargador = inject(Descargador);
  // Con withComponentInputBinding(), una entrada que la ruta no trae se pone en undefined (el valor por defecto
  // se pierde). Por eso el id llega por `data` de la ruta y la fecha no es una entrada.
  readonly id = input.required<string>();
  private readonly hoy = new Date();

  protected readonly correo = signal('');
  protected readonly cupon = signal('');
  protected readonly mensaje = signal('');

  protected readonly carga = toSignal(toObservable(this.id).pipe(switchMap((id) => this.api.obtener(id).pipe(
    map((factura): Carga => ({ estado: 'lista', factura })),
    startWith<Carga>({ estado: 'cargando' }),
    catchError((e) => of<Carga>({ estado: 'error', mensaje: e?.status === 404 ? 'La factura no existe' : 'No se pudo cargar la factura' })),
  ))), { initialValue: { estado: 'cargando' } as Carga });

  private readonly factura = computed(() => { const c = this.carga(); return c.estado === 'lista' ? c.factura : undefined; });
  protected readonly puedeEnviar = computed(() => { const f = this.factura(); return !!f && sePuedeEnviar(f, this.correo()); });
  protected readonly resultadoCupon = computed(() => {
    const f = this.factura();
    return f && this.cupon() ? aplicarCupon(this.cupon(), subtotal(f), this.hoy) : undefined;
  });

  protected enviar(f: Factura): void {
    this.mensaje.set('Enviando…');
    this.api.enviarPorCorreo(this.correo(), `Factura ${f.id}`, `Total: ${total(f)}`).subscribe({
      next: () => this.mensaje.set(`Enviada a ${this.correo()}`),
      error: () => this.mensaje.set('No se pudo enviar'),
    });
  }

  protected exportar(f: Factura): void {
    this.descargador.descargar(`factura-${f.id}.csv`, facturaACsv(f), 'text/csv');
  }
}
