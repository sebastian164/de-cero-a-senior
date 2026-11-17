# Cambio 006 (urgencia): el espacio duro cuenta como espacio

## Motivo

Los teclados móviles insertan un espacio duro (U+00A0) tras algunos signos. Una nota `reunión #trabajo` con ese espacio no reconocía la etiqueta.
Arreglado en producción en el commit "URGENCIA". Este documento y los cambios siguientes cierran la urgencia.

## Cambio en la especificación

- Definiciones, "Comienzo de una palabra": también cuenta después de un espacio duro.

## Pruebas afectadas

- Nuevo escenario en `@criterio-1`: texto con espacio duro antes del `#`.

## Riesgo

Ninguno para clientes existentes: se reconocen más etiquetas, no menos.
