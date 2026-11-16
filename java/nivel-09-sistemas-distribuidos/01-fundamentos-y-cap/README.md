# 01 — Fundamentos de sistemas distribuidos y CAP

## Idea

Coordinar una cena con amigos por mensajes de texto: algunos no responden, otros responden tarde, uno recibe el mensaje dos veces y otro tiene el reloj mal. Aun así hay que ponerse de acuerdo en la hora y el lugar. Un sistema distribuido es eso: varios computadores que deben parecer uno solo mientras se comunican por un medio poco fiable.

## Lo esencial

### Las falacias de la computación distribuida

Supuestos falsos que se cuelan en el diseño:

1. La red es fiable.
2. La latencia es cero.
3. El ancho de banda es infinito.
4. La red es segura.
5. La topología no cambia.
6. Hay un solo administrador.
7. El costo de transporte es cero.
8. La red es homogénea.

Una llamada local falla o funciona. Una llamada remota tiene un tercer resultado: **no sé**. Venció el tiempo de espera y no se sabe si la operación se ejecutó. Buena parte del diseño distribuido consiste en convivir con ese tercer estado.

### Teorema CAP

Ante una **partición** de red (nodos que no pueden comunicarse), un sistema debe elegir entre:

- **Consistencia (C):** todos los nodos ven los mismos datos; se rechazan operaciones si no se puede garantizar.
- **Disponibilidad (A):** todo nodo responde, aunque sea con datos posiblemente desactualizados.

La tolerancia a particiones (P) no es opcional: las particiones ocurren. La elección real es **CP o AP durante una partición**.

| | Durante una partición | Ejemplos de uso |
|---|---|---|
| CP | Rechaza o espera | Saldos, inventario crítico, coordinación |
| AP | Responde y reconcilia después | Carritos, contadores de "me gusta", catálogos |

**PACELC** completa la idea: incluso sin partición (*Else*), hay que elegir entre **latencia** y **consistencia**. Replicar de forma síncrona es consistente y lento; asíncrona es rápido y eventualmente consistente.

### Modelos de consistencia

| Modelo | Garantía |
|---|---|
| Fuerte (linealizable) | Toda lectura ve la última escritura |
| Lee tus escrituras | Un usuario ve sus propios cambios de inmediato |
| Eventual | Si no hay más cambios, todas las réplicas convergen |

### Otros conceptos base

- **Replicación:** líder-seguidores (escrituras al líder), multilíder, sin líder (quórum).
- **Particionamiento (*sharding*):** repartir los datos entre nodos por una clave.
- **Consenso:** que varios nodos acuerden un valor aunque algunos fallen (Raft, Paxos). Lo usan etcd, ZooKeeper y los brokers para elegir líder.
- **Tiempo:** los relojes de máquinas distintas no coinciden. No ordenes eventos entre nodos por marca de tiempo.

## Ejemplo

Un usuario actualiza su perfil y recarga la página. La escritura fue al líder; la lectura, a una réplica que aún no recibió el cambio. Ve sus datos anteriores y cree que el sistema falló. Soluciones: leer del líder durante unos segundos tras escribir, dirigir a ese usuario siempre a la misma réplica, o enviar al cliente la versión esperada.

## Errores comunes

- Diseñar llamadas remotas como si fueran locales.
- Sin tiempos límite: un servicio lento detiene a todos los que lo llaman.
- Asumir consistencia inmediata al leer de réplicas.
- Ordenar por marcas de tiempo de máquinas distintas.
- Distribuir un sistema que cabía en una máquina.
- Decir "elegimos CA".

## Taller

1. Para cada dato de una tienda en línea (saldo de la billetera, existencias, carrito, reseñas, precio, historial de pedidos), decide CP o AP y justifica con el costo de equivocarse.
2. Describe tres situaciones en las que un tiempo de espera vencido deja al cliente sin saber si la operación ocurrió, y qué haría cada parte.
3. Monta PostgreSQL con una réplica asíncrona (Docker Compose) y reproduce una lectura desactualizada. Propón dos soluciones.
4. Explica el problema de los relojes con un ejemplo concreto de dos servidores.
5. Calcula la disponibilidad de un sistema con tres componentes en serie al 99,9 % y de uno con dos réplicas redundantes al 99 %.

Rama `taller/n9-01-fundamentos`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Explica CAP sin decir "elige dos de tres".
2. ¿Qué agrega PACELC?
3. ¿Qué es la consistencia eventual y cómo afecta la experiencia del usuario?
4. ¿Por qué no se puede confiar en los relojes para ordenar eventos?
5. ¿Qué diferencia hay entre un fallo y una respuesta lenta, visto desde quien llama?
6. ¿Cuándo vale la pena distribuir?
