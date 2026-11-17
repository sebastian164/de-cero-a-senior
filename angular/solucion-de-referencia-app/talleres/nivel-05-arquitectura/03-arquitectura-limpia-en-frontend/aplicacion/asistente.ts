import { computed, inject, Injectable, signal } from '@angular/core';
import { erroresDelPaso, Errores, Paso, pasoDelCampo, pasosAplicables, puedeEnviar, Solicitud, SOLICITUD_VACIA } from '../dominio/solicitud';
import { RepositorioSolicitudes, SolicitudRechazada } from '../dominio/puertos';

/** Caso de uso y estado del asistente: orquesta el dominio y el puerto; no conoce HTTP. */
@Injectable()
export class AsistenteSolicitud {
  private readonly repositorio = inject(RepositorioSolicitudes);
  readonly solicitud = signal<Solicitud>(SOLICITUD_VACIA);
  private readonly indice = signal(0);
  readonly mostrarErrores = signal(false);
  readonly erroresServidor = signal<Errores>({});
  readonly enviada = signal<number | undefined>(undefined);
  readonly enviando = signal(false);
  /** Error que no es de validación (500, sin red): se muestra al usuario, que puede volver a intentarlo. */
  readonly errorGeneral = signal<string | undefined>(undefined);

  readonly pasos = computed(() => pasosAplicables(this.solicitud()));
  readonly paso = computed<Paso>(() => this.pasos()[Math.min(this.indice(), this.pasos().length - 1)]);
  readonly errores = computed(() => ({ ...erroresDelPaso(this.paso(), this.solicitud()), ...this.erroresServidor() }));
  readonly esUltimo = computed(() => this.paso() === 'revision');

  actualizar(cambios: Partial<Solicitud>) { this.solicitud.update((s) => ({ ...s, ...cambios })); this.erroresServidor.set({}); }
  avanzar() {
    this.mostrarErrores.set(true);
    if (Object.keys(erroresDelPaso(this.paso(), this.solicitud())).length) return;
    this.mostrarErrores.set(false);
    this.indice.update((i) => Math.min(i + 1, this.pasos().length - 1));
  }
  retroceder() { this.indice.update((i) => Math.max(0, i - 1)); this.mostrarErrores.set(false); }

  enviar() {
    if (this.enviando()) return;
    if (!puedeEnviar(this.solicitud())) { this.mostrarErrores.set(true); return; }
    this.enviando.set(true); this.errorGeneral.set(undefined);
    this.repositorio.enviar(this.solicitud()).subscribe({
      next: ({ id }) => { this.enviando.set(false); this.enviada.set(id); },
      error: (e: unknown) => {
        this.enviando.set(false);
        if (e instanceof SolicitudRechazada) {
          // El servidor vuelve a validar: sus errores se muestran en el paso del primer campo rechazado.
          this.erroresServidor.set(e.campos);
          const paso = pasoDelCampo(Object.keys(e.campos)[0] ?? '');
          const i = this.pasos().indexOf(paso);
          this.indice.set(i >= 0 ? i : this.pasos().length - 1);
          this.mostrarErrores.set(true);
        } else {
          this.errorGeneral.set('No se pudo enviar la solicitud. Vuelve a intentarlo.');
        }
      },
    });
  }
}
