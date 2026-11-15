# 02 — Formularios y validación

## Idea

Un formulario es la frontera por donde entra todo lo que el usuario escribe. Bien hecho, guía, previene errores y no pierde datos. Mal hecho, es donde más se frustra la gente y donde se cuelan los datos inválidos.

## Lo esencial

### Dos enfoques consolidados y uno nuevo

| | Formularios reactivos | Formularios de plantilla |
|---|---|---|
| El modelo se define en | La clase (`FormGroup`, `FormControl`) | La plantilla (`ngModel`) |
| Validación | Funciones en la clase | Directivas en la plantilla |
| Pruebas | Sin interfaz | Requieren la plantilla |
| Conviene para | Formularios medianos y grandes, dinámicos | Formularios muy simples |

Angular ha incorporado además formularios basados en señales (*Signal Forms*). Revisa su estado en tu versión antes de adoptarlos en un proyecto; los conceptos de esta sesión aplican igual.

### Formularios reactivos tipados

```ts
protected readonly formulario = inject(NonNullableFormBuilder).group({
  correo: ['', [Validators.required, Validators.email]],
  documento: ['', [Validators.required, documentoValido()]],
  clave: ['', [Validators.required, Validators.minLength(12)]],
  confirmacion: ['', Validators.required],
}, { validators: [clavesIguales] });
```

`NonNullableFormBuilder` produce controles tipados cuyo valor no pasa a `null` al reiniciar.

### Tipos de validador

| Tipo | Ejemplo | Dónde |
|---|---|---|
| De control, síncrono | Formato de documento | En el control |
| De grupo | Dos campos que deben coincidir; rango de fechas | En el grupo |
| Asíncrono | ¿El correo ya está registrado? | Tercer argumento; con espera y cancelación |

```ts
export function documentoValido(): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null =>
    /^\d{6,10}$/.test(control.value) ? null : { documento: 'Debe tener entre 6 y 10 dígitos' };
}

export const clavesIguales: ValidatorFn = (grupo) =>
  grupo.get('clave')?.value === grupo.get('confirmacion')?.value ? null : { clavesDistintas: true };

export function correoDisponible(api: UsuariosApi): AsyncValidatorFn {
  return (control) => timer(400).pipe(                       // espera a que deje de escribir
    switchMap(() => api.existeCorreo(control.value)),
    map((existe) => (existe ? { correoEnUso: true } : null)),
    catchError(() => of(null)),                              // si el servicio falla, no bloquea
  );
}
```

Los validadores son funciones puras: se prueban sin Angular.

### Experiencia de uso

- Mostrar el error cuando el campo fue **tocado** o tras intentar enviar, no mientras se escribe la primera letra.
- Mensajes que digan cómo corregir, no solo "inválido".
- Deshabilitar el envío mientras hay una petición en curso; no deshabilitarlo por estar inválido sin explicar por qué.
- Conservar lo escrito ante un error del servidor.
- Mostrar los errores que devuelve el servidor en el campo correspondiente.
- Advertir antes de salir con cambios sin guardar (`canDeactivate`).

### Accesibilidad

Cada campo con su `<label>` asociado; errores vinculados con `aria-describedby` y anunciados (`role="alert"` o `aria-live`); `aria-invalid` en campos con error; foco al primer campo con error tras enviar; `autocomplete` apropiado.

### La regla que no cambia

**La validación del frontend es experiencia de usuario. La validación real ocurre en el servidor.** Todo lo que valida el navegador se puede saltar.

## Errores comunes

- Formularios grandes con `ngModel` y lógica en la plantilla.
- Validar solo en el cliente.
- Errores visibles desde el primer instante.
- Validadores asíncronos sin espera ni cancelación: una petición por tecla.
- Un control propio sin `ControlValueAccessor`, con valores sincronizados a mano.
- Formularios sin tipo (`FormGroup` con `any`).
- Enviar `formulario.value` cuando hay controles deshabilitados (no se incluyen; usa `getRawValue()` si los necesitas).
- No contemplar el doble envío.

## Taller

Construye el formulario de registro de una clínica:

1. Datos personales, documento según tipo (la validación cambia con el tipo), correo, clave con confirmación y fecha de nacimiento (mayor de edad).
2. Una lista dinámica de contactos de emergencia (`FormArray`): mínimo uno, máximo tres.
3. Un validador asíncrono de correo con espera y cancelación.
4. Mensajes de error accesibles y foco en el primer error al enviar.
5. Manejo de errores del servidor asignados al campo correspondiente.
6. Guarda de cambios sin guardar.
7. Pruebas de los validadores sin `TestBed`.

Rama `taller/ng-n4-02-formularios`.

## Preguntas senior

1. Reactivos frente a de plantilla: criterios de elección.
2. ¿Dónde va una validación que involucra dos campos?
3. ¿Cómo implementas un validador asíncrono sin saturar el servidor?
4. ¿Por qué la validación del cliente no es suficiente?
5. ¿Qué hace accesible a un formulario?
