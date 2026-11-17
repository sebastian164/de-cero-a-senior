import { Component, inject, signal } from '@angular/core';
import { ValidacionDeDocumentos } from './documentos';

@Component({
  template: `
    <h1>Validación de documentos por país</h1>
    <label>País <select (change)="pais.set($any($event.target).value)">
      @for (p of servicio.paises(); track p) { <option [value]="p" [selected]="p === pais()">{{ p }}</option> }
    </select></label>
    <label>Documento <input (input)="documento.set($any($event.target).value)" /></label>
    <p role="status">{{ resultado() }}</p>
    <button type="button" (click)="comprobar()">Validar</button>
  `,
})
export class PaginaDocumentos {
  protected readonly servicio = inject(ValidacionDeDocumentos);
  protected readonly pais = signal('CO');
  protected readonly documento = signal('');
  protected readonly resultado = signal('');
  protected comprobar() { this.resultado.set(this.servicio.validar(this.pais(), this.documento()) ?? 'Documento válido'); }
}
