import { CurrencyPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { AsistenteSolicitud } from '../aplicacion/asistente';
import { cuotaMensual, TipoEmpleo } from '../dominio/solicitud';

@Component({
  imports: [CurrencyPipe],
  template: `
    <h1>Solicitud de crédito</h1>
    @if (a.enviada(); as id) { <p role="status">Solicitud enviada ({{ id }})</p> } @else {
    <ol aria-label="Pasos">@for (p of a.pasos(); track p) { <li [attr.aria-current]="p === a.paso() ? 'step' : null">{{ p }}</li> }</ol>
    @switch (a.paso()) {
      @case ('personales') {
        <label>Nombre completo <input [value]="a.solicitud().nombre" #n (input)="a.actualizar({ nombre: n.value })" /></label>
      }
      @case ('ingresos') {
        <label>Tipo de empleo
          <select #t (change)="a.actualizar({ tipoEmpleo: asTipo(t.value) })"><option value="empleado">Empleado</option><option value="independiente">Independiente</option></select></label>
        <label>Ingreso mensual <input type="number" [value]="a.solicitud().ingresoMensual" #i (input)="a.actualizar({ ingresoMensual: +i.value })" /></label>
        <label>Monto <input type="number" [value]="a.solicitud().monto" #m (input)="a.actualizar({ monto: +m.value })" /></label>
        <label>Plazo (meses) <input type="number" [value]="a.solicitud().plazoMeses" #p (input)="a.actualizar({ plazoMeses: +p.value })" /></label>
        <label><input type="checkbox" #c (change)="a.actualizar({ conCodeudor: c.checked })" /> Quiero agregar un codeudor</label>
        @if (a.solicitud().monto > 0) { <p>Cuota estimada: {{ cuota() | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</p> }
      }
      @case ('codeudor') {
        <label>Nombre del codeudor <input #cn (input)="a.actualizar({ codeudor: { ...a.solicitud().codeudor, nombre: cn.value } })" /></label>
        <label>Documento del codeudor <input #cd (input)="a.actualizar({ codeudor: { ...a.solicitud().codeudor, documento: cd.value } })" /></label>
      }
      @case ('revision') { <p>Revisa: {{ a.solicitud().nombre }} pide {{ a.solicitud().monto }} a {{ a.solicitud().plazoMeses }} meses.</p> }
    }
    @if (a.mostrarErrores()) { <ul role="alert">@for (e of errores(); track e) { <li>{{ e }}</li> }</ul> }
    <button type="button" (click)="a.retroceder()" [disabled]="a.paso() === 'personales'">Atrás</button>
    @if (a.enviando()) { <p role="status">Enviando…</p> }
    @if (a.errorGeneral(); as msg) { <p role="alert" data-testid="error-general">{{ msg }}</p> }
    @if (a.esUltimo()) { <button type="button" (click)="a.enviar()" [attr.aria-busy]="a.enviando()">Enviar solicitud</button> } @else { <button type="button" (click)="a.avanzar()">Siguiente</button> }
    }
  `,
})
export class AsistentePagina {
  protected readonly a = inject(AsistenteSolicitud);
  protected asTipo(v: string): TipoEmpleo { return v === 'independiente' ? 'independiente' : 'empleado'; }
  protected errores() { return Object.values(this.a.errores()); }
  protected cuota() { const s = this.a.solicitud(); return cuotaMensual(s.monto, s.plazoMeses); }
}
