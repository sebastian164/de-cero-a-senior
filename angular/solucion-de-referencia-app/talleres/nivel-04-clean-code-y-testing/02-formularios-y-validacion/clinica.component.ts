import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { CanDeactivateFn, RouterLink } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { applyEach, email, form, FormField, hidden, minLength, pattern, required, schema, submit, validate, validateHttp } from '@angular/forms/signals';
import { CONFIRMAR_SALIDA, ClinicaApi } from './clinica.api';
import { cantidadContactosValida, clavesIguales, documentoValido, esMenorDeEdad, fechaNacimientoValida, MAX_CONTACTOS, TipoDocumento } from './clinica.reglas';

export interface Contacto { nombre: string; telefono: string; }
export interface Paciente {
  nombre: string; tipoDocumento: TipoDocumento; documento: string; correo: string;
  clave: string; confirmacion: string; fechaNacimiento: string; acudiente: string; contactos: Contacto[];
}

const contactoEsquema = schema<Contacto>((c) => {
  required(c.nombre, { message: 'Escribe el nombre del contacto' });
  pattern(c.telefono, /^\d{10}$/, { message: 'El teléfono tiene 10 dígitos' });
});

@Component({
  selector: 'app-clinica',
  imports: [FormField, NgTemplateOutlet, RouterLink],
  template: `
    <ng-template #errores let-campo let-id="id">
      <p [id]="id" role="alert" class="error">
        @if (campo().touched() && campo().invalid()) { {{ campo().errors()[0].message }} }
      </p>
    </ng-template>

    <h1>Registro de paciente</h1>
    <form (submit)="enviar($event)" novalidate aria-label="Registro de paciente">
      <label for="nombre">Nombre completo</label>
      <input id="nombre" autocomplete="name" [formField]="formulario.nombre"
             [attr.aria-invalid]="formulario.nombre().touched() && formulario.nombre().invalid()" aria-describedby="nombre-error" />
      <ng-container *ngTemplateOutlet="errores; context: { $implicit: formulario.nombre, id: 'nombre-error' }" />

      <label for="tipo">Tipo de documento</label>
      <select id="tipo" [formField]="formulario.tipoDocumento">
        <option value="cedula">Cédula</option>
        <option value="pasaporte">Pasaporte</option>
      </select>
      <label for="documento">Documento</label>
      <input id="documento" [formField]="formulario.documento"
             [attr.aria-invalid]="formulario.documento().touched() && formulario.documento().invalid()" aria-describedby="documento-error" />
      <ng-container *ngTemplateOutlet="errores; context: { $implicit: formulario.documento, id: 'documento-error' }" />

      <label for="correo">Correo</label>
      <input id="correo" type="email" autocomplete="email" [formField]="formulario.correo"
             [attr.aria-invalid]="formulario.correo().touched() && formulario.correo().invalid()" aria-describedby="correo-error" />
      @if (formulario.correo().pending()) { <span role="status">Comprobando el correo…</span> }
      <ng-container *ngTemplateOutlet="errores; context: { $implicit: formulario.correo, id: 'correo-error' }" />

      <label for="clave">Clave</label>
      <input id="clave" type="password" autocomplete="new-password" [formField]="formulario.clave"
             [attr.aria-invalid]="formulario.clave().touched() && formulario.clave().invalid()" aria-describedby="clave-error" />
      <ng-container *ngTemplateOutlet="errores; context: { $implicit: formulario.clave, id: 'clave-error' }" />
      <label for="confirmacion">Confirma la clave</label>
      <input id="confirmacion" type="password" autocomplete="new-password" [formField]="formulario.confirmacion"
             [attr.aria-invalid]="formulario.confirmacion().touched() && formulario.confirmacion().invalid()" aria-describedby="confirmacion-error" />
      <ng-container *ngTemplateOutlet="errores; context: { $implicit: formulario.confirmacion, id: 'confirmacion-error' }" />

      <label for="nacimiento">Fecha de nacimiento</label>
      <input id="nacimiento" type="date" [formField]="formulario.fechaNacimiento"
             [attr.aria-invalid]="formulario.fechaNacimiento().touched() && formulario.fechaNacimiento().invalid()" aria-describedby="nacimiento-error" />
      <ng-container *ngTemplateOutlet="errores; context: { $implicit: formulario.fechaNacimiento, id: 'nacimiento-error' }" />

      @if (!formulario.acudiente().hidden()) {
        <label for="acudiente">Nombre del acudiente (menor de edad)</label>
        <input id="acudiente" [formField]="formulario.acudiente"
               [attr.aria-invalid]="formulario.acudiente().touched() && formulario.acudiente().invalid()" aria-describedby="acudiente-error" />
        <ng-container *ngTemplateOutlet="errores; context: { $implicit: formulario.acudiente, id: 'acudiente-error' }" />
      }

      @for (contacto of formulario.contactos; track $index) {
        <fieldset>
          <legend>Contacto de emergencia {{ $index + 1 }}</legend>
          <label>Nombre del contacto {{ $index + 1 }} <input [formField]="contacto.nombre" /></label>
          <label>Teléfono del contacto {{ $index + 1 }} <input inputmode="numeric" [formField]="contacto.telefono" /></label>
          @if (contacto.nombre().touched() && contacto.nombre().invalid()) { <p role="alert">{{ contacto.nombre().errors()[0].message }}</p> }
          @if (contacto.telefono().touched() && contacto.telefono().invalid()) { <p role="alert">{{ contacto.telefono().errors()[0].message }}</p> }
          <button type="button" (click)="quitarContacto($index)" [disabled]="modelo().contactos.length <= 1">Quitar contacto {{ $index + 1 }}</button>
        </fieldset>
      }
      <button type="button" (click)="agregarContacto()" [disabled]="modelo().contactos.length >= maxContactos">Agregar contacto</button>

      @if (errorGeneral()) { <p role="alert">{{ errorGeneral() }}</p> }
      <button type="submit" [disabled]="enviando()">{{ enviando() ? 'Enviando…' : 'Registrar paciente' }}</button>
    </form>
    @if (registrado()) { <p role="status">Paciente registrado</p> }
    <a routerLink="salida">Salir sin registrar</a>
  `,
})
export class ClinicaComponent {
  private readonly api = inject(ClinicaApi);
  protected readonly maxContactos = MAX_CONTACTOS;
  /** La fecha de "hoy" es una señal para poder fijarla en las pruebas. */
  readonly hoy = signal(new Date());

