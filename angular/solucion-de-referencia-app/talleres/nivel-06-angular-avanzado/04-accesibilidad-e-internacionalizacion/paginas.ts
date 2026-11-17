import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChildren } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink, RouterOutlet } from '@angular/router';
import { DialogoConfirmar } from './dialogo.component';

@Component({
  selector: 'app-marco-n6-04',
  imports: [RouterLink, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div>
      <h1>Accesibilidad</h1>
      <nav aria-label="Puntos del taller"><a routerLink="antes">Antes (inaccesible)</a> | <a routerLink="despues">Después</a></nav>
      <router-outlet />
    </div>
  `,
  styles: `:focus-visible { outline: 3px solid #0b57d0; outline-offset: 2px }`,
})
export class Marco {}

/** Puntos 1 a 3, el "antes": div con clic, ícono sin nombre, campo con solo placeholder, imagen sin alternativa. */
@Component({
  selector: 'app-antes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Pedido 7</h2>
    <div class="boton" (click)="guardado.set(true)">Guardar</div>
    <span class="enlace" (click)="guardado.set(false)">Ir al inicio</span>
    <span class="icono" (click)="guardado.set(false)">✕</span>
    <input placeholder="Correo" style="display:block">
    <img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" width="10" height="10">
    <div class="titulo">Resumen</div>
    @if (guardado()) { <div class="estado">Guardado</div> }
  `,
  styles: `.boton, .enlace, .icono { cursor: pointer; display: inline-block; padding: 4px; color: #777 }`,
})
export class Antes { protected readonly guardado = signal(false); }

/** Puntos 2 a 5, el "después": elementos semánticos, formulario etiquetado y diálogo del CDK. */
@Component({
  selector: 'app-despues',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Pedido 7</h2>
    <p><button type="button" (click)="guardar()">Guardar</button>
       <a routerLink="/n6-04/taller">Ir al inicio</a>
       <button type="button" aria-label="Cerrar pedido" (click)="cerrar()"><span aria-hidden="true">✕</span></button></p>
    <p role="status" data-id="estado">{{ estado() }}</p>

    <button type="button" #abrir (click)="confirmar(abrir)">Eliminar pedido</button>
    <p role="status" data-id="resultado">{{ resultado() }}</p>

    <form [formGroup]="form" (ngSubmit)="enviar()" novalidate aria-labelledby="titulo-form">
      <h3 id="titulo-form">Datos de contacto</h3>
      @if (errores().length) {
        <div role="alert" data-id="resumen-errores"><p>Corrige estos campos:</p>
          <ul>@for (e of errores(); track e.campo) { <li>{{ e.texto }}</li> }</ul></div>
      }
      <p>
        <label for="nombre">Nombre</label>
        <input id="nombre" formControlName="nombre" autocomplete="name" [attr.aria-invalid]="invalido('nombre')" [attr.aria-describedby]="invalido('nombre') ? 'err-nombre' : null" #campo>
        @if (invalido('nombre')) { <span id="err-nombre" class="error">El nombre es obligatorio.</span> }
      </p>
      <p>
        <label for="correo">Correo</label>
        <input id="correo" type="email" formControlName="correo" autocomplete="email" [attr.aria-invalid]="invalido('correo')" [attr.aria-describedby]="invalido('correo') ? 'err-correo' : 'ayuda-correo'" #campo>
        <small id="ayuda-correo">Te escribiremos aquí.</small>
        @if (invalido('correo')) { <span id="err-correo" class="error">Escribe un correo válido.</span> }
      </p>
      <button type="submit">Enviar</button>
      <p role="status" data-id="enviado">{{ enviado() }}</p>
    </form>
  `,
  styles: `.error { color: #b00020 } button { margin-inline-end: 8px }`,
})
export class Despues {
  private readonly dialog = inject(Dialog);
  private readonly anunciador = inject(LiveAnnouncer);
  private readonly campos = viewChildren<ElementRef<HTMLInputElement>>('campo');
  protected readonly estado = signal('');
  protected readonly resultado = signal('');
  protected readonly enviado = signal('');
  protected readonly errores = signal<{ campo: string; texto: string }[]>([]);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nombre: ['', Validators.required],
    correo: ['', [Validators.required, Validators.email]],
  });
  protected guardar(): void { this.estado.set('Pedido guardado'); }
  protected cerrar(): void { this.estado.set('Pedido cerrado'); }
  protected invalido(c: 'nombre' | 'correo'): 'true' | null { const x = this.form.controls[c]; return x.invalid && (x.touched || this.errores().length > 0) ? 'true' : null; }
  protected confirmar(origen: HTMLElement): void {
    const ref = this.dialog.open<boolean>(DialogoConfirmar, { data: { pedido: 7 }, restoreFocus: true, autoFocus: 'first-tabbable', ariaModal: true });
    void origen;
    ref.closed.subscribe((si) => { this.resultado.set(si ? 'Pedido eliminado' : 'Eliminación cancelada'); void this.anunciador.announce(si ? 'Pedido eliminado' : 'Eliminación cancelada'); });
  }
  protected enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      const e = [];
      if (this.form.controls.nombre.invalid) e.push({ campo: 'nombre', texto: 'Nombre: es obligatorio.' });
      if (this.form.controls.correo.invalid) e.push({ campo: 'correo', texto: 'Correo: escribe un correo válido.' });
      this.errores.set(e);
      this.campos().find((c) => this.form.get(c.nativeElement.id)?.invalid)?.nativeElement.focus();   // foco al primer error
      return;
    }
    this.errores.set([]);
    this.enviado.set('Formulario enviado');
  }
}
