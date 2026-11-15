# Cambio 002: ampliar el límite de la consulta

## Motivo

Un cliente busca por referencias de pedido que llegan a 160 caracteres. Con el límite de 100 recibe un 400.

## Cambio en la especificación

- Criterio 8, antes: "Si `q` tiene más de 100 caracteres, entonces el sistema deberá responder 400…"
- Criterio 8, ahora: "Si `q` tiene más de 200 caracteres, entonces el sistema deberá responder 400…"

## Lo que no cambia

Todo lo demás. En particular, la forma del error.

## Pruebas afectadas

- `busqueda.feature`, escenario "rechaza una consulta demasiado larga": los límites pasan de 100/101 a 200/201.

## Riesgo

Ninguno para clientes existentes: se acepta más, no menos.
