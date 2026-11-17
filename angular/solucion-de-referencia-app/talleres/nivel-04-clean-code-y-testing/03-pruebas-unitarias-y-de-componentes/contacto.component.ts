import { Component, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface Consulta { correo: string; mensaje: string; }
export const validarConsulta = (c: Consulta): Partial<Record<keyof Consulta, string>> => ({
  ...( /^\S+@\S+\.\S+$/.test(c.correo) ? {} : { correo: 'Escribe un correo válido' }),
  ...(c.mensaje.trim().length >= 10 ? {} : { mensaje: 'El mensaje necesita al menos 10 caracteres' }),
});

/** Formulario de contacto probado "como usuario": llenar, enviar, ver errores. */
@Component({
  selector: 'app-contacto',
  imports: [FormsModule],
  template: `
    <form (submit)="enviar($event)" novalidate>
      <label for="c-correo">Correo</label>
      <input id="c-correo" name="correo" [(ngModel)]="consulta.correo" />
      @if (errores().correo) { <p role="alert">{{ errores().correo }}</p> }
      <label for="c-mensaje">Mensaje</label>
      <textarea id="c-mensaje" name="mensaje" [(ngModel)]="consulta.mensaje"></textarea>
      @if (errores().mensaje) { <p role="alert">{{ errores().mensaje }}</p> }
      <button type="submit">Enviar consulta</button>
    </form>
    @if (enviada()) { <p role="status">Consulta enviada</p> }
  `,
})
export class ContactoComponent {
  readonly consultaEnviada = output<Consulta>();
  protected consulta: Consulta = { correo: '', mensaje: '' };
  protected readonly errores = signal<ReturnType<typeof validarConsulta>>({});
  protected readonly enviada = signal(false);
  protected enviar(e: Event) {
    e.preventDefault();
    const errores = validarConsulta(this.consulta);
    this.errores.set(errores);
    if (Object.keys(errores).length) return;
    this.consultaEnviada.emit({ ...this.consulta });
    this.enviada.set(true);
  }
}
