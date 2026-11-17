import { signal, Signal } from '@angular/core';
import { firstValueFrom, isObservable, Observable } from 'rxjs';

export interface Entrada<T> {
  readonly datos: T | undefined;
  readonly cargando: boolean;
  readonly error: unknown;
  readonly obsoleta: boolean;
}

interface Registro<T> {
  estado: ReturnType<typeof signal<Entrada<T>>>;
  buscar: () => Observable<T> | Promise<T>;
  actualizadoEn?: number;
  enVuelo?: Promise<void>;
  generacion: number;
}

/**
 * Caché de estado de servidor por clave: deduplica peticiones en vuelo, sirve lo guardado mientras revalida
 * (stale-while-revalidate) y se invalida tras una mutación.
 */
export class CacheServidor {
  private readonly registros = new Map<string, Registro<unknown>>();
  constructor(private readonly opciones: { vigenciaMs: number; ahora?: () => number } = { vigenciaMs: 30_000 }) {}
  private ahora() { return (this.opciones.ahora ?? Date.now)(); }

  leer<T>(clave: string, buscar: () => Observable<T> | Promise<T>): Signal<Entrada<T>> {
    let r = this.registros.get(clave) as Registro<T> | undefined;
    if (!r) {
      r = { estado: signal<Entrada<T>>({ datos: undefined, cargando: false, error: undefined, obsoleta: true }), buscar, generacion: 0 };
      this.registros.set(clave, r);
    }
    r.buscar = buscar;
    const vigente = r.actualizadoEn !== undefined && this.ahora() - r.actualizadoEn < this.opciones.vigenciaMs && !r.estado().obsoleta;
    if (!vigente) this.cargar(r);                       // si ya hay una en vuelo, `cargar` la reutiliza
    return r.estado.asReadonly();
  }

  /** Marca como obsoletas las entradas que empiezan por `prefijo` y las vuelve a pedir en segundo plano. */
  invalidar(prefijo: string): void {
    for (const [clave, r] of this.registros) {
      if (!clave.startsWith(prefijo)) continue;
      r.generacion++;                                    // una respuesta en vuelo anterior ya no vale
      r.estado.update((e) => ({ ...e, obsoleta: true }));
      r.enVuelo = undefined;
      this.cargar(r);
    }
  }

  /** Ejecuta una escritura y, si sale bien, invalida lo que cambia. */
  async mutar<R>(accion: () => Observable<R> | Promise<R>, invalida: string[]): Promise<R> {
    const origen = accion();
    const resultado = await (isObservable(origen) ? firstValueFrom(origen) : origen);
    invalida.forEach((p) => this.invalidar(p));
    return resultado;
  }

  private cargar<T>(r: Registro<T>): void {
    if (r.enVuelo) return;                               // deduplicación
    const generacion = r.generacion;
    r.estado.update((e) => ({ ...e, cargando: true }));
    const origen = r.buscar();
    const promesa = isObservable(origen) ? firstValueFrom(origen) : (origen as Promise<T>);
    r.enVuelo = promesa.then(
      (datos) => { if (generacion !== r.generacion) return; r.actualizadoEn = this.ahora(); r.estado.set({ datos, cargando: false, error: undefined, obsoleta: false }); },
      (error) => { if (generacion !== r.generacion) return; r.estado.update((e) => ({ ...e, cargando: false, error })); },
    ).finally(() => { if (generacion === r.generacion) r.enVuelo = undefined; });
  }
}
