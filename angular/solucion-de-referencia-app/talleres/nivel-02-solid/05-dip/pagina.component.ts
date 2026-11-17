import { Component, inject, signal } from '@angular/core';
import { Avisador, AvisadorEnPagina, RegistroDeAvisos, RegistroEnMemoria, Recordatorios } from './recordatorios';

@Component({
  template: `
    <h1>DIP: recordatorios</h1>
    <p>Registro de avisos activo en esta ruta: <strong>{{ tipoRegistro }}</strong></p>
    <button type="button" (click)="programar()">Programar cita de mañana</button>
    <p role="status">{{ estado() }}</p>
    <ul>@for (a of avisos(); track $index) { <li>{{ a }}</li> }</ul>
  `,
})
export class PaginaDip {
  private readonly recordatorios = inject(Recordatorios);
  private readonly avisador = inject(Avisador) as AvisadorEnPagina;
  protected readonly tipoRegistro = inject(RegistroDeAvisos) instanceof RegistroEnMemoria ? 'memoria' : 'localStorage';
  protected readonly estado = signal('');
  protected readonly avisos = signal<string[]>([]);
  protected async programar(): Promise<void> {
    const manana = new Date(Date.now() + 3 * 3600_000).toISOString();
    this.estado.set(await this.recordatorios.programar('cita-1', manana));
    this.avisos.set([...this.avisador.avisos]);
  }
}
