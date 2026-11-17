# Taller n1-03 — Componente de paginación

| # | Solución | Prueba |
|---|---|---|
| 1 | `paginacion.component.ts`: `total = input.required<number>()`, `tamano = input(10, { transform: numberAttribute })`, `pagina = model(1)` | `taller.spec.ts` 1-2 |
| 2 | `paginas`, `paginaValida`, `hayAnterior`, `haySiguiente`, `texto` son `computed` | 1-2 |
| 3 | Corrección automática (ver abajo) | 3 |
| 4 | Botón "Ir a la primera": `pagina.set(1)` y `irAPrimera.emit()`. La lista enfoca su primer `<li>` con `viewChildren('elemento')` dentro de `afterNextRender` | 4 y E2E |
| 5 | `@let actual = paginaValida()` en la plantilla de la paginación evita repetir la lectura en tres enlaces | — |
| 6 | `paginacion-decoradores.component.ts` | 6 |
| 7 | `migracion/`: esquemas oficiales aplicados por `migracion/ejecutar-migracion.sh` (se ejecuta también con `./verificar.sh`) | 7 |

## 3. Con qué herramienta se corrige la página

La página es un `model`: su dueño es el padre. Hay dos necesidades distintas:

- **Lo que se ve** nunca debe ser una página inexistente. Eso es estado derivado: `paginaValida = computed(() => acotarPagina(pagina(), paginas()))`. Se corrige en el mismo instante en que cambia `total`, sin esperar a ningún ciclo.
- **El padre** también tiene que enterarse, o su señal `pagina` seguiría diciendo 7 cuando solo hay 3 páginas. `computed` y `linkedSignal` no pueden escribir en el padre; la única forma es `pagina.set()`. Por eso hay un `effect` que, cuando `paginaValida` difiere de `pagina`, escribe el modelo. Es un efecto que sincroniza con un dueño externo (el padre), no un efecto que deriva estado local.

**Un error que encontró la prueba E2E.** La primera versión del efecto leía solo `paginaValida` (y `pagina` con `untracked`). En el navegador fallaba: con la página en 2, clic en "Siguiente" (página pedida 3) y, antes de la siguiente detección de cambios, bajar el total a 6 (dos páginas). `paginaValida` pasó de 2 a 2 sin que nadie leyera el 3 intermedio; para el grafo de señales no cambió, el efecto no se ejecutó y el padre se quedó con la página 3, que no existe. Las señales no tienen parpadeos (*glitch-free*): un efecto ve valores, no la historia de cambios. La corrección es leer también la señal que hay que corregir (`pagina()`): el efecto se ejecuta siempre que cambia la página pedida o la válida. La prueba unitaria "3b" reproduce el caso.

`linkedSignal` sería la herramienta si la página fuera estado **local** del componente (sin `[(pagina)]`): `linkedSignal(() => acotar(...))` se reiniciaría solo al cambiar el total.

## 5. Dónde no se usó `@let`

En `lista-paginada.component.ts` el rango de elementos visibles (`desde`, `hasta`, longitud) se calcula en un `computed` de la clase, no con `@let`: tiene lógica (recortes en el último tramo) y debe poder probarse.

## 6. Decoradores frente a señales: qué errores permite cada versión

| Error | Decoradores | Señales |
|---|---|---|
| `tamano="5"` llega como texto | Lo permite: el tipo es `number \| string` y hay que acordarse de convertir | `numberAttribute` lo convierte al entrar |
| Escribir una entrada desde dentro (`this.pagina = 3`) | Lo permite y no avisa al padre si no se emite a mano | `input()` es de solo lectura: no compila. `model.set` avisa solo |
| Estado derivado desactualizado | `texto` se recalcula en `ngOnChanges` y en `cambiar`; si se olvida un sitio, miente (la prueba 6 lo muestra) | `computed`: imposible que se desfase |
| Entrada obligatoria ausente | `@Input({ required: true })` también lo detecta al compilar, pero el `!` en la clase sigue siendo una promesa | `input.required` lo detecta y el tipo no necesita `!` |
| Consulta usada antes de existir | `@ViewChild` es `undefined` hasta `ngAfterViewInit` y el tipo no lo refleja salvo que se declare `?` | `viewChild.required` lanza un error claro; `viewChild()` tipa `undefined` |

## 7. Migración con los esquemas de Angular

Esquemas de `@angular/core` 22.2.6 que aplican: `signal-input-migration`, `output-migration`, `signal-queries-migration`, y el combinado `signals`. `migracion/ejecutar-migracion.sh` (también lo llama `./verificar.sh`) copia `contador-antes.component.ts` a una carpeta temporal dentro de `migracion/`, ejecuta lo siguiente sobre la copia y comprueba que la salida coincide, salvo espacios y los nombres de clase y selector, con `resultado/contador-migrado.component.ts`:

```
ng g @angular/core:signals --path <carpeta> --migrations inputs --migrations outputs --migrations queries --defaults
```

Lo que produjo (salida real):

- `@Input() valor = 0` → `readonly valor = input(0)`, y las lecturas → `this.valor()`.
- `@Output() valorChange = new EventEmitter<number>()` → `output<number>()`.
- `@ViewChild('parrafo')` → `viewChild<ElementRef<HTMLParagraphElement>>('parrafo')`.
- **Lo que no hizo:** dejó `ngOnChanges` y el campo `etiqueta` tal cual, ahora leyendo `this.valor()`. Funciona, pero conserva el problema de fondo: el estado derivado se mantiene a mano. La revisión manual (`contador-a-mano.component.ts`) lo reemplaza por `etiqueta = computed(...)` y elimina `ngOnChanges`. Además informó "Migrated 1/3 queries" en el proyecto: las otras dos consultas con decorador del proyecto (`contador-antes.component.ts` y `paginacion-decoradores.component.ts`) quedaban fuera de la carpeta indicada y se dejaron como estaban.
