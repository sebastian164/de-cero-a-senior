import { Component, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  template: `
    <h1>Anfitrión</h1>
    <nav><a routerLink="/inicio">Inicio</a> | <a routerLink="/resumen">Resumen (remoto)</a></nav>
    <button type="button" (click)="entrar()">Entrar como ana</button> <span data-prueba="host-sesion">{{ usuario() }}</span>
    <main><router-outlet /></main>
  `,
  styles: `h2 { color: rgb(0, 0, 255); }`,
})
export class App {
  protected readonly usuario = signal(sessionStorage.getItem('sesion') ?? 'anónimo');
  protected entrar(): void { sessionStorage.setItem('sesion', 'ana'); this.usuario.set('ana'); }
}
