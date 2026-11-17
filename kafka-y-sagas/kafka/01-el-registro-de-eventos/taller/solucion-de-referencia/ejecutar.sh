#!/usr/bin/env bash
# Taller Kafka 01: ejecuta los diez pasos y comprueba cada afirmación de ANALISIS.md.
# Pasos 1-9: el entorno del curso (kafka-y-sagas/entorno/compose.yaml) sin puerto publicado (sin-puertos.yaml).
# Paso 8 (segunda parte) y paso 10: el compose.yaml propio de esta carpeta.
# Uso: bash ejecutar.sh   (tarda unos cinco minutos; deja la salida cruda de cada paso en salida/)
set -uo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
E=$(cd "$AQUI/../../../../entorno" && pwd)
SP=$AQUI/sin-puertos.yaml
S=$AQUI/salida; rm -rf "$S"; mkdir -p "$S"
FALLOS=0
T=$'\t'
ok()   { echo "   ok    $1"; }
mal()  { echo "   FALLA $1"; FALLOS=$((FALLOS+1)); }
check(){ if eval "$2"; then ok "$1"; else mal "$1"; fi; }

ENT="docker compose -p pub-kafka-y-sagas-k01 -f $E/compose.yaml -f $SP"
PROPIO="docker compose -p pub-kafka-y-sagas-k01p -f $AQUI/compose.yaml"
k()  { $ENT exec -T kafka "$@"; }
kb() { $ENT exec -T kafka bash -c "$1"; }
B="--bootstrap-server localhost:9092"; P=/opt/kafka/bin

$ENT down -v >/dev/null 2>&1
$ENT up -d --wait >/dev/null 2>&1 || { echo "no levantó el entorno"; exit 1; }

echo "1. Tema pedidos con tres particiones"
k $P/kafka-topics.sh $B --create --topic pedidos --partitions 3 --replication-factor 1 > "$S/01-crear.txt"
k $P/kafka-topics.sh $B --describe --topic pedidos | tee "$S/01-describir.txt"
check "tres particiones, líder 1, Isr 1" "[ \$(grep -c 'Leader: 1.*Isr: 1' $S/01-describir.txt) -eq 3 ]"

echo "2. Diez mensajes sin clave"
kb "seq 1 10 | sed 's/^/sin-clave-/' | $P/kafka-console-producer.sh $B --topic pedidos"
kb "$P/kafka-console-consumer.sh $B --topic pedidos --from-beginning --timeout-ms 6000 --formatter-property print.partition=true --formatter-property print.offset=true 2>/dev/null" | tee "$S/02-sin-clave.txt"
check "llegan los diez" "[ \$(grep -c sin-clave $S/02-sin-clave.txt) -eq 10 ]"
echo "   reparto: $(grep -o 'Partition:[0-9]' $S/02-sin-clave.txt | sort | uniq -c | tr '\n' ' ')"

echo "3. Diez mensajes con tres claves"
kb "printf 'cliente-1:a1\ncliente-2:b1\ncliente-3:c1\ncliente-1:a2\ncliente-2:b2\ncliente-3:c2\ncliente-1:a3\ncliente-2:b3\ncliente-3:c3\ncliente-1:a4\n' | $P/kafka-console-producer.sh $B --topic pedidos --reader-property parse.key=true --reader-property key.separator=:"
kb "$P/kafka-console-consumer.sh $B --topic pedidos --from-beginning --timeout-ms 6000 --formatter-property print.key=true --formatter-property print.partition=true --formatter-property print.offset=true 2>/dev/null | grep cliente-" | tee "$S/03-con-clave.txt"
for c in 1 2 3; do
  check "cliente-$c siempre en la misma partición" "[ \$(grep \"cliente-$c\${T}\" $S/03-con-clave.txt | cut -f1 | sort -u | wc -l) -eq 1 ]"
done
grep '^Partition' "$S/03-con-clave.txt" | awk -F'\t' '{print $3, $1}' | sort -u > "$S/03-mapa-3-particiones.txt"

echo "4. Dos consumidores sin grupo explícito"
kb "$P/kafka-console-consumer.sh $B --topic pedidos --from-beginning --timeout-ms 6000 > /tmp/c1.txt 2>/dev/null & $P/kafka-console-consumer.sh $B --topic pedidos --from-beginning --timeout-ms 6000 > /tmp/c2.txt 2>/dev/null; wait; wc -l < /tmp/c1.txt; wc -l < /tmp/c2.txt" | tee "$S/04-dos-consumidores.txt"
check "cada uno recibe los 20" "[ \"\$(tr '\n' ' ' < $S/04-dos-consumidores.txt)\" = '20 20 ' ]"
k $P/kafka-consumer-groups.sh $B --list 2>/dev/null | tee "$S/04-grupos.txt"
check "cada consola creó su propio grupo console-consumer-*" "[ \$(grep -c console-consumer $S/04-grupos.txt) -ge 2 ]"

