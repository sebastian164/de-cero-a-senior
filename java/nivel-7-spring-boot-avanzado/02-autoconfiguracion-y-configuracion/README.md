# 02 — Autoconfiguración, starters y configuración externa

## Idea

Spring Boot funciona como un asistente que observa lo que tienes en la mesa y prepara lo obvio: si ve un driver de PostgreSQL y una URL, configura la conexión. Tú solo intervienes para lo que se sale de lo habitual. Saber cómo decide permite intervenir sin pelear con él.

## Lo esencial

- **Starter:** una dependencia que agrupa bibliotecas compatibles (`spring-boot-starter-web`, `-data-jpa`, `-security`, `-test`).
- **Autoconfiguración:** clases `@AutoConfiguration` que registran beans **si se cumplen condiciones**:

| Condición | Se activa si |
|---|---|
| `@ConditionalOnClass` | Cierta clase está en el classpath |
| `@ConditionalOnMissingBean` | No definiste tú ese bean |
| `@ConditionalOnProperty` | Una propiedad tiene cierto valor |
| `@ConditionalOnWebApplication` | Es una aplicación web |

- **Regla de oro:** tu bean gana. `@ConditionalOnMissingBean` hace que la autoconfiguración se retire si declaras el tuyo.
- **Diagnóstico:** arranca con `--debug` o consulta `/actuator/conditions` para ver qué se configuró y por qué.

### Configuración externa

Orden de precedencia (de menor a mayor, simplificado): valores por defecto → `application.yml` → `application-{perfil}.yml` → variables de entorno → argumentos de línea de comandos.

- **Perfiles:** `spring.profiles.active=prod`. Para variar configuración por ambiente, no lógica de negocio.
- **`@ConfigurationProperties`:** propiedades tipadas, validadas y agrupadas. Preferible a `@Value` disperso.
- **Secretos:** nunca en el repositorio. Variables de entorno, un gestor de secretos o un servidor de configuración.

## Ejemplo

```java
@ConfigurationProperties(prefix = "pagos")
@Validated
record PagosProperties(
        @NotBlank String claveApi,
        @NotNull Duration tiempoLimite,
        @Min(0) @Max(5) int reintentos) { }
```

```yaml
pagos:
  clave-api: ${PAGOS_CLAVE_API}      # viene del entorno
  tiempo-limite: 3s
  reintentos: 2
```

Si falta la clave o un valor es inválido, la aplicación no arranca: se falla pronto y con un mensaje claro.

Una autoconfiguración propia:

```java
@AutoConfiguration
@ConditionalOnClass(ClienteAuditoria.class)
@EnableConfigurationProperties(AuditoriaProperties.class)
class AuditoriaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ClienteAuditoria clienteAuditoria(AuditoriaProperties propiedades) {
        return new ClienteAuditoria(propiedades.url());
    }
}
```

## Errores comunes

- `@Value` repetido en veinte clases para la misma propiedad.
- Secretos en `application.yml` dentro del repositorio.
- `if (perfil.equals("prod"))` en el código: la variación debe estar en la configuración.
- Excluir autoconfiguraciones sin entender qué hacían.
- Demasiados perfiles combinados que nadie puede razonar.
- Pelear con la autoconfiguración en lugar de declarar el bean propio.

## Taller

1. Arranca una aplicación con `--debug` y explica por qué se configuraron tres beans y por qué no otros tres.
2. Reemplaza cinco `@Value` por una clase `@ConfigurationProperties` validada. Haz que el arranque falle con un valor inválido.
3. Define tres perfiles (dev, test, prod) y demuestra la precedencia sobrescribiendo una propiedad por variable de entorno.
4. Sustituye un bean autoconfigurado (por ejemplo, `ObjectMapper`) por uno propio y verifica que la autoconfiguración se retiró.
5. Escribe un starter mínimo con su autoconfiguración y úsalo desde otra aplicación.

Rama `taller/n7-02-autoconfiguracion`.

## Preguntas senior

1. ¿Cómo decide Spring Boot qué configurar?
2. ¿Qué hace `@ConditionalOnMissingBean` y por qué es clave?
3. `@Value` frente a `@ConfigurationProperties`.
4. ¿Cómo gestionas secretos en distintos ambientes?
5. ¿Qué principio de los doce factores aplica a la configuración?
