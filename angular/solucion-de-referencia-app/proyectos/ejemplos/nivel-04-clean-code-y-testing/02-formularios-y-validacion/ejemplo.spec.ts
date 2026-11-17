import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { documentoValido } from './registro.reglas';
import { RegistroComponent } from './registro.component';
import { correoDisponible, clavesIguales } from './reactivo';
import { FormControl, FormGroup } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { UsuariosApi } from './usuarios.api';

describe('n4-02 ejemplo', () => {
  it('documentoValido es una función pura', () => {
    expect(documentoValido('cedula', '123456')).toBeUndefined();
    expect(documentoValido('cedula', '12ab')).toEqual({ kind: 'documento', message: 'Entre 6 y 10 dígitos' });
    expect(documentoValido('pasaporte', 'AB1234')).toBeUndefined();
    expect(documentoValido('pasaporte', 'ab')?.message).toBe('Entre 6 y 9 letras o números');
  });

  it('el formulario con señales muestra el error del correo al tocarlo y no envía si es inválido', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const f = TestBed.createComponent(RegistroComponent);
    await f.whenStable();
    const el: HTMLElement = f.nativeElement;
    const correo = el.querySelector<HTMLInputElement>('#correo')!;
    correo.dispatchEvent(new Event('blur'));
    await f.whenStable();
    expect(el.querySelector('#correo-error')!.textContent).toContain('Escribe tu correo');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await f.whenStable();
    TestBed.inject(HttpTestingController).expectNone('/api/usuarios');
  });

  it('reactivo: validadores de grupo y asíncrono', async () => {
    const g = new FormGroup({ clave: new FormControl('a'), confirmacion: new FormControl('b') });
    expect(clavesIguales(g)).toEqual({ clavesDistintas: true });
    g.patchValue({ confirmacion: 'a' });
    expect(clavesIguales(g)).toBeNull();
    const api = (r: boolean | Error) => ({ existeCorreo: () => (r instanceof Error ? throwError(() => r) : of(r)) }) as unknown as UsuariosApi;
    const { firstValueFrom } = await import('rxjs');
    const control = new FormControl('a@b.co');
    expect(await firstValueFrom(correoDisponible(api(true))(control) as never)).toEqual({ correoEnUso: true });
    expect(await firstValueFrom(correoDisponible(api(false))(control) as never)).toBeNull();
    expect(await firstValueFrom(correoDisponible(api(new Error('x')))(control) as never)).toBeNull();
  });
});
