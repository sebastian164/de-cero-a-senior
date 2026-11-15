# 03 — Pruebas unitarias y de componentes

Los fundamentos (AAA, dobles, TDD, qué hace buena a una prueba) están en el [Nivel 4 de Java](../../../java/nivel-4-clean-code-testing/). El ejecutor de pruebas por defecto de Angular ha cambiado entre versiones (Karma y Jasmine, luego Vitest): la sintaxis de `describe`, `it` y `expect` es casi idéntica; revisa cuál usa tu proyecto.

## Idea

Una buena prueba de interfaz se parece a lo que haría una persona: "escribo el correo, pulso enviar, veo el mensaje de confirmación". No pregunta por variables internas del componente. Así, la prueba sigue sirviendo cuando el componente se reescribe por dentro.

## Qué probar y cómo

| Qué | Cómo | Velocidad |
|---|---|---|
| Funciones puras (reglas, adaptadores, validadores) | Sin Angular: entrada y salida | Muy rápida |
| Servicios y almacenes | `TestBed` mínimo, dobles de sus dependencias | Rápida |
| Servicios HTTP | `provideHttpClientTesting` y `HttpTestingController` | Rápida |
| Componentes | Renderizados, interactuando como un usuario | Media |
| Flujos completos | Extremo a extremo (sesión siguiente) | Lenta |

Cuanta más lógica viva en funciones puras, más pruebas baratas tendrás. Es la consecuencia práctica de SRP.

## Ejemplos

**Función pura:**

```ts
describe('total', () => {
  it('suma los importes de las líneas y aplica el IVA', () => {
    const pedido = unPedido({ lineas: [{ precio: 100, cantidad: 2 }, { precio: 50, cantidad: 1 }] });
    expect(total(pedido)).toBe(297.5);
  });
});
```

**Almacén con un doble de la API:**

```ts
describe('AlmacenPedidos', () => {
  it('expone solo los pedidos del filtro activo', () => {
    const api = { listar: () => of([unPedido({ estado: 'pendiente' }), unPedido({ estado: 'pagado' })]) };
    TestBed.configureTestingModule({ providers: [{ provide: PedidosApi, useValue: api }] });
    const almacen = TestBed.inject(AlmacenPedidos);

    almacen.cargar();
    almacen.filtrar('pendiente');

    expect(almacen.visibles()).toHaveLength(1);
  });
});
```

**Servicio HTTP:**

```ts
it('traduce la respuesta de la API al modelo', () => {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const api = TestBed.inject(PedidosApi);
  const http = TestBed.inject(HttpTestingController);
  let resultado: Pedido[] = [];

  api.listar().subscribe((pedidos) => (resultado = pedidos));
  http.expectOne('/api/pedidos').flush([{ id: 7, estado: 1, valor_centavos: 12000, /* ... */ }]);

  expect(resultado[0]).toMatchObject({ id: '7', estado: 'pendiente', total: 120 });
  http.verify();
});
```

**Componente, como lo usa una persona (Angular Testing Library):**

```ts
it('avisa al padre cuando se cancela un pedido', async () => {
  const cancelar = vi.fn();
  await render(ListaPedidosComponent, {
    inputs: { pedidos: [unPedido({ id: 'p1', cliente: 'Ana' })] },
    on: { cancelar },
  });

  await userEvent.click(screen.getByRole('button', { name: /cancelar/i }));

  expect(cancelar).toHaveBeenCalledWith('p1');
});

it('muestra un mensaje cuando no hay pedidos', async () => {
  await render(ListaPedidosComponent, { inputs: { pedidos: [] } });
  expect(screen.getByText('No hay pedidos.')).toBeInTheDocument();
});
```

Se busca por rol y por texto visible, como lo haría un lector de pantalla. Si la prueba no encuentra el botón por su rol, probablemente tampoco lo encuentre una persona que usa tecnología de asistencia.

## Buenas prácticas

- **Fábricas de datos** (`unPedido({ … })`): cada prueba declara solo lo que le importa.
- **Probar comportamiento, no implementación:** no accedas a `component.propiedadPrivada`.
- **Tiempo controlado:** temporizadores simulados para `debounceTime` y similares.
- **Un motivo de fallo por prueba.**
- **Componentes hijos reales** cuando son de presentación; sustituye solo lo costoso.
- **Sin dependencia entre pruebas.**

## Errores comunes

- Probar que "el componente se crea" y nada más.
- Pruebas que consultan por clases CSS o por estructura del DOM.
- Simular todos los hijos y todos los servicios: la prueba verifica los simulacros.
- Instantáneas (*snapshots*) gigantes que se actualizan sin leer.
- `fixture.detectChanges()` repartido a ciegas hasta que pasa.
- Esperas reales (`setTimeout`) en las pruebas.
- Cobertura alta sin aserciones.
- No probar los estados de error, vacío y carga.

## Taller

1. Prueba todas las funciones puras del dominio de un proyecto tuyo.
2. Prueba un almacén: carga, filtro, actualización optimista y su reversa.
3. Prueba un servicio HTTP, incluido el caso de error 500.
4. Prueba un componente de formulario como usuario: llenar, enviar, ver errores.
5. Prueba el buscador con `debounceTime` usando tiempo simulado.
6. Introduce un error en el código y comprueba que alguna prueba falla.

Rama `taller/ng-n4-03-pruebas`.

## Preguntas senior

1. ¿Qué significa probar un componente "como usuario" y por qué resiste mejor los cambios?
2. ¿Qué pruebas escribirías primero en una aplicación sin ninguna?
3. ¿Cuándo sustituyes un componente hijo o un servicio y cuándo usas el real?
4. ¿Cómo pruebas código asíncrono y dependiente del tiempo?
5. ¿Qué te dice la dificultad para probar un componente?
