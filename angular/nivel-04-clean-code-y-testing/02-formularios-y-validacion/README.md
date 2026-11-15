# 02 — Formularios y validación

Los formularios con señales (*Signal Forms*) son estables desde Angular 22 y son la opción recomendada para formularios nuevos. Los formularios reactivos siguen soportados y son los que vas a encontrar en casi todo el código existente, así que esta sesión enseña los dos.

## Idea

Un formulario es la frontera por donde entra todo lo que el usuario escribe. Bien hecho, guía, previene errores y no pierde datos. Mal hecho, es donde más se frustra la gente y donde se cuelan los datos inválidos.

## Lo esencial

### Tres formas de hacer formularios

| | Con señales | Reactivos | De plantilla |
|---|---|---|---|
| El modelo es | Una señal con un objeto tuyo | Un árbol de `FormGroup` y `FormControl` | Lo que declare la plantilla con `ngModel` |
| Las reglas van en | Un esquema, junto al formulario | Validadores en cada control | Directivas en la plantilla |
| Reactividad | Señales | Observables (`valueChanges`) | La de la plantilla |
| Tipos | Los de tu modelo, sin envoltorios | Tipados, con sus propios tipos | Débiles |
| Úsalo para | Formularios nuevos | Mantener lo existente | Un campo suelto, como mucho |

### Formularios con señales

Tres piezas:

1. **El modelo:** una señal con los datos, con la forma que tú decidas. Es la fuente de verdad.
2. **`form(modelo, esquema)`:** crea un árbol de campos con la misma forma que el modelo y le aplica las reglas del esquema.
3. **`[formField]`:** enlaza un campo del árbol con un control de la plantilla.

```ts
import { email, form, FormField, minLength, required } from '@angular/forms/signals';

protected readonly modelo = signal({ correo: '', clave: '' });

protected readonly formulario = form(this.modelo, (campos) => {
  required(campos.correo, { message: 'Escribe tu correo' });
  email(campos.correo, { message: 'El correo no tiene un formato válido' });
  minLength(campos.clave, 12, { message: 'La clave necesita al menos 12 caracteres' });
});
```

```html
<input type="email" [formField]="formulario.correo" />
```

Cada campo se llama como una función y devuelve su estado, que son señales:

| Señal | Qué dice |
|---|---|
| `value()` | El valor actual |
| `valid()` / `invalid()` | Si cumple las reglas |
| `errors()` | Lista de errores, cada uno con `kind` y `message` |
| `touched()` | Si el usuario entró y salió del campo |
| `dirty()` | Si el usuario lo cambió |
| `pending()` | Si hay una validación asíncrona en curso |
| `disabled()` / `readonly()` / `hidden()` | Estados que también se declaran en el esquema |

Lo mismo vale para el formulario entero: `formulario().valid()`.

### Reglas del esquema

| Necesidad | Herramienta |
|---|---|
| Obligatorio, formato, límites | `required`, `email`, `pattern`, `min`, `max`, `minLength`, `maxLength` |
| Una regla propia | `validate(campo, contexto => error o nada)` |
| Una regla que mira otro campo | `validate` leyendo el otro con `valueOf` |
| Una regla que solo aplica a veces | La opción `when` de cada regla, o `applyWhen` para un grupo |
| Reglas para cada elemento de una lista | `applyEach` con un esquema reutilizable |
| Consultar al servidor | `validateHttp`, con espera incorporada |
| Deshabilitar u ocultar según otros valores | `disabled`, `readonly`, `hidden` con `when` |

Una regla devuelve un objeto `{ kind, message }` cuando falla y nada cuando pasa. Si la lógica vive en una función pura que recibe valores y devuelve eso, se prueba sin Angular.

## Antes

```ts
@Component({ /* ... */ })
export class RegistroComponent {
  correo = '';
  clave = '';
  confirmacion = '';
  errorCorreo = '';
  errorClave = '';

  validarCorreo() {
    this.errorCorreo = this.correo.includes('@') ? '' : 'Correo inválido';
    this.http.get(`/api/usuarios/existe?correo=${this.correo}`)       // una petición por tecla
      .subscribe((existe) => { if (existe) this.errorCorreo = 'Ya está registrado'; });
  }

  enviar() {
    if (this.clave !== this.confirmacion) { alert('Las claves no coinciden'); return; }
    this.http.post('/api/usuarios', { correo: this.correo, clave: this.clave }).subscribe();
  }
}
```

```html
<input [(ngModel)]="correo" (keyup)="validarCorreo()" />
<span>{{ errorCorreo }}</span>
```

## Análisis

| Problema | Consecuencia |
|---|---|
| El estado del formulario está repartido en campos sueltos | Nada garantiza que errores y valores estén sincronizados |
| Validación escrita a mano en manejadores de eventos | Si el valor cambia por otra vía (autocompletado, pegar), no se valida |
| Una petición al servidor por tecla, sin cancelar | Carga inútil, y una respuesta vieja puede pisar a una reciente |
| El error se muestra desde la primera letra | El usuario ve "inválido" antes de terminar de escribir |
| `alert` y errores sin relación con el campo | Inaccesible para un lector de pantalla |
| Sin protección contra doble envío ni manejo del fallo del servidor | Usuarios duplicados y datos perdidos |

