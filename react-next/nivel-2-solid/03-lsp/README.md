# 03 — LSP en React

Explicación completa: [LSP en la ruta Java](../../../java/nivel-2-solid/03-lsp/).

## Idea

Si tu componente se presenta como un botón, debe comportarse como un botón. Quien lo usa espera poder pasarle `disabled`, `onClick`, `aria-label` o una referencia, y que funcione igual que el elemento nativo. Un componente que acepta esas propiedades y las ignora rompe la promesa.

## Dónde aparece la sustitución

| Lugar | La promesa |
|---|---|
| Componentes que envuelven un elemento nativo | Aceptar y respetar sus propiedades, su accesibilidad y su referencia |
| Variantes de un mismo componente | Mismas propiedades con el mismo significado |
| Implementaciones de un mismo contrato (por contexto o por parámetro) | Mismo comportamiento ante éxito, vacío y error |
| Hooks intercambiables | Misma forma de retorno y mismas garantías |
| Dobles de prueba y simulacros de red | Parecerse al real en lo que la prueba depende |

## Antes

```tsx
interface CampoProps {
  etiqueta: string;
  valor: string;
  alCambiar: (valor: string) => void;      // firma distinta a la del input nativo
}

function Campo({ etiqueta, valor, alCambiar }: CampoProps) {
  return (
    <div>
      <span>{etiqueta}</span>               {/* sin label asociado */}
      <input value={valor} onChange={(e) => alCambiar(e.target.value)} />
    </div>
  );
}

// No acepta disabled, required, type, name, autoComplete, onBlur, ref ni aria-*.
// No funciona con bibliotecas de formularios ni con formularios nativos.
```

## Después

```tsx
type CampoProps = React.ComponentProps<'input'> & {
  etiqueta: string;
  error?: string;
};

export function Campo({ etiqueta, error, id, ...resto }: CampoProps) {
  const idGenerado = useId();
  const idCampo = id ?? idGenerado;
  const idError = `${idCampo}-error`;

  return (
    <div>
      <label htmlFor={idCampo}>{etiqueta}</label>
      <input
        id={idCampo}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? idError : undefined}
        {...resto}                                    // todo lo que acepta un input, incluida la ref
      />
      {error && <p id={idError} role="alert">{error}</p>}
    </div>
  );
}
```

Ahora `Campo` se puede usar en cualquier lugar donde iría un `<input>`: con un formulario nativo, con una biblioteca de formularios o con una acción de servidor.

Contratos de datos sustituibles:

```ts
export interface RepositorioPedidos {
  /** Devuelve [] si no hay pedidos. Lanza ErrorDeRed si no se puede consultar. Nunca devuelve null. */
  listar(): Promise<readonly Pedido[]>;
}

// La implementación real, la de memoria y el simulacro de pruebas cumplen lo mismo:
export const pedidosEnMemoria = (iniciales: Pedido[] = []): RepositorioPedidos => ({
  listar: async () => [...iniciales],
});
```

Una misma batería de pruebas puede ejecutarse contra todas las implementaciones.

## Errores comunes

- Componentes envoltorio que no reenvían propiedades, referencia ni atributos de accesibilidad.
- `onChange` que a veces entrega el evento y a veces el valor, según el componente.
- Variantes que cambian el significado de una propiedad.
- Implementaciones que devuelven `null`, `undefined` o `[]` de forma inconsistente.
- Simulacros de red que nunca fallan ni tardan.
- Un componente de cliente que reemplaza a uno de servidor cambiando qué datos necesita.
- Elementos clicables que no son botones ni enlaces.

## Taller

1. Construye `Boton`, `Campo` y `Enlace` como envoltorios que respeten todo el contrato del elemento nativo. Pruébalo: `disabled`, foco, teclado, referencia.
2. Define un contrato de repositorio y escribe una batería de pruebas que pasen la implementación real (con red simulada) y la de memoria.
3. Encuentra en un proyecto tuyo un componente que ignore una propiedad que acepta.
4. Revisa los simulacros de tus pruebas: ¿en qué se diferencian del servidor real?

Rama `taller/rx-n2-03-lsp`.

## Preguntas senior

1. ¿Qué responsabilidades asume un componente que envuelve un elemento nativo?
2. ¿Cómo pruebas que varias implementaciones cumplen el mismo contrato?
3. ¿Qué debe cumplir un simulacro para que la prueba sea fiable?
