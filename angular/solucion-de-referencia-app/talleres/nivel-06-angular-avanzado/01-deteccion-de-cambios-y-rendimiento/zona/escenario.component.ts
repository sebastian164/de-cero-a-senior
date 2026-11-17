import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

// Punto 6: tres actualizaciones de estado que dependen de Zone.js. Cada una tiene su versión corregida con señales.

/** Versión que solo funciona con zona: tres campos normales que cambian fuera de la plantilla. */
@Component({
  selector: 'app-escenario-roto',
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <section aria-labelledby="t-roto">
      <h2 id="t-roto">Con campos normales</h2>
      <p>Temporizador: <output data-id="roto-timer">{{ temporizador }}</output></p>
      <p>Petición: <output data-id="roto-fetch">{{ peticion }}</output></p>
      <p>Tecla: <output data-id="roto-tecla">{{ tecla }}</output></p>
    </section>
  `,
})
export class EscenarioRoto {
  temporizador = 'esperando';
  peticion = 'esperando';
  tecla = 'esperando';
  constructor() {
    setTimeout(() => (this.temporizador = 'listo'), 100);                                                  // 1. temporizador
    fetch('/api/n6-01/dato').then((r) => r.json()).then((d) => (this.peticion = d.valor));                  // 2. promesa fuera de HttpClient
    document.addEventListener('keydown', (e) => (this.tecla = e.key));                                      // 3. oyente que no es de la plantilla
  }
}

/** La misma pantalla con señales: funciona con y sin zona. */
@Component({
  selector: 'app-escenario-corregido',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-labelledby="t-ok">
      <h2 id="t-ok">Con señales</h2>
      <p>Temporizador: <output data-id="ok-timer">{{ temporizador() }}</output></p>
      <p>Petición: <output data-id="ok-fetch">{{ peticion() }}</output></p>
      <p>Tecla: <output data-id="ok-tecla">{{ tecla() }}</output></p>
    </section>
  `,
})
export class EscenarioCorregido {
  protected readonly temporizador = signal('esperando');
  protected readonly peticion = signal('esperando');
  protected readonly tecla = signal('esperando');
  constructor() {
    setTimeout(() => this.temporizador.set('listo'), 100);
    fetch('/api/n6-01/dato').then((r) => r.json()).then((d) => this.peticion.set(d.valor));
    document.addEventListener('keydown', (e) => this.tecla.set(e.key));
  }
}

@Component({
  selector: 'app-root',
  imports: [EscenarioRoto, EscenarioCorregido],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<main><h1>Zona frente a sin zona</h1><app-escenario-roto /><app-escenario-corregido /></main>`,
})
export class Raiz {}
