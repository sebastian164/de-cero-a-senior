import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';

// "Mi componente" antes del taller: hace todo. Se compila y se prueba para tener la línea base,
// pero no se monta en la aplicación. Actores que piden cambios: ver ANALISIS.md.
@Component({
  selector: 'app-pantalla-factura-antes',
  // Código "antiguo": muta campos sueltos. Así lo dejaría la migración a Angular 22 (estrategia Eager).
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (cargando) {
      <p>Cargando...</p>
    }
    @if (error) {
      <p>{{ error }}</p>
    }
    @if (factura) {
      <h2>Factura {{ factura.id }} - {{ factura.cliente.nombre_completo }}</h2>
      <p>Fecha: {{ formatearFecha(factura.fecha_emision) }}</p>
      <table>
        @for (l of factura.lineas; track $index) {
          <tr>
            <td>{{ l.descripcion }}</td>
            <td>{{ l.cantidad }}</td>
            <td>{{ formatearDinero(l.valor_unitario * l.cantidad) }}</td>
          </tr>
        }
      </table>
      <p>Subtotal: {{ formatearDinero(calcularSubtotal()) }}</p>
      <p>Descuento: {{ formatearDinero(calcularDescuento()) }}</p>
      <p>IVA: {{ formatearDinero(calcularIva()) }}</p>
      <p><strong>Total: {{ formatearDinero(calcularTotal()) }}</strong></p>
      <label>Correo <input [value]="correo" (input)="correo = $any($event.target).value" /></label>
      <button type="button" (click)="enviarPorCorreo()" [disabled]="!puedeEnviar()">Enviar</button>
      <button type="button" (click)="exportarCsv()">Exportar CSV</button>
      <button type="button" (click)="imprimir()">Imprimir</button>
      @if (mensaje) {
        <p>{{ mensaje }}</p>
      }
      <label>Cupón <input [value]="cupon" (input)="cupon = $any($event.target).value" /></label>
      <button type="button" (click)="aplicarCupon()">Aplicar cupón</button>
      @if (descuentoCupon > 0) {
        <p>Cupón aplicado: {{ formatearDinero(descuentoCupon) }}</p>
      }
      <h3>Envíos anteriores</h3>
      <ul>
        @for (e of historial; track $index) {
          <li>{{ formatearFecha(e.fecha) }} a {{ e.para }}</li>
        } @empty {
          <li>Sin envíos</li>
        }
      </ul>
    }
  `,
})
export class PantallaFacturaAntesComponent implements OnInit {
  private http = inject(HttpClient);
  id = 'F-2';
  factura: any;
  cargando = false;
  error = '';
  correo = '';
  mensaje = '';
  ultimoCsv = '';
  cupon = '';
  descuentoCupon = 0;
  historial: { fecha: string; para: string }[] = [];

  ngOnInit() {
    this.cargando = true;
    const guardada = localStorage.getItem('factura-' + this.id);
    if (guardada) {
      this.factura = JSON.parse(guardada);
      this.cargando = false;
    }
    this.http.get('/api/facturas/' + this.id).subscribe({
      next: (dto: any) => {
        this.factura = dto;
        this.cargando = false;
        localStorage.setItem('factura-' + this.id, JSON.stringify(dto));
      },
      error: (e) => {
        this.cargando = false;
        if (e.status === 404) {
          this.error = 'La factura no existe';
        } else {
          this.error = 'Error ' + e.status;
        }
      },
    });
  }

  calcularSubtotal(): number {
    let s = 0;
    for (const l of this.factura.lineas) {
      s = s + l.valor_unitario * l.cantidad;
    }
    return s;
  }

  calcularDescuento(): number {
    // Regla comercial: 5 % si el subtotal supera un millón; 10 % si además el cliente es mayorista.
    const s = this.calcularSubtotal();
    if (s > 1000000) {
      if (this.factura.cliente.tipo === 'mayorista') {
        return s * 0.1;
      }
      return s * 0.05;
    }
    return 0;
  }

  calcularIva(): number {
    // Regla tributaria: 19 % sobre la base después del descuento.
    return Math.round((this.calcularSubtotal() - this.calcularDescuento()) * 0.19);
  }

  calcularTotal(): number {
    return this.calcularSubtotal() - this.calcularDescuento() + this.calcularIva();
  }

  formatearFecha(iso: string): string {
    const f = new Date(iso);
    const dia = String(f.getUTCDate()).padStart(2, '0');
    const mes = String(f.getUTCMonth() + 1).padStart(2, '0');
    return dia + '/' + mes + '/' + f.getUTCFullYear();
  }

  formatearDinero(v: number): string {
    return '$ ' + Math.round(v).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  }

  puedeEnviar(): boolean {
    return this.correo.includes('@') && this.correo.includes('.') && this.factura && this.factura.lineas.length > 0;
  }

  enviarPorCorreo() {
    if (!this.puedeEnviar()) {
      return;
    }
    this.mensaje = 'Enviando...';
    this.http
      .post('/api/correos', {
        para: this.correo,
        asunto: 'Factura ' + this.factura.id,
        cuerpo: 'Total: ' + this.formatearDinero(this.calcularTotal()),
      })
      .subscribe({
        next: () => {
          this.mensaje = 'Enviada a ' + this.correo;
          this.historial = [...this.historial, { fecha: new Date().toISOString(), para: this.correo }];
          localStorage.setItem('envios-' + this.id, JSON.stringify(this.historial));
        },
        error: () => (this.mensaje = 'No se pudo enviar'),
      });
  }

  exportarCsv() {
    let csv = 'descripcion;cantidad;valor\n';
    for (const l of this.factura.lineas) {
      csv = csv + l.descripcion.replace(/;/g, ',') + ';' + l.cantidad + ';' + l.valor_unitario * l.cantidad + '\n';
    }
    csv = csv + 'total;;' + this.calcularTotal() + '\n';
    this.ultimoCsv = csv;
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'factura-' + this.factura.id + '.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  aplicarCupon() {
    // Regla comercial: BIENVENIDA da 20.000 si el subtotal pasa de 100.000; NAVIDAD da 15 % en diciembre.
    const codigo = this.cupon.trim().toUpperCase();
    const s = this.calcularSubtotal();
    if (codigo === 'BIENVENIDA') {
      if (s > 100000) {
        this.descuentoCupon = 20000;
        this.mensaje = 'Cupón aplicado';
      } else {
        this.descuentoCupon = 0;
        this.mensaje = 'El cupón exige una compra mayor a $ 100.000';
      }
    } else if (codigo === 'NAVIDAD') {
      if (new Date().getMonth() === 11) {
        this.descuentoCupon = Math.round(s * 0.15);
        this.mensaje = 'Cupón aplicado';
      } else {
        this.descuentoCupon = 0;
        this.mensaje = 'El cupón NAVIDAD solo vale en diciembre';
      }
    } else {
      this.descuentoCupon = 0;
      this.mensaje = 'Cupón inválido';
    }
  }

  imprimir() {
    // Antes de imprimir, la factura debe tener al menos una línea y el título de la ventana cambia
    // para que el PDF que genera el navegador tenga un nombre útil.
    if (!this.factura || this.factura.lineas.length === 0) {
      this.mensaje = 'No hay nada que imprimir';
      return;
    }
    const tituloAnterior = document.title;
    document.title = 'Factura ' + this.factura.id;
    window.print();
    document.title = tituloAnterior;
  }
}
