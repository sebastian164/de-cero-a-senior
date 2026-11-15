# 03 — AOP, proxies y `@Transactional` por dentro

Requisito: el patrón [Proxy](../../nivel-03-patrones/estructurales/proxy/).

## Idea

En un edificio, el vigilante de la entrada registra a todo el que entra sin que cada oficina tenga que hacerlo. AOP es ese vigilante: comportamiento que se aplica alrededor de muchos métodos sin escribirlo en cada uno.

## Lo esencial

- **Preocupación transversal:** algo que atraviesa muchos módulos: transacciones, seguridad, caché, auditoría, métricas, reintentos.
- **Aspecto:** el código transversal. **Consejo (*advice*):** qué hace (`@Around`, `@Before`, `@AfterReturning`, `@AfterThrowing`). **Punto de corte (*pointcut*):** dónde se aplica.
- **Mecanismo:** Spring envuelve el bean en un **proxy**. Quien inyecta el bean recibe el proxy; el proxy ejecuta el consejo y delega en el objeto real.

### Las tres consecuencias del proxy

1. **Autoinvocación:** `this.metodo()` no pasa por el proxy. Las anotaciones del método llamado no se aplican.
2. **Solo métodos públicos no `final`** de beans gestionados.
3. **Objetos creados con `new`** no tienen proxy.

### `@Transactional`

El proxy abre la transacción antes del método y confirma o revierte al salir.

| Atributo | Qué controla |
|---|---|
| `propagation` | Qué pasa si ya hay una transacción (`REQUIRED` por defecto, `REQUIRES_NEW`, `MANDATORY`, `NESTED`…) |
| `isolation` | Nivel de aislamiento |
| `readOnly` | Indica solo lectura; permite optimizaciones |
| `rollbackFor` | Qué excepciones revierten |
| `timeout` | Tiempo máximo |

**Reglas por defecto que causan errores reales:**

- Revierte con `RuntimeException` y `Error`. **No** con excepciones comprobadas.
- Una excepción capturada dentro del método no revierte.
- Si un método interno marca la transacción para revertir y el externo captura la excepción, al confirmar se lanza `UnexpectedRollbackException`.

## Ejemplo

```java
@Service
class ServicioPedidos {

    public void importar(List<PedidoDto> lote) {
        for (PedidoDto dto : lote) {
            guardar(dto);              // autoinvocación: @Transactional NO se aplica
        }
    }

    @Transactional
    public void guardar(PedidoDto dto) { ... }
}
```

Correcciones: mover `guardar` a otro bean; usar `TransactionTemplate` de forma explícita; o anotar el método externo si la intención es una sola transacción.

Un aspecto propio:

```java
@Aspect
@Component
class AspectoDeTiempos {

    @Around("@annotation(Medido)")
    Object medir(ProceedingJoinPoint punto) throws Throwable {
        long inicio = System.nanoTime();
        try {
            return punto.proceed();
        } finally {
            log.info("{} tardó {} ms", punto.getSignature().toShortString(),
                    (System.nanoTime() - inicio) / 1_000_000);
        }
    }
}
```

## Errores comunes

- `@Transactional` en métodos privados o llamados internamente.
- Transacciones largas que incluyen llamadas HTTP o envío de correos: retienen una conexión del pool.
- `@Transactional` en el controlador.
- Asumir que una excepción comprobada revierte.
- Aspectos con lógica de negocio.
- Puntos de corte demasiado amplios que afectan lo que no se pretendía.
- `REQUIRES_NEW` usado sin considerar que consume otra conexión.

## Taller

1. Demuestra la autoinvocación: un método transaccional llamado internamente que falla y no revierte. Corrígelo de dos formas.
2. Lanza una excepción comprobada dentro de un método transaccional y verifica que los datos quedaron guardados. Corrígelo.
3. Reproduce `UnexpectedRollbackException` y explica la causa.
4. Escribe un aspecto de auditoría que registre quién ejecutó los métodos anotados con `@Auditable`.
5. Imprime `servicio.getClass()` de un bean transaccional y explica el nombre que aparece.
6. Compara `REQUIRED` y `REQUIRES_NEW` con un caso de registro de auditoría que debe persistir aunque la operación falle.

Rama `taller/n7-03-aop-transacciones`.

## Preguntas senior

1. Explica por qué `@Transactional` no funciona en una llamada interna.
2. ¿Qué excepciones revierten por defecto y cómo se cambia?
3. Proxy dinámico del JDK frente a CGLIB.
4. ¿Por qué no hacer una llamada HTTP dentro de una transacción?
5. ¿Cuándo usar `REQUIRES_NEW` y qué riesgo tiene?
6. ¿Dónde colocas los límites transaccionales en una arquitectura hexagonal?
