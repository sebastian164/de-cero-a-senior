# Taller 12-04: deuda técnica y estimación (solución)

Sistema: el servicio `pedidos` y su entorno (ruta Java). Los hallazgos técnicos (Trivy, sondas, Secret, pruebas existentes) son medidos; las cifras de incidentes e intereses en días son del caso ilustrativo. Los cálculos están en `calcular.py` (salida en `resultado.txt`).

## 1. Registro de deuda (ocho elementos)

| # | Deuda | Dónde | Interés (costo de convivir) | Capital | Frecuencia de cambio | Dificultad |
|---|---|---|---|---|---|---|
| 1 | Una sola prueba de capa web; sin pruebas de integración con la base | `pedidos` (src/test) | Cada cambio en repositorios se prueba a mano contra Postgres (media hora); riesgo de regresión | 1 semana | Alta | Media |
| 2 | Migraciones que no se prueban hacia atrás ni con datos | `db/migration` | Un despliegue puede dejar el esquema incompatible con la versión anterior; sin reversa posible | 3 días | Media | Media |
| 3 | La contraseña de la base viaja en un `Secret` sin cifrado en reposo | Kubernetes | Riesgo de seguridad; no hay costo diario | 1 semana (gestor de secretos) | Baja | Alta |
| 4 | Sin tableros ni alertas del servicio | Operación | Los incidentes se detectan por quejas | 1 semana | Alta | Baja |
| 5 | Imagen con dependencias vulnerables (snappy-java y lz4-java, hallado con Trivy en 10-02) | `pom.xml` | Riesgo creciente; hay que fijar versiones a mano | 2 horas | Alta | Baja |
| 6 | Valor de `preStop` y de sondas ajustado por prueba y error | Deployment | Cada clúster nuevo exige repetir la medición | 2 días (probar en CI) | Media | Baja |
| 7 | Nombres de variables en español e inglés mezclados | varios | Casi nulo | 2 días | Baja | Baja |
| 8 | Sesión local por defecto (no es sin estado sin el perfil `redis`) | configuración | No escala horizontalmente sin acordarse del perfil | 1 día | Baja | Baja |

## 2. Matriz frecuencia × dificultad

| | Se toca poco | Se toca mucho |
|---|---|---|
| **Fácil** | 7 (cuando se pase por ahí), 8 | **Ya:** 5, 4, 6 |
| **Difícil** | 3 (planificar con fecha por seguridad) | **Planificar e invertir:** 1, 2 |

**No voy a pagar:** el 7 (nombres mezclados): interés casi nulo, y renombrar sin motivo crea ruido en el historial.

## 3. Propuesta para negocio: pruebas del servicio de pedidos (deuda 1)

> **Asunto: una semana de pruebas automáticas para publicar con confianza.**
>
> Hoy, cada cambio en cómo guardamos pedidos requiere media hora de comprobaciones manuales y, aun así, el año pasado dos cambios llegaron con errores a los clientes. Proponemos una semana de trabajo para que esas comprobaciones corran solas en cada cambio. **Costo:** una persona durante una semana. **Beneficio:** cada cambio futuro ahorra media hora de comprobaciones manuales; con los cuatro cambios previstos este año son 2 horas, que por sí solas no pagan una semana (40 horas). Lo que sí la justifica es el riesgo: los dos errores que llegaron a clientes el año pasado se habrían detectado antes de publicar, y cada cambio nuevo en esa zona queda protegido desde el primer día. **Riesgo:** una semana menos de funcionalidades nuevas; si no lo hacemos, el riesgo de otro error en producción sigue igual.

## 4. Estimación de una funcionalidad real: "estado del pedido con eventos"

Salida de `calcular.py` (días-persona, tres valores por parte):

| Parte | Optimista | Probable | Pesimista | Esperado |
|---|---|---|---|---|
| Migración compatible hacia atrás | 0,5 | 1 | 2 | 1,08 |
| Modelo y transiciones con pruebas | 2 | 3 | 6 | 3,33 |
| Publicar evento en Kafka (Outbox) | 2 | 4 | 9 | 4,50 |
| Endpoint de consulta y autorización | 1 | 2 | 4 | 2,17 |
| Pruebas de integración y de carga | 2 | 3 | 6 | 3,33 |
| Despliegue, tableros y runbook | 1 | 2 | 4 | 2,17 |
| Revisión y correcciones | 1 | 2 | 3 | 2,00 |
| **Total** | | 17 | | **18,6 (desv. 1,7)** |

**Respuesta:** entre 17 y 22 días-persona; 18,6 es el valor esperado; con 84 % de confianza, 20,3.

**Supuestos:** el patrón Outbox ya existe en el proyecto; hay un entorno de pruebas con Kafka; una persona dedicada, sin interrupciones.
**Riesgos que moverían la cifra:** el contrato del evento se discute con otro equipo (+3 días); la base tiene datos que migrar (+2); el equipo de seguridad exige revisión (+2). **Primero un *spike*** de medio día sobre Outbox con la versión actual de Spring.

## 5. Estimaciones pasadas frente a lo real

Datos: **ficticios**, declarados así, porque la solución de referencia no tiene historial personal; el tuyo sí. El método es el que importa.

| Tarea | Estimado | Real | Razón |
|---|---|---|---|
| Importador de archivos | 5 d | 9 d | 1,80 |
| Reporte mensual | 8 d | 11 d | 1,38 |
| Integración con la pasarela | 10 d | 19 d | 1,90 |

**Sesgo sistemático:** razón media geométrica 1,68; se subestima cerca de un 68 %, sobre todo lo que depende de un sistema externo (la pasarela) y de datos reales (el importador). Se olvidan integración, pruebas con datos reales y esperas. Corrección: estimar con tres valores y multiplicar por 1,5 el trabajo con dependencias externas.

## 6. Aviso de una fecha que no se cumplirá

> **Asunto: el estado de pedidos no llegará el 5 de diciembre; opciones.**
>
> Equipo: con lo que sabemos hoy, el estado de pedidos con eventos estará listo entre el 12 y el 19 de diciembre, no el 5. Lo vimos al terminar la parte de eventos: la integración con el otro equipo tarda más de lo previsto (el contrato aún no está cerrado). Les aviso hoy, con tres semanas de margen, para que decidamos juntos.
>
> **Opciones:**
> 1. **Recortar alcance:** el 5 de diciembre entregamos el estado y la consulta; los eventos llegan después. (Cumple la fecha; el equipo de reportes debe esperar los eventos.)
> 2. **Mover la fecha** al 19 de diciembre con todo el alcance.
> 3. **Añadir una persona**: no recomendada; el tiempo de incorporación consume lo que ahorra.
>
> **Recomiendo la 1.** Necesito una respuesta antes del viernes. Actualizo la estimación cada semana.
