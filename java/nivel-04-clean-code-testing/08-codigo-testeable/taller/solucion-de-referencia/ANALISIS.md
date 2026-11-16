# Taller 08 — Código testeable: solución

Código: `Main.java` (se ejecuta con `java Main.java`; termina con código 1 si alguna comprobación falla).
Contiene la versión nueva, las comprobaciones del punto 3 y, renombrada a `ServicioTurnosOriginal`, la
versión de `taller/inicio` sin cambios para la demostración del punto 4.

## 1. Dependencias ocultas (siete)

| # | Dependencia | Dónde | Por qué impide probar |
|---|---|---|---|
| 1 | El reloj del sistema | `LocalDateTime.now()` | El resultado depende de la hora a la que corre la prueba: "fuera de horario" solo se puede probar de noche |
| 2 | La zona horaria por defecto de la JVM | `LocalDateTime.now()` sin zona | La misma prueba da otro resultado en un servidor en UTC que en un portátil en Bogotá |
| 3 | El azar | `UUID.randomUUID()` | El turno nunca es el mismo: no se puede comparar con un valor esperado |
| 4 | El estado global | `private static int contador` | Sobrevive entre instancias y entre pruebas: el número depende de lo que corrió antes |
| 5 | La pantalla de la sala | `new PantallaSala()` dentro del método | No se puede reemplazar ni observar qué se mostró |
| 6 | El envío de SMS | `new Sms()` dentro del método | Cada prueba mandaría un SMS real; tampoco se puede observar |
| 7 | La consola | `System.out.println("Fuera de horario")` y el `void` | El resultado no se devuelve: la única forma de saber qué pasó es leer la salida |

## 2. Refactor

- **Núcleo puro** (`ReglasDeTurno`): `jornadaPara(LocalTime)` decide mañana, tarde o fuera de horario;
  `codigo(jornada, numero, sufijo)` arma el texto. Reciben datos y devuelven datos.
- **Cáscara** (`ServicioTurnos`): recibe por constructor el `Clock` (que ya trae la zona), el `Numerador`,
  el proveedor de sufijos (`Supplier<String>`), la `Pantalla` y la `Mensajeria`. Lee, decide con el núcleo y
  produce los efectos. Devuelve `Optional<String>`: vacío si es fuera de horario. Quien llama decide cómo
  informarlo; el servicio ya no imprime.
- **El contador deja de ser `static`**: `Numerador` es un objeto por servicio.
- Las horas (07:00, 12:00, 17:00) son constantes con nombre.

## 3. Comprobaciones (resultado real)

`java Main.java` imprime `PASA` en todas: fuera de horario en 06:59, 17:00 y 23:30 (sin efectos y sin
consumir número); mañana en 07:00 y 11:59; tarde en 12:00 y 16:59; numeración consecutiva M1, M2 y T3
al cruzar el mediodía; y otro servicio vuelve a empezar en 1. Cada comprobación arma su propio `Escenario`
con un reloj fijo (`RelojAjustable`, 2026-03-02 en America/Bogota), sufijos fijos y espías a mano.

## 4. El orden importa en la versión original

`demostrarDependenciaDelOrdenEnElOriginal` ejecuta la misma comprobación ("el primer turno es M1") dos veces
sobre el código original: primero sola, y después de otra prueba que también asignó un turno. Resultado real:

```
   'el primer turno es M1' ejecutada primero:           PASA ([pantalla] Ana -> M1-8aed)
   la misma prueba, después de otra que asignó un turno: FALLA ([pantalla] Ana -> M3-d1f0)
```

Para que la demostración no dependa de la hora real (el original lee el reloj del sistema), se fija la zona
horaria de la JVM a una en la que en ese momento son las 9 de la mañana. Haber tenido que hacerlo es en sí
la prueba de la dependencia 2.

**Cómo se resolvió:** el contador vive en una instancia (`Numerador`) que se inyecta. Cada prueba crea su
servicio y su numerador, así que ninguna ve el estado de otra. La comprobación "otro servicio empieza en 1"
lo verifica.

## 5. Qué quedó sin probar

- **Los adaptadores reales**: `PantallaSala` y `Sms` (y en producción, el proveedor de SMS). Se cubren con
  una prueba de integración de cada adaptador contra el sistema real o un simulador (por ejemplo WireMock
  para un API de SMS), no con pruebas del servicio.
- **La raíz de composición** (el ensamblado con `Clock.system`, `UUID` y los adaptadores reales). `ensambladoReal`
  solo comprueba el formato del turno; un humo de arranque de la aplicación cubriría que todo se conecte.
- **La concurrencia**: `Numerador` usa `AtomicInteger`, pero ninguna comprobación ejecuta asignaciones en
  paralelo. Se cubriría con una prueba de varios hilos que asigne N turnos y verifique N números distintos.
- **Que el sufijo aleatorio no colisione**: con 4 caracteres hexadecimales hay 65.536 valores; es una
  decisión de producto, no algo que una prueba unitaria pueda asegurar.
