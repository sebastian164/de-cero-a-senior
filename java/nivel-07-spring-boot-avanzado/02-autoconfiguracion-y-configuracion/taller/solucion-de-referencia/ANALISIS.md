# Taller 07-02 — Autoconfiguración, starters y configuración externa (solución)

Dos módulos Maven: `auditoria-starter` (un starter mínimo con la autoconfiguración del README) y `aplicacion` (la usa). Spring Boot 4.1.1. `mvn test`: 2 pruebas en el starter y 8 en la aplicación.

## 1. `--debug`: tres que sí y tres que no

Se arrancó con `--debug` (el reporte completo tiene unas 690 líneas) y la prueba `reporteDeCondiciones` lee el mismo reporte con `ConditionEvaluationReport` y verifica:

| | Autoconfiguración | Razón (texto real del reporte) |
|---|---|---|
| SÍ | `auditoria.AuditoriaAutoConfiguration` | `@ConditionalOnClass found required class 'auditoria.ClienteAuditoria'` (el starter está en el classpath) |
| SÍ | `DispatcherServletAutoConfiguration` | `@ConditionalOnClass found required class 'org.springframework.web.servlet.DispatcherServlet'` |
| SÍ | `ValidationAutoConfiguration#defaultValidator` | `@ConditionalOnMissingBean (types: jakarta.validation.Validator) did not find any beans` |
| NO | `AopAutoConfiguration.AspectJAutoProxyingConfiguration` | `@ConditionalOnClass did not find required class 'org.aspectj.weaver.Advice'` (no está `spring-boot-starter-aspectj`) |
| NO | `AuditAutoConfiguration` (Actuator) | `@ConditionalOnBean (types: ...AuditEventRepository) did not find any beans` |
| NO | `TaskSchedulingConfigurations.TaskSchedulerConfiguration` | no existe `internalScheduledAnnotationProcessor`: nadie puso `@EnableScheduling` |

`/actuator/conditions` queda expuesto (`management.endpoints.web.exposure.include: health,conditions`) y devuelve lo mismo en JSON.

## 2. Cinco `@Value` → `@ConfigurationProperties` validada

- Antes: `antes.PagosConValue` con cinco `@Value`. Con `--pagos.reintentos=9` arranca sin aviso (`conValueElMismoValorInvalidoPasaSinAviso`).
- Después: `config.PagosProperties`, el `record` del README (`@NotBlank claveApi`, `@NotNull tiempoLimite`, `@Min(0) @Max(5) reintentos`) más `moneda` y `urlPasarela`.
- Con `--pagos.reintentos=9` el arranque falla: *"Binding validation errors on pagos"* con el campo `reintentos`.
- Sin la variable `PAGOS_CLAVE_API` el arranque también falla, como dice el README: *"Could not resolve placeholder 'PAGOS_CLAVE_API'"*.

## 3. Perfiles y precedencia

`application.yml` (reintentos 2), `application-dev.yml` (0), `application-test.yml` (1), `application-prod.yml` (3 y tiempo límite 2 s). La prueba lanza **procesos aparte** con variables de entorno reales:

| Entorno del proceso | `pagos.reintentos` efectivo |
|---|---|
| `SPRING_PROFILES_ACTIVE=dev` | 0 |
| `SPRING_PROFILES_ACTIVE=test` | 1 |
| `SPRING_PROFILES_ACTIVE=prod` | 3 (y `tiempoLimite=PT2S`) |
| `prod` + `PAGOS_REINTENTOS=5` | 5: la variable de entorno gana al archivo del perfil |
| `prod` + `PAGOS_REINTENTOS=5` + `--pagos.reintentos=4` | 4: el argumento gana a la variable |

## 4. Sustituir un bean autoconfigurado

`config.JsonConfig` declara `JsonMapper miJsonMapper()`. Resultado: solo existe `miJsonMapper`, y el reporte dice de `JacksonAutoConfiguration#jacksonJsonMapper`: *"@ConditionalOnMissingBean (types: tools.jackson.databind.json.JsonMapper) found beans of type 'tools.jackson.databind.json.JsonMapper' miJsonMapper"*.

Hallazgo: en Spring Boot 4 (Jackson 3) la autoconfiguración pregunta por `JsonMapper`. Un bean declarado como `ObjectMapper` **no** la retira: quedan dos (`unObjectMapperDeclaradoComoTalNoLaRetira`). En Spring Boot 3 el tipo era `ObjectMapper` de Jackson 2. Se aclaró en el enunciado.

## 5. Starter mínimo

`auditoria-starter`: `ClienteAuditoria`, `AuditoriaProperties` y la `AuditoriaAutoConfiguration` del README registrada en `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Sus pruebas con `ApplicationContextRunner` comprueban que crea el cliente con la URL configurada y que se retira si la aplicación declara el suyo. La aplicación solo agrega la dependencia y recibe `ClienteAuditoria` con `auditoria.url` de su `application.yml`.
