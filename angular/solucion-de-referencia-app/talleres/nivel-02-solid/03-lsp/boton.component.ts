import { booleanAttribute, Component, input } from '@angular/core';

/**
 * 3. Botón primario que NO envuelve a <button>: se aplica SOBRE un <button> nativo (selector de atributo).
 * Así conserva gratis disabled, type, foco, Enter/Espacio, el rol y cualquier atributo ARIA que le pongan.
 * Solo agrega lo suyo: estilo y un estado "cargando" que también deshabilita.
 */
@Component({
  selector: 'button[appBotonPrimario]',
  template: `@if (cargando()) { <span aria-hidden="true">⏳ </span> }<ng-content />`,
  host: {
    class: 'boton-primario',
    '[attr.aria-busy]': 'cargando() || null',
    '[disabled]': 'deshabilitado() || cargando()',
  },
  styles: `:host { background: #1d3557; color: #fff; border: 0; padding: .4rem .8rem; border-radius: 4px; }
           :host(:disabled) { opacity: .5; }`,
})
export class BotonPrimarioComponent {
  readonly cargando = input(false, { transform: booleanAttribute });
  // `disabled` escrito como atributo estático llega como '' y debe significar verdadero, igual que en el nativo.
  readonly deshabilitado = input(false, { alias: 'disabled', transform: booleanAttribute });
}