## Después

```ts
// registro.reglas.ts — puras: se prueban sin Angular
export interface Registro {
  correo: string;
  clave: string;
  confirmacion: string;
  tipoDocumento: 'cedula' | 'pasaporte';
  documento: string;
  contactos: Contacto[];
}
export interface Contacto { nombre: string; telefono: string; }

export const documentoValido = (tipo: Registro['tipoDocumento'], valor: string) => {
  const patron = tipo === 'cedula' ? /^\d{6,10}$/ : /^[A-Z0-9]{6,9}$/;
  return patron.test(valor)
    ? undefined
    : { kind: 'documento', message: tipo === 'cedula' ? 'Entre 6 y 10 dígitos' : 'Entre 6 y 9 letras o números' };
};

export const contactoEsquema = schema<Contacto>((contacto) => {
  required(contacto.nombre, { message: 'Escribe el nombre del contacto' });
  pattern(contacto.telefono, /^\d{10}$/, { message: 'El teléfono tiene 10 dígitos' });
});
```

```ts
// registro.component.ts
@Component({
  selector: 'app-registro',
  imports: [FormField],
  templateUrl: './registro.component.html',
})
export class RegistroComponent {
  private readonly usuarios = inject(UsuariosApi);

  protected readonly modelo = signal<Registro>({
    correo: '', clave: '', confirmacion: '', tipoDocumento: 'cedula', documento: '',
    contactos: [{ nombre: '', telefono: '' }],
  });

  protected readonly formulario = form(this.modelo, (r) => {
    required(r.correo, { message: 'Escribe tu correo' });
    email(r.correo, { message: 'El correo no tiene un formato válido' });
    validateHttp(r.correo, {                                    // solo corre si las reglas anteriores pasan
      debounce: 400,
      request: ({ value }) => (value() ? `/api/usuarios/existe?correo=${encodeURIComponent(value())}` : undefined),
      onSuccess: (respuesta: { existe: boolean }) =>
        respuesta.existe ? { kind: 'correoEnUso', message: 'Ese correo ya está registrado' } : undefined,
      onError: () => undefined,                                 // si el servicio falla, no bloquea al usuario
    });

    minLength(r.clave, 12, { message: 'La clave necesita al menos 12 caracteres' });
    validate(r.confirmacion, ({ value, valueOf }) =>
      value() === valueOf(r.clave) ? undefined : { kind: 'clavesDistintas', message: 'Las claves no coinciden' });

    validate(r.documento, ({ value, valueOf }) => documentoValido(valueOf(r.tipoDocumento), value()));
    applyEach(r.contactos, contactoEsquema);
  });

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal('');

  protected agregarContacto(): void {
    this.modelo.update((m) => ({ ...m, contactos: [...m.contactos, { nombre: '', telefono: '' }] }));
  }

  protected async enviar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (this.enviando()) return;                                // sin doble envío
    if (this.formulario().invalid()) {
      this.formulario().markAsTouched();                        // muestra todos los errores
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set('');
    try {
      await this.usuarios.registrar(this.modelo());
    } catch {
      this.errorGeneral.set('No pudimos registrarte. Tus datos siguen aquí; intenta de nuevo.');
    } finally {
      this.enviando.set(false);
    }
  }
}
```

```html
<form (submit)="enviar($event)" novalidate>
  <label for="correo">Correo</label>
  <input id="correo" type="email" autocomplete="email" [formField]="formulario.correo"
         [attr.aria-invalid]="formulario.correo().touched() && formulario.correo().invalid()"
         aria-describedby="correo-error" />
  <p id="correo-error" role="alert">
    @if (formulario.correo().touched() && formulario.correo().invalid()) {
      {{ formulario.correo().errors()[0].message }}
    }
  </p>

  @for (contacto of formulario.contactos; track $index) {
    <fieldset>
      <legend>Contacto de emergencia {{ $index + 1 }}</legend>
      <label>Nombre <input [formField]="contacto.nombre" /></label>
      <label>Teléfono <input inputmode="numeric" [formField]="contacto.telefono" /></label>
    </fieldset>
  }
  <button type="button" (click)="agregarContacto()" [disabled]="modelo().contactos.length >= 3">Agregar contacto</button>

  @if (errorGeneral()) { <p role="alert">{{ errorGeneral() }}</p> }
  <button type="submit" [disabled]="enviando()">{{ enviando() ? 'Enviando…' : 'Registrarme' }}</button>
</form>
```

- El modelo es un objeto tuyo. No hay que "sacar el valor" del formulario: `this.modelo()` ya es lo que se envía.
- Las reglas están juntas y se leen de arriba abajo.
- La validación contra el servidor trae la espera incorporada y solo corre cuando el formato es válido.
- Añadir un contacto es añadir un elemento a la lista del modelo.
- La regla del documento es una función pura.

