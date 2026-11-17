// registro.component.ts
import { Component, inject, signal } from '@angular/core';
import { applyEach, email, form, FormField, minLength, required, validate, validateHttp } from '@angular/forms/signals';
import { contactoEsquema, documentoValido, Registro } from './registro.reglas';
import { UsuariosApi } from './usuarios.api';

@Component({
  selector: 'app-registro',
  imports: [FormField],
  templateUrl: './registro.component.html',
})
export class RegistroComponent {
  private readonly usuarios = inject(UsuariosApi);

  protected readonly modelo = signal<Registro>({
    correo: '', clave: '', confirmacion: '', tipoDocumento: 'cedula', documento: '',
    contactos: [{ nombre: '', telefono: '' }],
  });

  protected readonly formulario = form(this.modelo, (r) => {
    required(r.correo, { message: 'Escribe tu correo' });
    email(r.correo, { message: 'El correo no tiene un formato válido' });
    validateHttp(r.correo, {                                    // solo corre si las reglas anteriores pasan
      debounce: 400,
      request: ({ value }) => (value() ? `/api/usuarios/existe?correo=${encodeURIComponent(value())}` : undefined),
      onSuccess: (respuesta: { existe: boolean }) =>
        respuesta.existe ? { kind: 'correoEnUso', message: 'Ese correo ya está registrado' } : undefined,
      onError: () => undefined,                                 // si el servicio falla, no bloquea al usuario
    });

    minLength(r.clave, 12, { message: 'La clave necesita al menos 12 caracteres' });
    validate(r.confirmacion, ({ value, valueOf }) =>
      value() === valueOf(r.clave) ? undefined : { kind: 'clavesDistintas', message: 'Las claves no coinciden' });

    validate(r.documento, ({ value, valueOf }) => documentoValido(valueOf(r.tipoDocumento), value()));
    applyEach(r.contactos, contactoEsquema);
  });

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal('');
  protected readonly registrado = signal(false);

  protected agregarContacto(): void {
    this.modelo.update((m) => ({ ...m, contactos: [...m.contactos, { nombre: '', telefono: '' }] }));
  }

  protected async enviar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (this.enviando()) return;                                // sin doble envío
    if (this.formulario().invalid()) {
      this.formulario().markAsTouched();                        // muestra todos los errores
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set('');
    try {
      await this.usuarios.registrar(this.modelo());
      this.registrado.set(true);
    } catch {
      this.errorGeneral.set('No pudimos registrarte. Tus datos siguen aquí; intenta de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }
}
