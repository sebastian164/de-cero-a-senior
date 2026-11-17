import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SESIONES } from './sesiones';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="barra">
      <a routerLink="/" class="marca">De cero a senior · Angular 22</a>
    </header>
    <div class="cuerpo">
      <nav aria-label="Sesiones">
        <ul>
          @for (s of sesiones; track s.id) {
            <li>
              <span>{{ s.id }} {{ s.titulo }}</span>
              @if (s.ejemplo) { <a [routerLink]="'/' + s.id + '/ejemplo'" routerLinkActive="activo">ejemplo</a> }
              <a [routerLink]="'/' + s.id + '/taller'" routerLinkActive="activo">taller</a>
            </li>
          }
        </ul>
      </nav>
      <main id="contenido"><router-outlet /></main>
    </div>
  `,
  styles: `
    .barra { padding: .5rem 1rem; background: #1d3557; } .marca { color: #fff; font-weight: bold; }
    .cuerpo { display: flex; gap: 1rem; } nav { width: 18rem; font-size: .8rem; flex-shrink: 0; }
    nav ul { list-style: none; padding: 0 .5rem; } nav li { margin: .2rem 0; } nav a { margin-left: .3rem; }
    .activo { font-weight: bold; } main { flex: 1; padding: 1rem; min-width: 0; }
  `,
})
export class App {
  protected readonly sesiones = SESIONES;
}
