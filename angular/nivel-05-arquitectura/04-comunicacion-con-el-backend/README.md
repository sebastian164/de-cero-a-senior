# 04 — Comunicación con el backend

## Idea

Entre la interfaz y el servidor hay una red que falla, tarda y a veces responde dos veces. Una aplicación bien hecha asume que cada petición puede salir mal y decide de antemano qué verá el usuario en cada caso.

## Lo esencial

### Una capa de datos

Todas las peticiones pasan por servicios de datos por funcionalidad. Los componentes nunca conocen URLs. Esa capa es responsable de: construir la petición, traducir DTO a modelos, y entregar errores con significado.

Dentro de esa capa, las **lecturas** se expresan hoy como recursos (`httpResource`, visto en el [Nivel 1](../../nivel-01-fundamentos/04-datos-asincronos-con-resource/)) y las **escrituras** como métodos que usan `HttpClient`. Los dos pasan por los mismos interceptores.

Desde Angular 22, `HttpClient` usa por defecto la API Fetch del navegador en lugar de `XMLHttpRequest`. Para casi todo es transparente; la excepción conocida es el progreso de subida de archivos, que con Fetch no está disponible y obliga a pedir el modo anterior de forma explícita.

### Los cuatro estados de toda petición

Cargando, éxito, vacío y error. Cada pantalla debe tener diseñados los cuatro. El más olvidado es el error, y el segundo, el vacío.

### Errores

| Error | Qué hace la aplicación |
|---|---|
| Sin conexión o tiempo vencido | Avisar, ofrecer reintento, conservar lo escrito |
| 400 / 422 | Mostrar el problema junto al campo o la acción |
| 401 | Renovar la sesión o llevar al ingreso, recordando a dónde volver |
| 403 | Explicar que no hay permiso; no reintentar |
| 404 | Pantalla de "no encontrado" |
| 409 | Avisar que el dato cambió y ofrecer recargar |
| 5xx | Mensaje genérico, reintento, y registrar el error |

Lo transversal (401, 5xx, registro) va en un interceptor. Lo específico de una pantalla, en quien hizo la petición. Los errores se traducen a tipos del dominio: el componente no interpreta códigos HTTP.

### Autenticación en el navegador

- **Dónde guardar el token:** una *cookie* `HttpOnly`, `Secure` y `SameSite` no es legible por JavaScript, lo que la protege de un ataque de XSS; exige protección contra CSRF. Un token en `localStorage` es legible por cualquier script que se ejecute en la página.
- Tokens de vida corta con renovación.
- Patrón BFF (*Backend for Frontend*): el navegador maneja solo una *cookie* de sesión y el servidor intermedio guarda los tokens.
- Nunca secretos en el código del frontend: todo lo que llega al navegador es público.

### Rendimiento percibido

- **Paralelizar** peticiones independientes.
- **Evitar cascadas:** una petición que solo puede salir cuando termina otra.
- **Caché y deduplicación** para lo que varias partes de la pantalla piden.
- **Paginación** o desplazamiento infinito con cursor.
- **Actualizaciones optimistas** donde el fallo es raro y reversible.
- **Esqueletos de carga** en lugar de indicadores giratorios para contenido estructurado.
- **Cancelar** lo que ya no interesa.

### Tiempo real

| Mecanismo | Uso |
|---|---|
| Sondeo | Simple; datos que cambian poco |
| Eventos enviados por el servidor (SSE) | El servidor empuja; una sola dirección |
| WebSocket | Bidireccional: chat, colaboración |

### Contratos

Generar los tipos y clientes a partir de la especificación OpenAPI mantiene sincronizados cliente y servidor: un cambio incompatible en la API rompe la compilación del frontend en lugar de fallar en producción.

## Ejemplo: errores con significado

```ts
export class PedidoNoEncontrado extends Error {}
export class SinConexion extends Error {}
export class ErrorDeValidacion extends Error {
  constructor(readonly campos: Record<string, string>) { super('Datos inválidos'); }
}

const aErrorDeDominio = (error: HttpErrorResponse): Error => {
  if (error.status === 0) return new SinConexion();
  if (error.status === 404) return new PedidoNoEncontrado();
  if (error.status === 422) return new ErrorDeValidacion(error.error?.campos ?? {});
  return error;
};

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);

  obtener(id: string): Observable<Pedido> {
    return this.http.get<PedidoDto>(`/api/pedidos/${id}`).pipe(
      timeout(8000),
      retry({ count: 2, delay: (_, intento) => timer(intento * 500) }),   // solo porque GET es idempotente
      map(aPedido),
      catchError((error) => throwError(() => aErrorDeDominio(error))),
    );
  }
}
```

## Errores comunes

- URLs y `HttpClient` en los componentes.
- Solo se diseña el caso feliz.
- "Ocurrió un error" para todo.
- Reintentar peticiones que no son idempotentes.
- Cascadas de peticiones en serie.
- Tokens en `localStorage` sin evaluar el riesgo.
- Claves de API de terceros en el código del cliente.
- Tipos de la API escritos a mano que se desactualizan.
- Sin tiempo límite: indicadores de carga eternos.

## Taller

1. Para una pantalla tuya, diseña e implementa los cuatro estados.
2. Implementa la traducción de errores HTTP a errores del dominio y úsala en dos pantallas.
3. Simula una red lenta y sin conexión con las herramientas del navegador y corrige lo que se rompa.
4. Encuentra una cascada de peticiones y paralelízala.
5. Genera los tipos desde una especificación OpenAPI.
6. Escribe una nota de una página: dónde guarda tu aplicación el token y qué riesgo asume.

Rama `taller/ng-n5-04-backend`.

## Preguntas senior

1. ¿Dónde guardas un token en el navegador y por qué?
2. ¿Qué errores maneja un interceptor y cuáles la pantalla?
3. ¿Cómo mantienes sincronizados los contratos entre frontend y backend?
4. ¿Qué es una cascada de peticiones y cómo se elimina?
5. ¿Cuándo usar sondeo, SSE o WebSocket?