La API de formularios con señales tiene más piezas que las mostradas, entre ellas una función `submit` y una directiva para el elemento `<form>` que se encargan del envío y de devolver errores del servidor a un campo concreto. Los nombres y las opciones se han ido afinando entre versiones: tómalos de la guía oficial (angular.dev, sección de formularios con señales) y no de este ejemplo, que es ilustrativo y no se compiló.

## Formularios reactivos: lo que vas a mantener

```ts
protected readonly formulario = inject(NonNullableFormBuilder).group({
  correo: ['', [Validators.required, Validators.email], [correoDisponible(this.usuarios)]],
  clave: ['', [Validators.required, Validators.minLength(12)]],
  confirmacion: ['', Validators.required],
}, { validators: [clavesIguales] });

export const clavesIguales: ValidatorFn = (grupo) =>
  grupo.get('clave')?.value === grupo.get('confirmacion')?.value ? null : { clavesDistintas: true };

export function correoDisponible(api: UsuariosApi): AsyncValidatorFn {
  return (control) => timer(400).pipe(                         // espera a que deje de escribir
    switchMap(() => api.existeCorreo(control.value)),
    map((existe) => (existe ? { correoEnUso: true } : null)),
    catchError(() => of(null)),
  );
}
```

Equivalencias para leer uno sabiendo el otro:

| Reactivos | Con señales |
|---|---|
| `FormGroup` / `FormControl` | Un objeto en una señal y `form()` |
| `Validators.required` en el control | `required(campo)` en el esquema |
| Validador de grupo | `validate` leyendo otro campo con `valueOf` |
| `AsyncValidatorFn` con `timer` y `switchMap` | `validateHttp` |
| `FormArray` | Una lista en el modelo y `applyEach` |
| `valueChanges` | `computed` o `effect` sobre el modelo |
| `formControlName` | `[formField]` |
| `ControlValueAccessor` | Un componente con un `model()` llamado `value` |
| `getRawValue()` | El modelo ya lo tiene todo |

Los dos sistemas pueden convivir en una aplicación mientras se migra, formulario por formulario. No hay razón para reescribir uno que funciona.

## Experiencia de uso

- Mostrar el error cuando el campo fue **tocado** o tras intentar enviar, no mientras se escribe la primera letra.
- Mensajes que digan cómo corregir, no solo "inválido".
- Deshabilitar el envío mientras hay una petición en curso; no deshabilitarlo por estar inválido sin explicar por qué.
- Conservar lo escrito ante un error del servidor.
- Mostrar los errores que devuelve el servidor en el campo correspondiente.
- Advertir antes de salir con cambios sin guardar (`canDeactivate`).

## Accesibilidad

Cada campo con su `<label>` asociado; errores vinculados con `aria-describedby` y anunciados (`role="alert"` o `aria-live`); `aria-invalid` en campos con error; foco al primer campo con error tras enviar; `autocomplete` apropiado.

## La regla que no cambia

**La validación del frontend es experiencia de usuario. La validación real ocurre en el servidor.** Todo lo que valida el navegador se puede saltar.

## Errores comunes

- Validar solo en el cliente.
- Errores visibles desde el primer instante.
- Validación asíncrona sin espera ni cancelación: una petición por tecla.
- Reglas escritas dentro del componente en lugar de funciones puras.
- Copiar el modelo a otra señal "para trabajar" y tener dos fuentes de verdad.
- Modificar el objeto del modelo en el sitio: la señal no cambia.
- En reactivos: enviar `formulario.value` cuando hay controles deshabilitados (no se incluyen).
- No contemplar el doble envío.
- Migrar por moda un formulario reactivo que funciona y tiene pruebas.

## Taller

Construye el formulario de registro de una clínica con formularios con señales:

1. Datos personales, documento según tipo (la regla cambia con el tipo), correo, clave con confirmación y fecha de nacimiento (mayor de edad).
2. Una lista de contactos de emergencia: mínimo uno, máximo tres, con sus propias reglas.
3. Un campo que solo existe y solo es obligatorio según otro (por ejemplo, el nombre del acudiente si es menor de edad).
4. Comprobación de correo contra el servidor, con espera, y que no bloquee si el servidor falla.
5. Mensajes de error accesibles y foco en el primer error al enviar.
6. Errores del servidor asignados al campo correspondiente. Averigua en la guía oficial cómo se hace y úsalo.
7. Guarda de cambios sin guardar.
8. Pruebas de las reglas como funciones puras, sin `TestBed`.
9. Escribe el mismo formulario, sin los contactos, con formularios reactivos. Compara cantidad de código, tipos y facilidad de prueba, y deja escrito cuándo migrarías un formulario existente y cuándo no.

Rama `taller/ng-n4-02-formularios`.

## Preguntas senior

1. ¿Qué cambia de fondo entre un formulario reactivo y uno con señales?
2. ¿Dónde va una validación que involucra dos campos, en cada sistema?
3. ¿Cómo validas contra el servidor sin saturarlo?
4. ¿Por qué la validación del cliente no es suficiente?
5. ¿Qué hace accesible a un formulario?
6. Tu aplicación tiene ochenta formularios reactivos. ¿Cuál es tu plan?
