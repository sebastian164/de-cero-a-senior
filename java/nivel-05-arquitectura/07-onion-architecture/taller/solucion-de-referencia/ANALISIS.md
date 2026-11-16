# Taller 05-07 — Onion Architecture: préstamos de biblioteca (solución)

Se eligió el dominio de biblioteca para no repetir el de transferencias. Módulo Maven **sin dependencias de compilación**: solo JUnit, AssertJ y ArchUnit en pruebas. `mvn test` ejecuta 7 pruebas, entre ellas `onionArchitecture()` de ArchUnit con los cuatro anillos.

## 1. Clasificación antes de programar

| Lógica | Anillo | Nota |
|---|---|---|
| Un ejemplar de referencia no se presta; un ejemplar prestado no se vuelve a prestar | Modelo (`Ejemplar`) | |
| Multa de 1.000 por día de retraso | Modelo (`Prestamo.devolver`) | |
| Plazo según tipo de socio (15 o 30 días) | Servicio de dominio (`PoliticaDePrestamos`) | **Dudosa**: podría ir en `TipoSocio`. Se dejó en la política porque el negocio la cambia junto con las otras reglas de préstamo |
| Máximo de préstamos según tipo de socio | Servicio de dominio | Involucra al socio y a sus préstamos vigentes |
| Socio con multas no puede pedir prestado | Servicio de dominio | **Dudosa**: es un dato del socio, pero la decisión es del préstamo |
| Interfaces de repositorio | Servicios de dominio (`Repositorios`) | Como lo pide Onion |
| Cargar socio, ejemplar y préstamos vigentes; guardar | Aplicación (`PrestarEjemplar`, `DevolverEjemplar`) | |
| Repositorios en memoria | Exterior (`infraestructura`) | |

## 2. Núcleo sin dependencias externas

`biblioteca.nucleo` (modelo + servicios) solo importa `java..` y a sí mismo; lo verifica `elNucleoSoloUsaElPropioNucleoYJava`.

## 3. Regla mal ubicada que se corrigió

La primera versión de `PrestarEjemplar` tenía `if (ejemplar.prestado()) throw ...`. Es una regla del ejemplar, no de la orquestación: se movió a `Ejemplar.marcarPrestado()`, junto con la de los ejemplares de referencia. Efecto visible: `elEjemplarProtegeSusReglas` la prueba sin repositorios ni servicio de aplicación.

## 4. Frente a la hexagonal de la lección 06

| Cambió de verdad | Es solo nombre |
|---|---|
| Las interfaces de repositorio pasaron a un anillo de dominio (en hexagonal eran puertos de la aplicación) | "Puertos de salida" frente a "interfaces de repositorio" |
| Aparece un anillo explícito para servicios de dominio sin estado | "Adaptadores" frente a "infraestructura" |
| No hay puerto de entrada: los servicios de aplicación son clases concretas | |

La regla de dependencias es la misma; lo que aporta Onion es el lugar explícito para `PoliticaDePrestamos`.

## 5. Convención para el equipo (cinco líneas)

1. Si la regla la explicaría un bibliotecario sin hablar de pantallas ni bases, va en el dominio.
2. Si afecta a un solo objeto, va en ese objeto; si cruza varios, en un servicio de dominio.
3. Un servicio de dominio no carga ni guarda: recibe los objetos ya cargados.
4. El servicio de aplicación carga, llama, guarda y publica; si aparece un `if` de negocio, sube un anillo.
5. Ante la duda, se elige el lugar donde la prueba necesita menos preparación.