echo "5. Partición 1 desde la posición 2"
kb "$P/kafka-console-consumer.sh $B --topic pedidos --partition 1 --offset 2 --timeout-ms 5000 --formatter-property print.partition=true --formatter-property print.offset=true 2>/dev/null" | tee "$S/05-particion-1.txt"
check "todo lo leído es de la partición 1 y empieza en la posición 2" "head -1 $S/05-particion-1.txt | grep -q 'Partition:1.Offset:2' && ! grep -q 'Partition:[02]' $S/05-particion-1.txt"

echo "6. Dos claves alternadas con un consumidor leyendo"
k $P/kafka-topics.sh $B --create --topic alternadas --partitions 3 --replication-factor 1 >/dev/null
kb "($P/kafka-console-consumer.sh $B --topic alternadas --timeout-ms 12000 --formatter-property print.key=true --formatter-property print.partition=true > /tmp/alt.txt 2>/dev/null &) ; sleep 6; for i in 1 2 3 4 5; do printf \"cliente-1:x\$i\ncliente-2:y\$i\n\"; done | $P/kafka-console-producer.sh $B --topic alternadas --reader-property parse.key=true --reader-property key.separator=: ; sleep 9; cat /tmp/alt.txt" | tee "$S/06-alternadas.txt"
check "x1..x5 llegan en orden" "[ \"\$(grep -o 'x[0-9]' $S/06-alternadas.txt | tr -d '\n')\" = x1x2x3x4x5 ]"
check "y1..y5 llegan en orden" "[ \"\$(grep -o 'y[0-9]' $S/06-alternadas.txt | tr -d '\n')\" = y1y2y3y4y5 ]"

echo "7. De tres a seis particiones"
k $P/kafka-topics.sh $B --alter --topic pedidos --partitions 6
kb "printf 'cliente-1:a5\ncliente-2:b5\ncliente-3:c5\n' | $P/kafka-console-producer.sh $B --topic pedidos --reader-property parse.key=true --reader-property key.separator=:"
kb "$P/kafka-console-consumer.sh $B --topic pedidos --from-beginning --timeout-ms 6000 --formatter-property print.key=true --formatter-property print.partition=true 2>/dev/null | grep -E '(a5|b5|c5)$'" | awk -F'\t' '{print $2, $1}' | sort | tee "$S/07-mapa-6-particiones.txt"
echo "   antes:"; sed 's/^/     /' "$S/03-mapa-3-particiones.txt"
CAMBIARON=$(join <(sort "$S/03-mapa-3-particiones.txt") <(sort "$S/07-mapa-6-particiones.txt") | awk '$2!=$3' | wc -l | tr -d ' ')
echo "   claves que cambiaron de partición: $CAMBIARON" | tee "$S/07-cambiaron.txt"
check "al menos una clave cambió de partición" "[ $CAMBIARON -ge 1 ]"

echo "8. Retención de un minuto en el entorno del curso"
k $P/kafka-topics.sh $B --create --topic efimero --partitions 1 --replication-factor 1 --config retention.ms=60000 >/dev/null
kb "seq 1 5 | sed 's/^/efimero-/' | $P/kafka-console-producer.sh $B --topic efimero"
echo "   esperando 75 s"; sleep 75
kb "$P/kafka-console-consumer.sh $B --topic efimero --from-beginning --timeout-ms 5000 2>/dev/null" | tee "$S/08-tras-75s.txt"
check "a los 75 s los cinco siguen ahí (segmento activo y revisión cada 5 min)" "[ \$(grep -c efimero- $S/08-tras-75s.txt) -eq 5 ]"
$ENT down -v >/dev/null 2>&1

echo "8b. Lo mismo con segmentos de 10 s y revisión cada 5 s (compose propio)"
$PROPIO down -v >/dev/null 2>&1
CHEQUEO_RETENCION_MS=5000 $PROPIO up -d --wait >/dev/null 2>&1 || { echo "no levantó el compose propio"; exit 1; }
kp() { $PROPIO exec -T kafka bash -c "$1"; }
BP="--bootstrap-server kafka:19092"
kp "$P/kafka-topics.sh $BP --create --topic efimero --partitions 1 --replication-factor 1 --config retention.ms=60000 --config segment.ms=10000 >/dev/null; seq 1 5 | sed 's/^/efimero-/' | $P/kafka-console-producer.sh $BP --topic efimero"
sleep 15
kp "echo efimero-6 | $P/kafka-console-producer.sh $BP --topic efimero"   # abre un segmento nuevo: el primero queda cerrado
echo "   esperando hasta 120 s a que se borre el segmento viejo"
for i in $(seq 1 24); do
  N=$(kp "$P/kafka-console-consumer.sh $BP --topic efimero --from-beginning --timeout-ms 4000 2>/dev/null" | grep -c efimero-)
  [ "$N" -lt 6 ] && break; sleep 5
