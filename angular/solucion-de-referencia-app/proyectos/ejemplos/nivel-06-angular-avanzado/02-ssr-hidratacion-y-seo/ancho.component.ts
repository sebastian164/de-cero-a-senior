import { ChangeDetectionStrategy, Component, afterNextRender, signal } from '@angular/core';

// README: "Bien: solo en el navegador, tras el renderizado"
@Component({
  selector: 'app-ancho',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p>Ancho: <output data-id="ancho">{{ ancho() }}</output></p>`,
})
export class Ancho {
  protected readonly ancho = signal(0);
  constructor() {
    afterNextRender(() => {
      this.ancho.set(window.innerWidth);
    });
  }
}
