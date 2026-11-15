# 04 — Observer con RxJS

RxJS es el patrón Observer llevado al extremo: flujos que se pueden transformar, combinar y cancelar.

## El problema

Los eventos de una interfaz no llegan ordenados ni de uno en uno: el usuario escribe rápido, hace doble clic, navega antes de que termine una petición, el servidor responde tarde o falla. Manejar eso con banderas y temporizadores a mano produce errores sutiles.

## Lo esencial

- **Observable:** fuente de valores en el tiempo. No hace nada hasta que alguien se suscribe (es perezoso).
- **Suscripción:** la conexión; cancelarla detiene el trabajo (incluida la petición HTTP en curso).
- **Frío o caliente:** un observable frío ejecuta su lógica por cada suscriptor (una petición HTTP por suscripción); uno caliente comparte la ejecución (`Subject`, `shareReplay`).
- **Subject:** observable al que además se le pueden enviar valores. `BehaviorSubject` recuerda el último.

### Elegir el operador de aplanamiento

| Situación | Operador | Por qué |
|---|---|---|
| Búsqueda mientras se escribe, cambio de pestaña | `switchMap` | Solo importa lo último; cancela lo anterior |
| Guardar cambios en orden | `concatMap` | Uno tras otro |
| Cargas independientes | `mergeMap` | En paralelo, sin orden |
| Botón de enviar | `exhaustMap` | Ignora los clics mientras hay uno en curso |

### Combinar

| Operador | Emite |
|---|---|
| `combineLatest` | Cada vez que cambia cualquiera, con el último de todos |
| `forkJoin` | Una vez, cuando todos terminan (el `Promise.all` de RxJS) |
| `withLatestFrom` | Cuando emite el principal, con el último de los otros |
| `merge` | Lo que llegue de cualquiera |

## Ejemplo: autoguardado

```ts
@Component({ /* ... */ })
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
```

Cada línea resuelve un problema real: frecuencia, duplicados, orden, fallos transitorios, error sin matar el flujo y limpieza al destruir.

## Manejo de errores

Un error termina el observable. Si `catchError` está en el flujo exterior, tras el primer fallo ya no se procesan más eventos. Va dentro del flujo interno (el del `concatMap` o `switchMap`), como en el ejemplo.

## Cancelar suscripciones

De mejor a peor: `toSignal` · tubería `async` · `takeUntilDestroyed()` · guardar la suscripción y cancelarla a mano. Las peticiones HTTP se completan solas, pero cancelarlas al salir evita trabajo inútil y actualizaciones sobre un componente destruido.

## Alternativas

- **Señales** para el estado y los valores derivados.
- **`async`/`await`** para una operación única sin cancelación.
- **`resource` y `httpResource`** (API reciente de Angular; revisa su estado en tu versión) para cargar datos asíncronos en función de señales.

## Errores comunes

- Suscripciones anidadas.
- Operador de aplanamiento equivocado.
- `catchError` en el flujo exterior.
- `shareReplay` sin `refCount`, que retiene la suscripción para siempre.
- `Subject` públicos que cualquiera puede emitir: expón `asObservable()`.
- Usar RxJS para estado que una señal resuelve mejor.
- Cadenas de veinte operadores sin nombre: extrae funciones.

## Taller

1. Implementa el autoguardado del ejemplo con una API simulada que falla al azar.
2. Implementa un formulario con envío protegido por `exhaustMap`.
3. Carga un tablero con tres peticiones en paralelo (`forkJoin`) y luego haz que una sea opcional: si falla, el tablero se muestra sin ella.
4. Demuestra el error de `catchError` en el flujo exterior.
5. Implementa sondeo cada 10 segundos que se detenga al salir de la pantalla y al perder el foco la pestaña.
6. Escribe una prueba con tiempo simulado para `debounceTime`.

Rama `taller/ng-n3-04-rxjs`.

## Preguntas senior

1. Observable frío frente a caliente, con un error que cause cada confusión.
2. ¿Dónde se coloca `catchError` y por qué?
3. ¿Cómo eliges entre los cuatro operadores de aplanamiento?
4. ¿Cuándo prefieres señales, promesas u observables?
5. ¿Cómo pruebas código dependiente del tiempo?
