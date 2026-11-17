import { catchError, debounceTime, distinctUntilChanged, EMPTY, exhaustMap, mergeMap, Observable, of, OperatorFunction, switchMap } from 'rxjs';

type Aplanador = <A, B>(fn: (a: A) => Observable<B>) => OperatorFunction<A, B>;

/** 3 y 5. El mismo buscador con el operador de aplanamiento como parámetro: switchMap (correcto) o mergeMap (carrera). */
export const buscador = <T>(buscar: (texto: string) => Observable<T[]>, aplanar: Aplanador = switchMap, alFallar: T[] = []) =>
  (textos: Observable<string>): Observable<T[]> => textos.pipe(
    debounceTime(300),
    distinctUntilChanged(),
    // catchError DENTRO del flujo interno: un fallo da una lista vacía y el buscador sigue vivo (fuera, lo mataría).
    aplanar((texto: string) => (texto.length < 2 ? of([] as T[]) : buscar(texto).pipe(catchError(() => of(alFallar))))),
  );

/** 4. Guardar sin doble envío: los clics que llegan mientras hay un guardado en curso se ignoran. */
export const guardarSinDobleEnvio = <T>(guardar: () => Observable<T>, alFallar?: (error: unknown) => void) =>
  (clics: Observable<void>): Observable<T> => clics.pipe(exhaustMap(() => guardar().pipe(
    // catchError dentro: un guardado fallido se avisa y el botón sigue funcionando para el siguiente clic.
    catchError((error: unknown) => { alFallar?.(error); return EMPTY; }),
  )));

export const OPERADORES = { switchMap, mergeMap } as const;
