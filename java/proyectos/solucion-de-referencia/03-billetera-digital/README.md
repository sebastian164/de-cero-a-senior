# Proyecto 3: billetera digital con pruebas, solución de referencia

> **Intenta el proyecto antes de leer esto.** Ver la solución primero convierte el proyecto en lectura y no en práctica. Es *una* solución mínima y verificada, no *la* solución: la revisión de tu entrega mira tu diseño, tus decisiones y tu historial de commits.

Enunciado: [sección 3 de proyectos/README.md](../../README.md#3-billetera-digital-con-pruebas). Java, Maven, JUnit y AssertJ. El enunciado evalúa también tu historial de commits (rojo, verde, refactor): ese historial solo puede ser tuyo.

## Qué requisitos cubre y dónde

| Requisito del enunciado | Dónde está | Prueba que lo demuestra |
|---|---|---|
| Cuentas con saldo, depósitos, retiros y transferencias | `src/main/java/billetera/Billetera.java` (`Cuenta`) | `retiro_cobra_comision_y_registra_ambos_movimientos`, `transferencia_mueve_dinero_con_comision_para_quien_envia` |
| Comisiones por tipo de operación | `COMISION_RETIRO` (1 %) y `COMISION_TRANSFERENCIA` (0,5 %) | las dos pruebas anteriores |
| Topes diarios | `TOPE_DIARIO` y `Cuenta.exigirTopeYSaldo` | `tope_diario_se_reinicia_al_dia_siguiente_con_el_reloj_controlado` |
| Bloqueo por intentos fallidos | `Cuenta.autenticar` (3 PIN incorrectos) | `tres_pines_incorrectos_bloquean_aun_con_el_pin_correcto`, `un_acierto_reinicia_el_contador_de_fallos` |
| Historial de movimientos inmutable | `Cuenta.historial()` devuelve una copia inmodificable | `historial_es_inmutable_y_los_montos_invalidos_se_rechazan` |
| Todo lo que dependa del tiempo se prueba con un reloj controlado | `Cuenta` recibe un `Clock` | el reloj mutable de `BilleteraTest` |
| Manejo de errores con excepciones de dominio | `ErrorDeDominio`, `SaldoInsuficiente`, `TopeDiarioExcedido`, `CuentaBloqueada` | `saldo_insuficiente_incluye_la_comision_y_no_cambia_nada` |
| Casos límite | monto cero o negativo, saldo que cubre el monto pero no la comisión, PIN correcto después de fallos | `BilleteraTest` |

**Qué no cubre:** la extensión (conversión de moneda con un servicio externo simulado) ni la cobertura de mutación (puedes medirla con PIT).

## Cómo ejecutarla

Requiere JDK 21 y Maven. No necesita Docker.

```bash
mvn clean verify
java -cp target/classes billetera.Demo
```

`Demo` deposita, retira, transfiere, imprime el saldo y el historial, y muestra un error de dominio por un PIN incorrecto.

## Decisiones de diseño

Están en [DECISIONES.md](DECISIONES.md).
