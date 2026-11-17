# 03 — Arquitectura limpia en el frontend

Requisito: [Inversión de dependencias en arquitectura](../../../java/nivel-05-arquitectura/04-inversion-de-dependencias-en-arquitectura/), [Clean](../../../java/nivel-05-arquitectura/05-clean-architecture/) y [hexagonal](../../../java/nivel-05-arquitectura/06-arquitectura-hexagonal/).

## Idea

La regla es la misma que en el backend: lo valioso y estable (las reglas de tu aplicación) no depende de lo reemplazable (el framework, la API, el navegador). La diferencia es de dosis: en un frontend hay menos reglas de negocio, así que hace falta menos estructura.

## Lo esencial

### Capas dentro de una funcionalidad

```
        ui / páginas      ← Angular: componentes, plantillas
              │
        aplicación        ← almacenes y casos de uso: orquestan
              │
          dominio         ← tipos y funciones puras. Sin Angular
              ▲
          datos           ← API, adaptadores, almacenamiento. Implementa contratos del dominio
```

Las dependencias apuntan al dominio. `datos` implementa interfaces que el dominio o la aplicación definen.

| Capa | Contiene | Puede importar |
|---|---|---|
| Dominio | Modelos, reglas, validaciones, cálculos, contratos (puertos) | Nada externo (los puertos pueden nombrar `Observable` de RxJS; nunca Angular) |
| Aplicación | Casos de uso, almacenes | Dominio; lo mínimo de Angular |
| Datos | Clientes HTTP, DTO, adaptadores, caché, almacenamiento local | Dominio, Angular, bibliotecas |
| Interfaz | Componentes, tuberías, directivas | Aplicación, dominio |

### Qué es "dominio" en un frontend

No dupliques el dominio del servidor. El dominio del frontend es lo que el cliente necesita decidir por sí mismo:

- Reglas de presentación con peso de negocio: ¿se puede cancelar este pedido?, ¿qué pasos del asistente aplican?
- Cálculos que el usuario ve mientras interactúa: totales, descuentos previos.
- Validaciones.
- Máquinas de estado de flujos complejos.

Si la interfaz solo muestra lo que el servidor calcula, el dominio del frontend es delgado, y eso está bien.

## Ejemplo

```ts
// dominio/pedido.ts — sin importaciones de Angular
export interface Pedido {
  readonly id: string;
  readonly estado: 'pendiente' | 'pagado' | 'enviado' | 'cancelado';
  readonly creadoEn: Date;
  readonly lineas: readonly Linea[];
}

export const sePuedeCancelar = (pedido: Pedido, ahora: Date): boolean =>
  (pedido.estado === 'pendiente' || pedido.estado === 'pagado') &&
  horasEntre(pedido.creadoEn, ahora) <= 24;

// dominio/puertos.ts — lo que la aplicación necesita del exterior
export abstract class RepositorioPedidos {
  abstract listar(): Observable<Pedido[]>;
  abstract cancelar(id: string): Observable<void>;
}

// aplicacion/cancelar-pedido.ts — caso de uso
@Injectable()
export class CancelarPedido {
  private readonly repositorio = inject(RepositorioPedidos);
  private readonly ahora = inject(RELOJ);

  ejecutar(pedido: Pedido): Observable<void> {
    if (!sePuedeCancelar(pedido, new Date(this.ahora()))) {
      return throwError(() => new PedidoNoCancelable(pedido.id));
    }
    return this.repositorio.cancelar(pedido.id);
  }
}

// datos/pedidos-http.repositorio.ts — adaptador
@Injectable()
export class PedidosHttp extends RepositorioPedidos {
  private readonly http = inject(HttpClient);
  listar() { return this.http.get<PedidoDto[]>('/api/pedidos').pipe(map((dtos) => dtos.map(aPedido))); }
  cancelar(id: string) { return this.http.post<void>(`/api/pedidos/${id}/cancelacion`, {}); }
}

// pedidos.rutas.ts — ensamblaje
providers: [{ provide: RepositorioPedidos, useClass: PedidosHttp }, CancelarPedido, AlmacenPedidos]
```

La regla de las 24 horas se prueba sin Angular, sin HTTP y con el reloj que se quiera. El botón "cancelar" se muestra con `sePuedeCancelar`, y el servidor vuelve a validar.

### La dosis correcta

| Aplicación | Estructura razonable |
|---|---|
| Pantallas CRUD sobre una API | Componentes, un servicio de datos con adaptadores. Sin capas formales |
| Reglas de cliente moderadas | Dominio en funciones puras, almacén por funcionalidad |
| Lógica de cliente compleja (editores, asistentes, modo sin conexión, cálculos) | Capas completas con puertos |

Aplicar la estructura completa a un CRUD produce tres archivos que solo reenvían una llamada.

## Errores comunes

- Copiar la arquitectura del backend capa por capa.
- Casos de uso que solo llaman al repositorio.
- Reglas de negocio en componentes, tuberías o interceptores.
- Dominio que importa `HttpClient` o `signal`.
- Mapeadores entre capas con tipos idénticos.
- Duplicar en el cliente reglas que deben decidirse en el servidor, y que se desincronicen.
- Confiar en la regla del cliente como si fuera la validación definitiva.

## Taller

1. Elige una funcionalidad con reglas reales (por ejemplo, un asistente de solicitud con pasos condicionales) y sepárala en las cuatro capas.
2. Prueba todas las reglas del dominio sin Angular.
3. Agrega un segundo adaptador de datos (en memoria, para desarrollo sin servidor) y cámbialo por configuración.
4. Verifica con una regla de lint que `dominio` no importa Angular.
5. Toma una pantalla CRUD y justifica por qué no le aplicarías esta estructura.

Rama `taller/ng-n5-03-arquitectura-limpia`.

## Preguntas senior

1. ¿Qué es dominio en un frontend y qué no lo es?
2. ¿Cómo decides cuánta arquitectura necesita una aplicación de interfaz?
3. ¿Qué gana un equipo al tener las reglas fuera de los componentes?
4. ¿Cómo evitas que las reglas del cliente y del servidor se desincronicen?
