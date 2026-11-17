import { HttpClient } from '@angular/common/http';
import { Component, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { catchError, concatMap, debounceTime, distinctUntilChanged, map, Observable, of, retry, tap } from 'rxjs';

// Pieza que el README usa sin declarar.
@Injectable({ providedIn: 'root' })
export class DocumentosApi {
  private readonly http = inject(HttpClient);
  guardar(valor: Partial<{ titulo: string; cuerpo: string }>): Observable<unknown> {
    return this.http.post('/api/documentos', valor, { params: { fallar: '0' } });
  }
}

@Component({
  selector: 'app-editor',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="formulario">
      <label>Título <input formControlName="titulo" /></label>
      <label>Cuerpo <textarea formControlName="cuerpo"></textarea></label>
    </form>
    <p role="status">{{ estado() }}</p>
  `,
})
export class EditorComponent {
  private readonly api = inject(DocumentosApi);
  protected readonly formulario = inject(NonNullableFormBuilder).group({ titulo: '', cuerpo: '' });
  protected readonly estado = signal<'sin cambios' | 'guardando' | 'guardado' | 'error'>('sin cambios');

  constructor() {
    this.formulario.valueChanges.pipe(
      debounceTime(800),                                        // espera una pausa al escribir
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      tap(() => this.estado.set('guardando')),
      concatMap((valor) =>                                      // en orden: no se pisan versiones
        this.api.guardar(valor).pipe(
          retry({ count: 2, delay: 1000 }),
          map(() => 'guardado' as const),
          catchError(() => of('error' as const)),               // dentro: el flujo exterior sigue vivo
        )),
      takeUntilDestroyed(),
    ).subscribe((resultado) => this.estado.set(resultado));
  }
}
