# Taller 05-05 — Clean Architecture: reservas de salas (solución)

`mvn test` ejecuta 14 pruebas: 10 de reglas sin Spring, el mismo contrato de repositorio contra el adaptador en memoria y contra el JPA (H2), el solapamiento leído de la base y la regla de dependencia con ArchUnit.

## Estructura

```
oficina.reservas
├── dominio          Reserva, Intervalo, SalaId, ReservaId, ReglaIncumplida   (sin imports de framework)
├── aplicacion       ReservarSala, CancelarReserva, ConsultarDisponibilidad
│   └── puertos      RepositorioReservas
├── adaptadores
│   └── persistencia
│       ├── memoria  RepositorioReservasEnMemoria
│       └── jpa      ReservaJpaEntity, ReservaJpaRepository, ReservaMapper, RepositorioReservasJpa
└── configuracion    ReservasConfig  (casos de uso como @Bean; las clases no tienen anotaciones)
```

## Reglas y dónde viven

| Regla | Dónde | Prueba |
|---|---|---|
| Sin solapamiento en la misma sala | `Reserva.nueva` con `Intervalo.seSolapaCon` (intervalo semiabierto: 9–10 y 10–11 no chocan) | `rechazaSolapamientoEnLaMismaSala`, `reservasContiguasNoSeSolapan`, `otraSalaNoChoca`, `unaReservaCanceladaLiberaElHorario` |
| Entre 30 min y 4 h (inclusive) | `Reserva.nueva` | `duracionMinimaYMaximaSonInclusivas`, `rechazaMenosDe30Minutos`, `rechazaMasDe4Horas` |
| Solo quien reservó cancela | `Reserva.cancelar` | `soloQuienReservoCancela` |
| Hasta una hora antes del inicio | `Reserva.cancelar` con reloj inyectado | `cancelaHastaUnaHoraAntes` (60 min se permite, 59 no) |

`ConsultarDisponibilidad` devuelve los huecos libres de una ventana (`disponibilidadDevuelveLosHuecos`).

Límite conocido: dos peticiones simultáneas pueden pasar la comprobación de solapamiento a la vez. En PostgreSQL se cierra con una restricción de exclusión (`EXCLUDE USING gist (sala WITH =, tsrange(inicio, fin) WITH &&)`) o bloqueando la sala; no se resolvió aquí porque el taller no lo pide.

## Costo medido

| | Clases |
|---|---|
| Total en `src/main` | 16 (incluye la clase `@SpringBootApplication`) |
| Dominio | 5 |
| Casos de uso y puerto | 4 |
| Traducción pura (no deciden nada) | 5: `ReservaJpaEntity`, `ReservaJpaRepository`, `ReservaMapper`, `RepositorioReservasJpa`, `ReservasConfig` |
| Adaptador en memoria | 1 |

Son 338 líneas, de las que unas 120 son traducción.

## Opinión: ¿lo justificaba?

Sí, de forma moderada. Las reglas son pocas pero sutiles (intervalos semiabiertos, límites inclusivos, una hora exacta), y poder probarlas en milisegundos con un reloj fijo pagó el costo: las 10 pruebas de reglas no necesitan base ni Spring. Lo que **no** se justificó fue una interfaz de entrada por caso de uso, y no se hizo. Si el módulo se quedara en reservar y cancelar sin reglas de horario, un CRUD en capas habría bastado.
