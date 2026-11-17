import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { map, Subject } from 'rxjs';
import { autoguardado, envioProtegido, EstadoEnvio, EstadoGuardado, estadoTablero, sondeo, EstadoTablero, ResultadoSondeo, visibilidadDe } from './flujos';

@Component({
  imports: [ReactiveFormsModule],
  template: `
    <h1>RxJS</h1>
    <section aria-labelledby="t1"><h2 id="t1">Autoguardado (la API falla al azar el 30 %)</h2>
      <form [formGroup]="documento"><label>Título <input formControlName="titulo" /></label></form>
      <p data-testid="guardado">{{ guardado() }}</p></section>
    <section aria-labelledby="t2"><h2 id="t2">Contacto</h2>
      <form [formGroup]="contacto" (ngSubmit)="enviar()">
        <label>Correo <input type="email" formControlName="correo" /></label>
        <label>Mensaje <textarea formControlName="mensaje"></textarea></label>
        <button type="submit" [attr.aria-busy]="estadoEnvio() === 'enviando'">Enviar mensaje</button>
      </form>
      @switch (estadoEnvio()) {
        @case ('enviando') { <p role="status" data-testid="estado-envio">Enviando…</p> }
        @case ('enviado') { <p role="status" data-testid="estado-envio">Mensaje enviado</p> }
        @case ('error') { <p role="alert" data-testid="estado-envio">No se pudo enviar el mensaje. Vuelve a intentarlo.</p> }
      }
      <p data-testid="envios">Respuestas: {{ respuestas() }}</p></section>
    <section aria-labelledby="t3"><h2 id="t3">Tablero</h2>
      @switch (tablero().estado) {
        @case ('listo') {
          @if (tableroListo(); as t) {
            <p>Ventas: {{ t.ventas }} · Clientes nuevos: {{ t.clientesNuevos }}</p>
            <p>{{ t.recomendaciones ? t.recomendaciones.length + ' recomendaciones' : 'Recomendaciones no disponibles' }}</p>
          }
        }
        @case ('error') { <p role="alert">No se pudo cargar el tablero.</p> }
        @default { <p>Cargando tablero…</p> }
      }</section>
    <section aria-labelledby="t4"><h2 id="t4">Sondeo</h2>
      <p data-testid="sondeo">Consultas: {{ consultas() }}</p>
      <p data-testid="sondeo-ultima">Última consulta: {{ ultima() }}</p></section>
  `,
})
export class PaginaRxjs {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly documento = this.fb.group({ titulo: '' });
  protected readonly contacto = this.fb.group({
    correo: ['', [Validators.required, Validators.email]],
    mensaje: ['', Validators.required],
  });
  protected readonly guardado = signal<EstadoGuardado>('sin cambios');
  protected readonly envios = new Subject<void>();
  protected readonly estadoEnvio = signal<EstadoEnvio | null>(null);
  protected readonly respuestas = signal(0);
  protected readonly tablero = signal<EstadoTablero>({ estado: 'cargando' });
  protected readonly consultas = signal(0);
  protected readonly ultima = signal<'ninguna' | ResultadoSondeo<unknown>['estado']>('ninguna');

  protected tableroListo() { const t = this.tablero(); return t.estado === 'listo' ? t.tablero : null; }

  protected enviar(): void {
    if (this.contacto.invalid) { this.contacto.markAllAsTouched(); return; }
    this.envios.next();
  }

  constructor() {
    this.documento.valueChanges.pipe(
      autoguardado((v) => this.http.post('/api/documentos', v)),
      takeUntilDestroyed(),
    ).subscribe((e) => this.guardado.set(e));

    this.envios.pipe(envioProtegido(() => this.http.post('/api/contacto', this.contacto.getRawValue())), takeUntilDestroyed())
      .subscribe((e) => { this.estadoEnvio.set(e); if (e === 'enviado') this.respuestas.update((n) => n + 1); });

    estadoTablero({
      ventas: () => this.http.get<{ total: number }>('/api/tablero/ventas').pipe(map((r) => r.total)),
      clientes: () => this.http.get<{ nuevos: number }>('/api/tablero/clientes').pipe(map((r) => r.nuevos)),
      recomendaciones: () => this.http.get<string[]>('/api/tablero/recomendaciones'),
    }).pipe(takeUntilDestroyed()).subscribe((t) => this.tablero.set(t));

    sondeo(() => this.http.get('/api/estado'), visibilidadDe(inject(DOCUMENT)), 10_000)
      .pipe(takeUntilDestroyed()).subscribe((r) => { this.ultima.set(r.estado); if (r.estado === 'ok') this.consultas.update((n) => n + 1); });
  }
}
