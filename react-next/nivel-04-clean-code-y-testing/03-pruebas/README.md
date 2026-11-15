# 03 — Pruebas en React y Next.js

Los fundamentos están en el [Nivel 4 de Java](../../../java/nivel-04-clean-code-testing/). La estrategia general y las pruebas de extremo a extremo, que no dependen del framework, están en la [sesión de E2E de la ruta Angular](../../../angular/nivel-04-clean-code-y-testing/04-pruebas-e2e-y-estrategia/).

## Idea

Una prueba de interfaz debería parecerse a cómo se usa la aplicación: "escribo, pulso, veo". Si en cambio pregunta por el estado interno del componente o por cuántas veces se llamó a un hook, se rompe con cada refactor y no detecta los errores que sí ve el usuario.

## Qué probar y con qué

| Qué | Cómo | Herramientas habituales |
|---|---|---|
| Dominio, reductores, esquemas, adaptadores | Funciones puras: entrada y salida | Vitest o Jest |
| Hooks con lógica | La lógica pura afuera; el hook con `renderHook` si hace falta | Testing Library |
| Componentes | Renderizar e interactuar como usuario | React Testing Library + `user-event` |
| Componentes que piden datos | Interceptar la red, no simular módulos | MSW |
| Acciones de servidor y funciones de acceso | Como funciones asíncronas, con dobles de sus dependencias | Vitest |
| Componentes de servidor asíncronos y recorridos | De extremo a extremo | Playwright |

Cuanta más lógica esté en funciones puras, más pruebas baratas.

## Ejemplos

**Reductor:**

```ts
describe('reductorCarrito', () => {
  it('suma la cantidad cuando el producto ya está en el carrito', () => {
    const inicial = { lineas: [unaLinea({ productoId: 'p1', cantidad: 1 })] };
    const resultado = reductorCarrito(inicial, { tipo: 'agregado', producto: unProducto({ id: 'p1' }) });
    expect(resultado.lineas).toEqual([expect.objectContaining({ productoId: 'p1', cantidad: 2 })]);
  });

  it('no modifica el estado original', () => {
    const inicial = { lineas: [] };
    reductorCarrito(inicial, { tipo: 'agregado', producto: unProducto() });
    expect(inicial.lineas).toHaveLength(0);
  });
});
```

**Componente, como usuario:**

```tsx
it('avisa cuando se cancela un pedido', async () => {
  const usuario = userEvent.setup();
  const alCancelar = vi.fn();
  render(<ListaPedidos pedidos={[unPedido({ id: 'p1', cliente: 'Ana' })]} onCancelar={alCancelar} />);

  await usuario.click(screen.getByRole('button', { name: /cancelar/i }));

  expect(alCancelar).toHaveBeenCalledWith('p1');
});

it('muestra el error junto al campo', async () => {
  const usuario = userEvent.setup();
  render(<FormularioRegistro />);

  await usuario.type(screen.getByLabelText('Correo'), 'no-es-correo');
  await usuario.tab();

  expect(await screen.findByRole('alert')).toHaveTextContent('Escribe un correo válido');
  expect(screen.getByLabelText('Correo')).toBeInvalid();
});
```

Se busca por rol y por etiqueta, como lo haría un lector de pantalla. Orden de preferencia: `getByRole`, `getByLabelText`, `getByText`; `getByTestId` solo como último recurso.

**Red simulada (MSW):**

```tsx
const servidor = setupServer(
  http.get('/api/pedidos', () => HttpResponse.json([{ id: 1, estado: 1, valor_centavos: 12000, /* … */ }])),
);
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
afterEach(() => servidor.resetHandlers());
afterAll(() => servidor.close());

it('muestra un mensaje y permite reintentar cuando el servidor falla', async () => {
  servidor.use(http.get('/api/pedidos', () => new HttpResponse(null, { status: 500 })));
  renderConProveedores(<PaginaPedidos />);

  expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudieron cargar/i);
  expect(screen.getByRole('button', { name: /reintentar/i })).toBeEnabled();
});
```

Interceptar en la red, y no simular `fetch` ni el módulo de datos, hace que la prueba ejercite adaptadores, validación y manejo de errores reales.

**Acción de servidor:**

```ts
it('rechaza un registro con correo ya usado', async () => {
  vi.mocked(existeCorreo).mockResolvedValue(true);
  const datos = aFormData({ correo: 'ana@correo.example', documento: '1234567', clave: 'x'.repeat(12), confirmacion: 'x'.repeat(12) });

  const estado = await registrar({}, datos);

  expect(estado.errores?.correo).toEqual(['Ese correo ya está registrado']);
});
```

## Buenas prácticas

- Fábricas de datos (`unPedido({ … })`).
- Una función `renderConProveedores` que monte los contextos necesarios.
- `findBy…` para lo asíncrono; nunca esperas fijas.
- Temporizadores simulados para lógica con tiempo.
- Probar los estados de carga, vacío y error.
- No probar detalles de implementación: ni estado interno, ni nombres de clases CSS.

## Errores comunes

- Renderizado superficial y aserciones sobre el árbol interno.
- Simular todos los hijos y todos los hooks.
- `getByTestId` para todo.
- Instantáneas enormes que se aprueban sin leer.
- Avisos de `act(...)` ignorados.
- Pruebas que dependen del orden.
- Simular el módulo de datos entero: la prueba ya no verifica la integración.
- Cobertura alta con aserciones triviales.

## Taller

1. Prueba todas las funciones del dominio y los reductores de un proyecto tuyo.
2. Prueba un formulario como usuario: escribir, salir del campo, enviar, ver errores.
3. Prueba una pantalla con datos usando MSW: éxito, vacío, error y reintento.
4. Prueba una acción de servidor, incluida la validación y la autorización.
5. Escribe dos pruebas de extremo a extremo de recorridos críticos.
6. Introduce un error en el código y verifica que alguna prueba falla.

Rama `taller/rx-n4-03-pruebas`.

## Preguntas senior

1. ¿Por qué consultar por rol y no por identificador de prueba?
2. ¿Qué ventaja tiene interceptar la red frente a simular módulos?
3. ¿Cómo pruebas un componente de servidor asíncrono?
4. ¿Qué es un detalle de implementación en una prueba de componente?
5. ¿Cómo repartes las pruebas entre niveles en una aplicación Next.js?