  private static readonly INICIAL: Paciente = {
    nombre: '', tipoDocumento: 'cedula', documento: '', correo: '', clave: '', confirmacion: '',
    fechaNacimiento: '', acudiente: '', contactos: [{ nombre: '', telefono: '' }],
  };
  readonly modelo = signal<Paciente>(structuredClone(ClinicaComponent.INICIAL));

  readonly formulario = form(this.modelo, (p) => {
    required(p.nombre, { message: 'Escribe tu nombre' });
    validate(p.documento, ({ value, valueOf }) => documentoValido(valueOf(p.tipoDocumento), value()));

    required(p.correo, { message: 'Escribe tu correo' });
    email(p.correo, { message: 'El correo no tiene un formato válido' });
    validateHttp(p.correo, {
      debounce: 400,
      request: ({ value }) => (value() ? `/api/usuarios/existe?correo=${encodeURIComponent(value())}` : undefined),
      onSuccess: (r: { existe: boolean }) => (r.existe ? { kind: 'correoEnUso', message: 'Ese correo ya está registrado' } : undefined),
      onError: () => undefined,                         // si el servidor falla, no bloquea
    });

    minLength(p.clave, 12, { message: 'La clave necesita al menos 12 caracteres' });
    validate(p.confirmacion, ({ value, valueOf }) => clavesIguales(valueOf(p.clave), value()));

    validate(p.fechaNacimiento, ({ value }) => fechaNacimientoValida(value(), this.hoy()));

    // El acudiente solo existe (y solo es obligatorio) si la persona es menor de edad.
    hidden(p.acudiente, ({ valueOf }) => !esMenorDeEdad(valueOf(p.fechaNacimiento), this.hoy()));
    required(p.acudiente, { message: 'Un menor de edad necesita un acudiente', when: ({ valueOf }) => esMenorDeEdad(valueOf(p.fechaNacimiento), this.hoy()) });

    validate(p.contactos, ({ value }) => cantidadContactosValida(value().length));
    applyEach(p.contactos, contactoEsquema);
  });

  readonly enviando = signal(false);
  readonly errorGeneral = signal('');
  readonly registrado = signal(false);
  /**
   * Para la guarda: hay datos sin guardar si el modelo difiere del inicial y todavía no se registró.
   * Se compara el modelo y no `dirty()`: en Chromium con Angular 22.2.6 `dirty()` ya era `true` al abrir el formulario
   * (lo causa el campo `input type="date"` al enlazarse), así que la guarda preguntaría siempre.
   */
  readonly haySinGuardar = computed(() => JSON.stringify(this.modelo()) !== JSON.stringify(ClinicaComponent.INICIAL) && !this.registrado());

  protected agregarContacto() { this.modelo.update((m) => ({ ...m, contactos: [...m.contactos, { nombre: '', telefono: '' }] })); }
  protected quitarContacto(i: number) { this.modelo.update((m) => ({ ...m, contactos: m.contactos.filter((_, j) => j !== i) })); }

  protected async enviar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (this.enviando()) return;
    this.errorGeneral.set('');
    this.enviando.set(true);
    try {
      await submit(this.formulario, {
        action: async (f) => {
          try {
            await this.api.registrar(this.modelo());
            this.registrado.set(true);
            return undefined;
          } catch (e) {
            if (e instanceof HttpErrorResponse && e.status === 422) {
              // El servidor señala el campo ({ campo, mensaje }): el error se asigna a ese campo; si no es uno conocido, es general.
              const campos = { nombre: f.nombre, documento: f.documento, correo: f.correo } as const;
              const destino = campos[e.error?.campo as keyof typeof campos];
              if (destino) return [{ fieldTree: destino, kind: 'servidor', message: e.error.mensaje as string }];
              this.errorGeneral.set((e.error?.mensaje as string | undefined) ?? 'El servidor rechazó los datos.');
              return undefined;
            }
            this.errorGeneral.set('No pudimos registrarte. Tus datos siguen aquí; intenta de nuevo.');
            return undefined;
          }
        },
        onInvalid: (f) => f().errorSummary()[0]?.fieldTree().focusBoundControl(),   // foco al primer error
      });
      // Tras un 422, el campo con el error recibe el foco.
      const primero = this.formulario().errorSummary()[0];
      if (primero && !this.registrado()) primero.fieldTree().focusBoundControl();
    } finally {
      this.enviando.set(false);
    }
  }
}

export const sinGuardar: CanDeactivateFn<ClinicaComponent> = (componente) =>
  !componente.haySinGuardar() || inject(CONFIRMAR_SALIDA)('Tienes cambios sin guardar. ¿Salir de todos modos?');
