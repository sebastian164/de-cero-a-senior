import { AsyncValidatorFn, NonNullableFormBuilder, ValidatorFn, Validators } from '@angular/forms';
import { Component, inject } from '@angular/core';
import { catchError, map, of, switchMap, timer } from 'rxjs';
import { UsuariosApi } from './usuarios.api';

export const clavesIguales: ValidatorFn = (grupo) =>
  grupo.get('clave')?.value === grupo.get('confirmacion')?.value ? null : { clavesDistintas: true };

export function correoDisponible(api: UsuariosApi): AsyncValidatorFn {
  return (control) => timer(400).pipe(                         // espera a que deje de escribir
    switchMap(() => api.existeCorreo(control.value)),
    map((existe) => (existe ? { correoEnUso: true } : null)),
    catchError(() => of(null)),
  );
}

@Component({ selector: 'app-registro-reactivo', template: '' })
export class RegistroReactivoComponent {
  private readonly usuarios = inject(UsuariosApi);
  protected readonly formulario = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email], [correoDisponible(this.usuarios)]],
    clave: ['', [Validators.required, Validators.minLength(12)]],
    confirmacion: ['', Validators.required],
  }, { validators: [clavesIguales] });
}
