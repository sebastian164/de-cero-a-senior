# Taller n4-02 — Formularios y validación (clínica)

Versión: `@angular/forms` 22.2.6. Solución: `clinica.component.ts` (señales), `clinica.reglas.ts` (reglas puras), `reactivo.component.ts` (punto 9).

| # | Solución | Prueba |
|---|---|---|
| 1 | Documento según tipo (`validate` + `valueOf(tipoDocumento)` → `documentoValido`), correo, clave con confirmación, fecha de nacimiento (`fechaNacimientoValida`; mayor/menor de edad con `edadEn`) | `clinica.reglas.spec.ts`; E2E |
| 2 | `contactos`: `applyEach(contactoEsquema)` + `validate(contactos, cantidadContactosValida)` (1 a 3); "Quitar" se deshabilita con uno y "Agregar" con tres | spec "contactos: mínimo uno, máximo tres" |
| 3 | `acudiente`: `hidden(...)` y `required(..., { when })` según `esMenorDeEdad` | spec "el acudiente solo existe y es obligatorio para menores" |
| 4 | `validateHttp` con `debounce: 400` y `onError: () => undefined` | spec "si el servicio ... falla, no bloquea"; E2E: una sola consulta al escribir un correo de golpe |
| 5 | Mensaje por campo con `role="alert"`, `aria-invalid`, `aria-describedby`; al enviar inválido, `submit({ onInvalid })` llama `errorSummary()[0].fieldTree().focusBoundControl()` | spec "enviar inválido ... foco en el primer error" |
| 6 | `submit(formulario, { action })`: el servidor responde 422 con `{ campo, mensaje }`; la acción devuelve `[{ fieldTree: f[campo], kind: 'servidor', message }]` y el error aparece en el campo indicado (correo o documento; si el campo no se conoce, queda un error general) | spec del 422 (correo y, aparte, documento) y E2E |
| 7 | `canDeactivate: [sinGuardar]`; compara el modelo con el inicial (`haySinGuardar`) y usa el token `CONFIRMAR_SALIDA` (ver "Por qué no `dirty()`") | spec de la guarda y E2E (diálogo) |
| 8 | Reglas puras sin `TestBed` | `clinica.reglas.spec.ts` |
| 9 | Versión reactiva (sin contactos) | spec "versión reactiva" |

## Decisiones

- **"Mayor de edad" (punto 1).** El enunciado pide la fecha de nacimiento y el punto 3 un acudiente "si es menor de edad"; ambos solo son compatibles si los menores pueden registrarse con acudiente. La solución valida que la fecha sea una fecha real y no futura, y los menores quedan obligados a dar acudiente. Se aclaró el README ("fecha de nacimiento válida; si es menor de edad, aplica el punto 3") y se quitó `esMayorDeEdad`, que no se usaba.

## Por qué no `dirty()` en la guarda

Medido en Chromium con `@angular/forms` 22.2.6 (build de desarrollo, leyendo `formulario().dirty()` con `ng.getComponent` en la consola): nada más abrir el formulario, `formulario().dirty()` es `true`, y el único campo con `dirty()` verdadero es `fechaNacimiento`, el `input type="date"`, que escribe su valor al enlazarse. Una guarda basada en `dirty()` preguntaría siempre. Por eso `haySinGuardar` compara el modelo con el inicial.

## Cómo se averiguó lo que el README manda a consultar (punto 6)

La API se leyó en los tipos instalados, no en el README: `submit(form, { action, onInvalid })` (action devuelve errores con `fieldTree` de destino), `FieldState.errorSummary()`, `focusBoundControl()`, `markAsTouched()`. En la versión 22.2.6 estos tipos son `@publicApi 22.0`; `FormRoot` (directiva `form[formRoot]`) también existe, y se prefirió `(submit)` + `submit()` por ser lo que muestra el README.

## Punto 9: reactivos frente a señales

Líneas (código TypeScript del componente, incluida su plantilla en línea): señales 172 (con 10 campos, contactos, foco, error del servidor, guarda y accesibilidad completa); reactivo 53, pero con solo tres campos visibles en la plantilla y sin contactos, foco, error del servidor ni guarda: no son comparables en bruto. Comparable de verdad:

| Aspecto | Señales | Reactivos |
|---|---|---|
| Reglas entre campos | `validate(campo, ({ valueOf }) => ...)` junto al campo que falla; el error cae en el campo | Validador de **grupo**: el error cuelga del grupo (`g.errors`) y la plantilla debe leerlo allí |
| Tipos | El modelo es `Paciente`; `modelo()` es lo que se envía | Tipado con `NonNullableFormBuilder`, pero `g.get('clave')?.value` no está tipado y exige `?`/casts |
| Validación asíncrona | `validateHttp` (espera y cancelación incluidas, solo corre si lo síncrono pasa) | `AsyncValidatorFn` con `timer` + `switchMap`: hay que escribirlo |
| Reutilizar reglas | Las funciones puras sirven a ambos (`aError` adapta `{kind, message}` a `ValidationErrors`) | Idem, con un adaptador |
| Prueba | Reglas puras sin Angular; el componente se prueba con `TestBed` | Los validadores de grupo se prueban sin render con `FormGroup` (spec) |

**Cuándo migrar:** un formulario reactivo con pruebas que funciona no se migra por moda; se migra cuando se va a tocar mucho (reglas condicionales entre campos, listas dinámicas, errores del servidor por campo) porque ahí es donde el árbol de `FormGroup` pesa. Los formularios nuevos nacen con señales. Conviven sin problema mientras tanto.
