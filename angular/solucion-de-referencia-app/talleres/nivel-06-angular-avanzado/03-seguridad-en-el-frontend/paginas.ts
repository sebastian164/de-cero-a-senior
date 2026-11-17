import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, Injectable, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { CanActivateFn, Router, RouterLink, RouterOutlet } from '@angular/router';

export interface Comentario { autor: string; texto: string; }

/** Sesión del taller: la bandera `rol` en localStorage es lo que lee la guarda (y lo que un atacante cambia); el token vive en memoria. */
@Injectable({ providedIn: 'root' })
export class Sesion {
  private readonly http = inject(HttpClient);
  token: string | null = null;
  readonly rolDelCliente = signal(localStorage.getItem('rol'));
  async iniciar(): Promise<void> {
    const r = await fetch('/api/n6-03/sesion', { method: 'POST', body: JSON.stringify({ usuario: 'admin', clave: 'admin123' }) });
    this.token = (await r.json()).token;
    localStorage.setItem('rol', 'admin'); this.rolDelCliente.set('admin');
    void this.http;
  }
  cerrar(): void { this.token = null; localStorage.removeItem('rol'); this.rolDelCliente.set(null); }
}

// Experiencia de usuario, NO seguridad: se salta cambiando localStorage.
export const soloAdmin: CanActivateFn = () => localStorage.getItem('rol') === 'admin' || inject(Router).createUrlTree(['/n6-03/taller']);

@Component({
  selector: 'app-marco-n6-03',
  imports: [RouterLink, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div>
      <h1>Seguridad en el frontend</h1>
      <nav aria-label="Puntos del taller">
        <a routerLink="xss-peligroso">XSS (peligroso)</a> | <a routerLink="xss-saneado">XSS (saneado)</a> | <a routerLink="xss-seguro">XSS (texto)</a> |
        <a routerLink="admin">Administración</a>
      </nav>
      <p>Bandera de rol en el cliente: <strong data-id="rol">{{ sesion.rolDelCliente() ?? 'ninguna' }}</strong></p>
      <button type="button" (click)="sesion.iniciar()">Iniciar sesión real (admin)</button>
      <button type="button" (click)="sesion.cerrar()">Cerrar</button>
      <router-outlet />
    </div>
  `,
})
export class Marco { protected readonly sesion = inject(Sesion); }

/** Punto 1, el error: el comentario de un usuario se inserta como HTML confiable. */
@Component({
  selector: 'app-xss-peligroso',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h2>Comentarios (con bypassSecurityTrustHtml)</h2>@for (c of html(); track $index) { <div class="comentario" [innerHTML]="c"></div> }`,
})
export class XssPeligroso {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly datos = toSignal(inject(HttpClient).get<Comentario[]>('/api/n6-03/comentarios'), { initialValue: [] });
  protected readonly html = () => this.datos().map((c): SafeHtml => this.sanitizer.bypassSecurityTrustHtml(c.texto));
}

/** Punto 1, corrección A: [innerHTML] sin bypass; Angular saneó el HTML y quitó el controlador de eventos. */
@Component({
  selector: 'app-xss-saneado',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h2>Comentarios (innerHTML saneado)</h2>@for (c of datos(); track $index) { <div class="comentario" [innerHTML]="c.texto"></div> }`,
})
export class XssSaneado { protected readonly datos = toSignal(inject(HttpClient).get<Comentario[]>('/api/n6-03/comentarios'), { initialValue: [] }); }

/** Punto 1, corrección B (la recomendada): texto. */
@Component({
  selector: 'app-xss-seguro',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h2>Comentarios (texto)</h2>@for (c of datos(); track $index) { <p class="comentario">{{ c.texto }}</p> }`,
})
export class XssSeguro { protected readonly datos = toSignal(inject(HttpClient).get<Comentario[]>('/api/n6-03/comentarios'), { initialValue: [] }); }

/** Punto 2: la guarda deja ver la pantalla; solo el servidor decide si entrega el dato. */
@Component({
  selector: 'app-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Administración</h2>
    @if (estado() === 'ok') { <p role="status" data-id="secreto">{{ secreto() }}</p> }
    @else if (estado() === 'rechazado') { <p role="alert">El servidor rechazó la petición ({{ codigo() }}).</p> }
  `,
})
export class Admin {
  protected readonly estado = signal<'cargando' | 'ok' | 'rechazado'>('cargando');
  protected readonly secreto = signal('');
  protected readonly codigo = signal(0);
  constructor() {
    const token = inject(Sesion).token;
    inject(HttpClient).get<{ secreto: string }>('/api/n6-03/secreto', { headers: token ? { Authorization: `Bearer ${token}` } : {} }).subscribe({
      next: (r) => { this.secreto.set(r.secreto); this.estado.set('ok'); },
      error: (e) => { this.codigo.set(e.status); this.estado.set('rechazado'); },
    });
  }
}
