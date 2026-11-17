import { Component, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';

// Componente remoto: lo construye y despliega otro equipo; el anfitrión lo carga en ejecución.
@Component({
  selector: 'remoto-resumen',
  imports: [CurrencyPipe],
  template: `
    <section aria-labelledby="t-remoto">
      <h2 id="t-remoto">Resumen del remoto</h2>
      <p>Sesión vista por el remoto: <strong data-prueba="sesion">{{ usuario() }}</strong></p>
      <p>Total: <span data-prueba="total">{{ 150000 | currency: 'COP' : 'symbol-narrow' : '1.0-0' }}</span></p>
      <button type="button" (click)="contar.set(contar() + 1)">Remoto pulsado {{ contar() }}</button>
    </section>
  `,
  styles: `h2 { color: rgb(0, 128, 0); } section { border: 2px dashed green; padding: 8px; }`,
})
export class Resumen {
  protected readonly contar = signal(0);
  // Problema de sesión: el remoto no comparte el servicio del anfitrión (otro bundle); lee el almacenamiento del navegador.
  protected readonly usuario = signal(sessionStorage.getItem('sesion') ?? '(sin sesión)');
}
