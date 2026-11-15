# 03 — LSP en Angular

Explicación completa: [LSP en la ruta Java](../../../java/nivel-2-solid/03-lsp/).

## Idea

Si un componente o servicio se presenta como reemplazo de otro, quien lo usa no debería notar la diferencia. Un botón "especial" que ignora `disabled`, o un servicio falso de pruebas que nunca falla, rompen esa promesa.

## Lo esencial

En Angular la sustitución aparece en cuatro lugares:

| Lugar | La promesa que debe cumplirse |
|---|---|
| Implementaciones de una interfaz o de una clase abstracta inyectada | Mismo contrato: mismos errores, mismos estados, mismo significado de cada método |
| Componentes intercambiables (variantes de un botón, de un campo) | Mismas entradas y salidas con el mismo significado; mismo comportamiento de accesibilidad |
| Controles de formulario propios (`ControlValueAccessor`) | Comportarse como un control nativo: valor, deshabilitado, tocado |
| Dobles de prueba | Comportarse como el real en lo que la prueba depende |

Como TypeScript es estructural, el compilador acepta cualquier objeto con la forma correcta. La forma no garantiza el comportamiento.

## Antes

```ts
export abstract class Almacenamiento {
  abstract guardar(clave: string, valor: string): void;
  abstract leer(clave: string): string | null;
  abstract eliminar(clave: string): void;
}

@Injectable()
export class AlmacenamientoLocal extends Almacenamiento { /* usa localStorage */ }

@Injectable()
export class AlmacenamientoSoloLectura extends Almacenamiento {
  guardar(): void { /* no hace nada, en silencio */ }
  leer(clave: string) { return CONFIGURACION[clave] ?? null; }
  eliminar(): void { throw new Error('No soportado'); }
}

// El cliente guarda la preferencia, cree que quedó guardada y se pierde.
```

## Después

```ts
export abstract class Lector {
  abstract leer(clave: string): string | null;
}

export abstract class Almacenamiento extends Lector {
  abstract guardar(clave: string, valor: string): void;
  abstract eliminar(clave: string): void;
}

@Injectable() export class AlmacenamientoLocal extends Almacenamiento { /* ... */ }
@Injectable() export class ConfiguracionFija extends Lector { /* solo lee */ }

// Quien solo necesita leer inyecta Lector; quien necesita escribir inyecta Almacenamiento.
// Pedir escritura a ConfiguracionFija ya no compila.
```

Un doble de prueba que respeta el contrato:

```ts
class AlmacenamientoEnMemoria extends Almacenamiento {
  private readonly datos = new Map<string, string>();
  guardar(clave: string, valor: string) { this.datos.set(clave, valor); }
  leer(clave: string) { return this.datos.get(clave) ?? null; }   // null si no existe, igual que el real
  eliminar(clave: string) { this.datos.delete(clave); }
}
```

## Componentes sustituibles

Un `app-boton-primario` que reemplaza a `<button>` debe: reenviar `disabled`, `type` y los atributos ARIA; ser accesible por teclado; y emitir el clic igual. Un componente que envuelve un elemento nativo y pierde su comportamiento es una violación que el usuario sufre directamente.

Un control propio de formulario debe implementar `ControlValueAccessor` completo: si ignora `setDisabledState`, el formulario lo deshabilita y el control sigue aceptando entradas.

## Errores comunes

- Métodos vacíos o que lanzan "no soportado" en una implementación.
- Dobles de prueba que devuelven `undefined` donde el real devuelve `null` o lanza error.
- Simulacros que nunca fallan: las pruebas pasan y el manejo de errores nunca se ejercita.
- Componentes envoltorio que pierden accesibilidad o el foco.
- Heredar de un componente y cambiar el significado de un `input`.
- `ControlValueAccessor` a medias.

## Taller

1. Diseña `Notificador` con tres implementaciones (aviso en pantalla, registro en consola, envío a un servicio). Escribe una misma batería de pruebas que deban pasar las tres.
2. Encuentra en un proyecto tuyo un método vacío o que lance "no implementado" y rediseña el contrato.
3. Crea un componente de botón que envuelva `<button>` y verifica con pruebas que respeta `disabled`, el foco y la activación por teclado.
4. Implementa un control de formulario propio (un selector de calificación por estrellas) con `ControlValueAccessor` completo y comprueba que `disable()` del formulario funciona.
5. Revisa un simulacro de tus pruebas: ¿en qué se diferencia del servicio real?

Rama `taller/ng-n2-03-lsp`.

## Preguntas senior

1. ¿Por qué el tipado estructural no garantiza la sustitución?
2. ¿Qué debe cumplir un doble de prueba para ser fiable?
3. ¿Qué responsabilidades adquiere un componente que envuelve un elemento nativo?
4. ¿Cómo pruebas que varias implementaciones cumplen el mismo contrato?
