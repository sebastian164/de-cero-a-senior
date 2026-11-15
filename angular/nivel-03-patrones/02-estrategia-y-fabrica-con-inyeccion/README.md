# 02 — Strategy y Factory con inyección de dependencias

Los patrones clásicos están en la [ruta Java](../../../java/nivel-03-patrones/). En Angular, el contenedor de inyección hace buena parte del trabajo de Strategy, Factory Method y Abstract Factory.

## El problema

Un servicio decide entre variantes con `if` por un dato (`pais`, `tipo`, `proveedor`), o crea sus colaboradores con `new`. Cada variante nueva lo modifica, y no hay forma limpia de cambiar la variante en pruebas o por ambiente.

## Las herramientas

| Necesidad | Mecanismo |
|---|---|
| Una implementación fija por ambiente | `{ provide: Contrato, useClass: Implementacion }` |
| Elegir en ejecución | `useFactory` con `inject()` |
| Todas las implementaciones a la vez | Token con `multi: true` |
| Elegir una por clave | Token `multi` con un mapa, o un `Record` de fábricas |
| Configuración por funcionalidad | Proveedores en la ruta |
| Función de configuración reutilizable | `provideAlgo(opciones)` que devuelve proveedores |

## Ejemplo: estrategia elegida por clave

```ts
export interface CalculoEnvio {
  readonly modalidad: string;
  calcular(pesoKg: number): number;
}

export const CALCULOS_ENVIO = new InjectionToken<CalculoEnvio[]>('cálculos de envío');

@Injectable() export class EnvioEstandar implements CalculoEnvio {
  readonly modalidad = 'estandar';
  calcular(pesoKg: number) { return 8000 + pesoKg * 500; }
}

@Injectable() export class EnvioExpress implements CalculoEnvio {
  readonly modalidad = 'express';
  calcular(pesoKg: number) { return 15000 + pesoKg * 900; }
}

// Función de configuración: el punto único donde se registran
export function provideEnvios(): Provider[] {
  return [
    { provide: CALCULOS_ENVIO, useClass: EnvioEstandar, multi: true },
    { provide: CALCULOS_ENVIO, useClass: EnvioExpress, multi: true },
  ];
}

// El contexto no conoce las estrategias concretas
@Injectable({ providedIn: 'root' })
export class CotizadorEnvio {
  private readonly calculos = new Map(inject(CALCULOS_ENVIO).map((c) => [c.modalidad, c]));

  cotizar(modalidad: string, pesoKg: number): number {
    const calculo = this.calculos.get(modalidad);
    if (!calculo) throw new Error(`Modalidad no soportada: ${modalidad}`);
    return calculo.calcular(pesoKg);
  }
}
```

Agregar una modalidad es escribir una clase y registrarla. `CotizadorEnvio` no cambia.

## Ejemplo: fábrica que decide en ejecución

```ts
export const PASARELA = new InjectionToken<PasarelaPagos>('pasarela', {
  providedIn: 'root',
  factory: () => {
    const config = inject(CONFIGURACION);
    return config.pais === 'CO' ? inject(PasarelaPse) : inject(PasarelaInternacional);
  },
});
```

Cuando la estrategia no tiene estado ni dependencias, una función basta:

```ts
type CalculoEnvio = (pesoKg: number) => number;
const CALCULOS: Record<Modalidad, CalculoEnvio> = {
  estandar: (kg) => 8000 + kg * 500,
  express: (kg) => 15000 + kg * 900,
  recogida: () => 0,
};
```

El tipo `Record<Modalidad, …>` obliga a cubrir todas las modalidades: si se agrega una a la unión, no compila hasta definirla.

## En el framework

`HTTP_INTERCEPTORS` y `withInterceptors`, `provideRouter(rutas, withComponentInputBinding())`, validadores, `ErrorHandler`, `TitleStrategy`: Angular se extiende registrando proveedores.

## Cuándo no

Con dos variantes estables, un `if`. Sin dependencias ni estado, un mapa de funciones. El token `multi` vale la pena cuando las variantes crecen o se registran desde módulos distintos.

## Errores comunes

- `switch` por tipo repetido en varios servicios.
- Fábricas con lógica de negocio.
- Registrar proveedores en muchos lugares: no se sabe qué implementación está activa.
- Olvidar `multi: true` y sobrescribir la implementación anterior.
- Tokens sin tipo.

## Taller

1. Implementa validadores de documento por país como estrategias registradas con `multi: true`.
2. Agrega un país sin tocar el servicio que valida.
3. Reescribe lo mismo con un `Record` de funciones y compara.
4. Escribe una función `provideX(opciones)` que configure una funcionalidad.
5. En una prueba, sustituye una estrategia por un doble.

Rama `taller/ng-n3-02-estrategia`.

## Preguntas senior

1. ¿Cómo implementas Strategy en Angular sin un `switch`?
2. ¿Cuándo una estrategia debe ser una clase inyectable y cuándo una función?
3. ¿Qué ventaja dan las funciones `provideX`?
4. ¿Cómo sabes qué implementación está activa en una aplicación grande?
