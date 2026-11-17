import { HttpClient } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, map } from 'rxjs';
import { BlogApi } from './adaptadores';
import { ConCambiosSinGuardar } from './guardas';
import { Avisos, Rol, Sesion } from './sesion';

@Component({
  imports: [RouterLink],
  template: `
    <h1>Blog (adaptadores de /users y /posts)</h1>
    <p>Sesión: {{ sesion.activa() ? 'activa' : 'sin sesión' }} · <a routerLink="ingreso">Ingresar</a> ·
      <a routerLink="admin">Administración</a> · <a routerLink="editor">Editor</a></p>
    @if (datos(); as d) {
      <ul aria-label="Autores">@for (a of d.autores; track a.id) {
        <li>{{ a.nombre }} ({{ a.ciudad }}, {{ a.empresa }}): {{ publicacionesDe(d.publicaciones, a.id) }} publicaciones</li>
      }</ul>
    }
    <h2>Renovación del token</h2>
    <button type="button" (click)="tresPeticionesConTokenVencido()" [disabled]="!sesion.activa()">Tres peticiones con el token vencido</button>
    <p role="status">{{ resultado() }}</p>
    @if (avisos.ultimo(); as a) { <p role="alert">{{ a }}</p> }
  `,
})
export class PaginaBlog {
  protected readonly sesion = inject(Sesion);
  protected readonly avisos = inject(Avisos);
  private readonly http = inject(HttpClient);
  protected readonly datos = toSignal(inject(BlogApi).todo());
  protected readonly resultado = signal('');
  protected publicacionesDe(ps: readonly { autorId: string }[], id: string) { return ps.filter((p) => p.autorId === id).length; }

  protected tresPeticionesConTokenVencido() {
    this.sesion.cambiarToken('vencido');
    forkJoin([1, 2, 3].map((n) => this.http.get<{ dato: number }>(`/api/privado/${n}`))).subscribe((r) =>
      this.http.get<{ renovaciones: number }>('/api/_contadores').subscribe((c) =>
        this.resultado.set(`Respuestas: ${r.map((x) => x.dato).join(', ')} · renovaciones: ${c.renovaciones}`)));
  }
}

/** 5. La guarda impide VER esta pantalla, pero el dato llega del servidor a quien lo pida. */
@Component({
  template: `<h1>Administración</h1><p>Sueldos: {{ sueldos() }}</p>`,
})
export class PaginaAdmin {
  protected readonly sueldos = toSignal(inject(HttpClient).get<{ sueldos: number[] }>('/api/admin/datos').pipe(map((d) => d.sueldos.join(', '))));
}

@Component({
  template: `
    <h1>Editor</h1>
    <label>Nota <input (input)="texto.set($any($event.target).value)" [value]="texto()" /></label>
    <button type="button" (click)="guardado.set(texto())">Guardar</button>
    <p>{{ tieneCambiosSinGuardar() ? 'Cambios sin guardar' : 'Todo guardado' }}</p>
  `,
})
export class PaginaEditor implements ConCambiosSinGuardar {
  protected readonly texto = signal('');
  protected readonly guardado = signal('');
  tieneCambiosSinGuardar() { return this.texto() !== this.guardado(); }
}

@Component({
  template: `
    <h1>Ingreso</h1>
    <button type="button" (click)="entrar(['cliente'])">Ingresar como cliente</button>
    <button type="button" (click)="entrar(['admin'])">Ingresar como admin</button>
  `,
})
export class PaginaIngreso {
  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);
  readonly volverA = input<string>();
  protected entrar(roles: Rol[]) {
    this.sesion.ingresar('valido-0', roles);
    this.router.navigateByUrl(this.volverA() ?? '/n3-05/taller');
  }
}

@Component({ template: `<h1>Sin permiso</h1>` })
export class PaginaSinPermiso {}
