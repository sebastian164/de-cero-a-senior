# 04 — ISP en Angular

Explicación completa: [ISP en la ruta Java](../../../java/nivel-2-solid/04-isp/).

## Idea

Un componente no debería recibir un objeto entero para mostrar dos de sus campos, ni inyectar un servicio de treinta métodos para usar uno. Cuanto más ancho es lo que recibe, más motivos tiene para romperse.

## Lo esencial

ISP en el frontend se aplica a tres contratos:

| Contrato | Violación típica | Corrección |
|---|---|---|
| Entradas de un componente | `[usuario]="usuario"` para mostrar el nombre y la foto | Entradas con lo que necesita, o un tipo estrecho (`Pick<Usuario, 'nombre' \| 'foto'>`) |
| Servicios | Un `UsuariosService` con sesión, perfil, administración, preferencias y estadísticas | Servicios por rol, o tokens con interfaces estrechas |
| Modelos de datos | Un tipo `Pedido` gigante que usan todas las pantallas | Modelos de vista por pantalla |

Beneficios concretos: los componentes son reutilizables (no exigen el objeto completo del dominio), las pruebas necesitan datos mínimos, y un cambio en un campo que el componente no usa no lo afecta ni lo vuelve a pintar.

## Antes

```ts
@Component({
  selector: 'app-avatar',
  template: `<img [src]="usuario().perfil.fotoUrl" [alt]="usuario().nombres + ' ' + usuario().apellidos" />`,
})
export class AvatarComponent {
  usuario = input.required<Usuario>();     // 40 campos: roles, direcciones, preferencias, pedidos…
}

@Component({ /* ... */ })
export class ContadorPedidosComponent {
  private readonly usuarios = inject(UsuariosService);   // 30 métodos; usa uno
  protected readonly total = toSignal(this.usuarios.contarPedidosDelUsuarioActual());
}
```

Para mostrar un avatar en una lista de comentarios hay que construir un `Usuario` completo. Para probar el contador hay que simular un servicio enorme.

## Después

```ts
@Component({
  selector: 'app-avatar',
  template: `<img [src]="fotoUrl()" [alt]="nombre()" />`,
})
export class AvatarComponent {
  readonly fotoUrl = input.required<string>();
  readonly nombre = input.required<string>();
}

// Contrato estrecho, definido por quien lo consume
export abstract class ConteoDePedidos {
  abstract delUsuarioActual(): Observable<number>;
}

@Component({ /* ... */ })
export class ContadorPedidosComponent {
  private readonly conteo = inject(ConteoDePedidos);
  protected readonly total = toSignal(this.conteo.delUsuarioActual(), { initialValue: 0 });
}

// Una sola clase puede cumplir varios contratos estrechos
@Injectable({ providedIn: 'root' })
export class PedidosApi implements ConteoDePedidos, ConsultaDePedidos, GestionDePedidos { /* ... */ }

// Registro
providers: [{ provide: ConteoDePedidos, useExisting: PedidosApi }]
```

Se usan clases abstractas como contrato porque las interfaces de TypeScript no existen en ejecución y no sirven como identificador de inyección. La alternativa es un `InjectionToken`.

## Cuándo no

Si un componente usa casi todo el objeto, recibirlo completo es más simple que diez entradas. Y un servicio pequeño y cohesivo no necesita dividirse. El criterio es el mismo de siempre: ¿existe un consumidor que use solo una parte?

## Errores comunes

- Pasar el objeto de dominio completo a componentes de presentación.
- Componentes que reciben el almacén de estado entero.
- Servicios "fachada" que exponen todo a todos.
- Decenas de entradas sueltas que siempre viajan juntas: agrúpalas en un tipo.
- Interfaces de un método para todo, que luego se combinan en cada uso.

## Taller

1. Toma tres componentes de presentación tuyos y reduce sus entradas a lo que usan. Compara el tamaño de los datos de sus pruebas antes y después.
2. Toma tu servicio más grande, construye la matriz de qué consumidor usa qué método y propón contratos estrechos.
3. Implementa los contratos con una sola clase y `useExisting`.
4. Escribe la prueba de un componente con un doble de un solo método.
5. Señala un caso en el que decidiste no segregar.

Rama `taller/ng-n2-04-isp`.

## Preguntas senior

1. ¿Qué gana un componente al recibir solo lo que usa?
2. ¿Cómo defines un contrato estrecho inyectable en Angular?
3. Entradas sueltas frente a un objeto: criterios.
4. ¿Qué relación hay entre ISP y la facilidad de prueba?