done
kp "$P/kafka-console-consumer.sh $BP --topic efimero --from-beginning --timeout-ms 4000 2>/dev/null" | tee "$S/08b-borrado.txt"
check "el segmento cerrado se borró y queda solo el mensaje del activo" "[ \$(grep -c efimero- $S/08b-borrado.txt) -lt 6 ]"
$PROPIO logs kafka 2>/dev/null | grep -i -E "deleting segment|retention" | grep efimero | head -3 > "$S/08b-registro-broker.txt"

echo "9. Tema compactado"
kp "$P/kafka-topics.sh $BP --create --topic saldos --partitions 1 --replication-factor 1 --config cleanup.policy=compact --config segment.ms=5000 --config min.cleanable.dirty.ratio=0.01 --config delete.retention.ms=5000 >/dev/null
printf 'cuenta-7:100\ncuenta-7:200\ncuenta-7:300\ncuenta-7:400\ncuenta-7:500\ncuenta-9:50\ncuenta-7:NULL\n' | $P/kafka-console-producer.sh $BP --topic saldos --reader-property parse.key=true --reader-property key.separator=: --reader-property null.marker=NULL"
kp "$P/kafka-console-consumer.sh $BP --topic saldos --from-beginning --timeout-ms 4000 --formatter-property print.key=true --formatter-property print.offset=true 2>/dev/null" > "$S/09-antes.txt"
sleep 7; kp "echo 'cuenta-1:1' | $P/kafka-console-producer.sh $BP --topic saldos --reader-property parse.key=true --reader-property key.separator=:"   # cierra el segmento
echo "   esperando hasta 120 s a la compactación"
for i in $(seq 1 24); do
  N=$(kp "$P/kafka-console-consumer.sh $BP --topic saldos --from-beginning --timeout-ms 4000 --formatter-property print.key=true 2>/dev/null" | grep -c 'cuenta-7')
  [ "$N" -le 1 ] && break; sleep 5
done
kp "$P/kafka-console-consumer.sh $BP --topic saldos --from-beginning --timeout-ms 4000 --formatter-property print.key=true --formatter-property print.offset=true --formatter-property print.value=true 2>/dev/null" | tee "$S/09-despues.txt"
check "antes de compactar estaban los seis de cuenta-7" "[ \$(grep -c cuenta-7 $S/09-antes.txt) -eq 6 ]"
check "después queda como mucho la lápida de cuenta-7" "[ \$(grep -c cuenta-7 $S/09-despues.txt) -le 1 ] && ! grep -q -E \"cuenta-7\${T}[1-5]00\" $S/09-despues.txt"
check "cuenta-9 conserva su último valor" "grep -q \"cuenta-9\${T}50\" $S/09-despues.txt"

echo "10. down y up: con el entorno del curso se pierde; con el compose propio se conserva"
$ENT up -d --wait >/dev/null 2>&1; k $P/kafka-topics.sh $B --create --topic persistente --partitions 1 --replication-factor 1 >/dev/null
$ENT down >/dev/null 2>&1; $ENT up -d --wait >/dev/null 2>&1
k $P/kafka-topics.sh $B --list | tee "$S/10-curso-tras-down.txt"
check "con el entorno del curso, el tema desapareció" "! grep -q persistente $S/10-curso-tras-down.txt"
$ENT down -v >/dev/null 2>&1
kp "$P/kafka-topics.sh $BP --create --topic persistente --partitions 1 --replication-factor 1 >/dev/null; echo dato-que-sobrevive | $P/kafka-console-producer.sh $BP --topic persistente"
$PROPIO down >/dev/null 2>&1; $PROPIO up -d --wait >/dev/null 2>&1
kp "$P/kafka-topics.sh $BP --list; $P/kafka-console-consumer.sh $BP --topic persistente --from-beginning --timeout-ms 4000 2>/dev/null" | tee "$S/10-propio-tras-down.txt"
check "con el compose propio, tema y mensaje siguen" "grep -q persistente $S/10-propio-tras-down.txt && grep -q dato-que-sobrevive $S/10-propio-tras-down.txt"
$PROPIO down -v >/dev/null 2>&1

echo; [ $FALLOS -eq 0 ] && echo "Kafka 01: todo correcto" || echo "Kafka 01: $FALLOS comprobaciones fallaron"
exit $FALLOS
