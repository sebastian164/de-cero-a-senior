// El mismo formulario, sin los contactos, con formularios reactivos (punto 9 del taller).
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, AsyncValidatorFn, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { catchError, map, of, switchMap, timer } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { clavesIguales, documentoValido, esMenorDeEdad, fechaNacimientoValida } from './clinica.reglas';

const aError = (e: { kind: string; message: string } | undefined): ValidationErrors | null => (e ? { [e.kind]: e.message } : null);

export const documentoCoherente: ValidatorFn = (g) => aError(documentoValido(g.get('tipoDocumento')?.value, g.get('documento')?.value ?? ''));
export const claveCoincide: ValidatorFn = (g) => aError(clavesIguales(g.get('clave')?.value, g.get('confirmacion')?.value));
export const acudienteSiMenor = (hoy: () => Date): ValidatorFn => (g) =>
  esMenorDeEdad(g.get('fechaNacimiento')?.value ?? '', hoy()) && !g.get('acudiente')?.value ? { acudiente: 'Un menor de edad necesita un acudiente' } : null;

export const correoDisponible = (http: HttpClient): AsyncValidatorFn => (control: AbstractControl) =>
  timer(400).pipe(
    switchMap(() => http.get<{ existe: boolean }>('/api/usuarios/existe', { params: { correo: control.value } })),
    map((r) => (r.existe ? { correoEnUso: true } : null)),
    catchError(() => of(null)),
  );

@Component({
  selector: 'app-clinica-reactiva',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="formulario" (ngSubmit)="enviar()" novalidate aria-label="Registro reactivo">
      <label for="r-correo">Correo</label>
      <input id="r-correo" type="email" formControlName="correo" />
      @if (formulario.controls.correo.touched && formulario.controls.correo.errors?.['correoEnUso']) { <p role="alert">Ese correo ya está registrado</p> }
      <label for="r-clave">Clave</label><input id="r-clave" type="password" formControlName="clave" />
      <label for="r-conf">Confirma la clave</label><input id="r-conf" type="password" formControlName="confirmacion" />
      @if (formulario.errors?.['clavesDistintas']) { <p role="alert">Las claves no coinciden</p> }
      <button type="submit">Registrar</button>
    </form>
    @if (enviado()) { <p role="status">Enviado</p> }
  `,
})
export class ClinicaReactivaComponent {
  private readonly http = inject(HttpClient);
  readonly hoy = signal(new Date());
  readonly formulario = inject(NonNullableFormBuilder).group({
    nombre: ['', Validators.required],
    tipoDocumento: ['cedula' as 'cedula' | 'pasaporte'],
    documento: [''],
    correo: ['', [Validators.required, Validators.email], [correoDisponible(this.http)]],
    clave: ['', [Validators.required, Validators.minLength(12)]],
    confirmacion: ['', Validators.required],
    fechaNacimiento: ['', (c: AbstractControl) => aError(fechaNacimientoValida(c.value, this.hoy()))],
    acudiente: [''],
  }, { validators: [documentoCoherente, claveCoincide, acudienteSiMenor(() => this.hoy())] });
  readonly enviado = signal(false);
  protected enviar() { if (this.formulario.valid) this.enviado.set(true); else this.formulario.markAllAsTouched(); }
}
