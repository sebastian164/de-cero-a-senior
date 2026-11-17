import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header><nav aria-label="Principal"><a routerLink="/">Inicio</a> | <a routerLink="/privado">Privado</a></nav></header>
    <main><router-outlet /></main>
  `,
})
export class App {}
