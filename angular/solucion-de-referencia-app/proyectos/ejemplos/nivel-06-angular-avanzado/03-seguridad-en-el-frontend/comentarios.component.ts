import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

export interface Comentario { autor: string; texto: string; }

// README, "Seguro": Angular lo trata como texto
@Component({
  selector: 'app-comentario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p data-id="comentario">{{ comentario().texto }}</p>`,
})
export class ComentarioComponent { readonly comentario = input.required<Comentario>(); }

// README, "Redirección validada"
@Component({
  selector: 'app-volver',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button type="button" (click)="volver()">Volver</button>`,
})
export class VolverComponent {
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected volver(): void {
    const destino = this.ruta.snapshot.queryParamMap.get('volverA') ?? '/';
    const esInterna = destino.startsWith('/') && !destino.startsWith('//');
    this.router.navigateByUrl(esInterna ? destino : '/');
  }
}
