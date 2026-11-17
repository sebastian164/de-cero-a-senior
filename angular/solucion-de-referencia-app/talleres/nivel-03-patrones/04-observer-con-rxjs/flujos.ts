import {
  catchError, concatMap, debounceTime, distinctUntilChanged, exhaustMap, filter, forkJoin, fromEvent, map, merge, Observable,
  of, retry, startWith, switchMap, timer, OperatorFunction,
} from 'rxjs';

export type EstadoGuardado = 'sin cambios' | 'guardando' | 'guardado' | 'error';

/** 1. Autoguardado como función: recibe los cambios y cómo guardar; el tiempo y los reintentos son parámetros. */
export const autoguardado = <T>(guardar: (v: T) => Observable<unknown>, opciones = { espera: 800, reintentos: 2, pausa: 1000 }) =>
  (cambios: Observable<T>): Observable<EstadoGuardado> => cambios.pipe(
    debounceTime(opciones.espera),
    distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    concatMap((valor) => guardar(valor).pipe(
      retry({ count: opciones.reintentos, delay: opciones.pausa }),
      map(() => 'guardado' as const),
      catchError(() => of('error' as const)),
      startWith('guardando' as const),
    )),
  );

/**
 * 2. Envío protegido: los envíos mientras hay uno en curso se ignoran (exhaustMap). El error se maneja DENTRO del flujo
 *    interno: un envío fallido emite 'error' y el flujo exterior sigue vivo, de modo que el siguiente envío vuelve a intentarlo.
 */
export type EstadoEnvio = 'enviando' | 'enviado' | 'error';
export const envioProtegido = (enviar: () => Observable<unknown>): OperatorFunction<unknown, EstadoEnvio> =>
  exhaustMap(() => enviar().pipe(
    map(() => 'enviado' as const),
    catchError(() => of('error' as const)),
    startWith('enviando' as const),
  ));

/** 3. Tablero: tres peticiones en paralelo; la de recomendaciones es opcional. */
export interface Tablero { ventas: number; clientesNuevos: number; recomendaciones: string[] | null; }
export const cargarTablero = (api: { ventas(): Observable<number>; clientes(): Observable<number>; recomendaciones(): Observable<string[]> }) =>
  forkJoin({
    ventas: api.ventas(),
    clientesNuevos: api.clientes(),
    recomendaciones: api.recomendaciones().pipe(catchError(() => of(null))),   // si falla, el tablero se muestra sin ella
  });

/** Lo mismo con estado para la pantalla: si fallan ventas o clientes (las obligatorias) hay un estado de error, no una espera eterna. */
export type EstadoTablero = { estado: 'cargando' } | { estado: 'listo'; tablero: Tablero } | { estado: 'error' };
export const estadoTablero = (api: Parameters<typeof cargarTablero>[0]): Observable<EstadoTablero> =>
  cargarTablero(api).pipe(
    map((tablero) => ({ estado: 'listo', tablero }) as const),
    catchError(() => of({ estado: 'error' } as const)),
    startWith({ estado: 'cargando' } as const),
  );

/** 4. El error de catchError en el flujo exterior: tras el primer fallo, el flujo termina. */
export const busquedaConCatchErrorAfuera = <T>(buscar: (q: string) => Observable<T>, alFallar: T) =>
  (textos: Observable<string>) => textos.pipe(switchMap((q) => buscar(q)), catchError(() => of(alFallar)));
export const busquedaConCatchErrorAdentro = <T>(buscar: (q: string) => Observable<T>, alFallar: T) =>
  (textos: Observable<string>) => textos.pipe(switchMap((q) => buscar(q).pipe(catchError(() => of(alFallar)))));

/**
 * 5. Sondeo cada `periodo` ms mientras la pestaña está visible. Se detiene al ocultarla y vuelve al mostrarla.
 *    Detenerse al salir de la pantalla lo da takeUntilDestroyed() en el componente.
 */
export type ResultadoSondeo<T> = { estado: 'ok'; valor: T } | { estado: 'error' };
export const sondeo = <T>(consultar: () => Observable<T>, visible: Observable<boolean>, periodo = 10_000): Observable<ResultadoSondeo<T>> =>
  visible.pipe(
    distinctUntilChanged(),
    switchMap((v) => (v
      // catchError DENTRO de concatMap: una consulta fallida emite 'error' y el sondeo sigue con la siguiente.
      ? timer(0, periodo).pipe(concatMap(() => consultar().pipe(
          map((valor) => ({ estado: 'ok', valor }) as const),
          catchError(() => of({ estado: 'error' } as const)),
        )))
      : [])),
  );

export const visibilidadDe = (doc: Document): Observable<boolean> =>
  merge(fromEvent(doc, 'visibilitychange'), of(null)).pipe(map(() => doc.visibilityState === 'visible'));

/** 6. Búsqueda con espera, para probarla con tiempo simulado. */
export const conEspera = (ms: number) => (textos: Observable<string>) => textos.pipe(debounceTime(ms), filter((t) => t.length > 0));
