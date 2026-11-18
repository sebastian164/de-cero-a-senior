# Solución de referencia: taller 01 (fundamentos de la nube)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué instalar

- Docker (Docker Desktop o Docker Engine con el plugin `compose`).
- [OpenTofu](https://opentofu.org/) 1.13 o superior (`tofu`).
- AWS CLI v2 (`aws`), `jq`, `python3` y `curl`.

Todo lo demás (LocalStack) lo levanta el propio script con Docker. No necesitas una cuenta de AWS ni gastas dinero.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./taller.sh
```

El script hace, en este orden:

1. **Levanta** LocalStack con `docker compose -p n10-01 -f compose.localstack.yaml up -d --wait` (puerto 4566).
2. Aplica `infra/` con OpenTofu (red de dos zonas, bucket, rol) y comprueba cada resultado con la AWS CLI.
3. Valida y planifica `infra-gestionada/` (ECS Fargate + balanceador + RDS Multi-AZ + presupuesto) y comprueba el plan en JSON. No se aplica: LocalStack Community no tiene esos servicios.
4. Ejecuta `costo.py`, la estimación mensual.
5. **Baja todo** al terminar (`tofu destroy` y `docker compose down -v`), incluso si algo falla.

Termina con código 0 solo si todas las comprobaciones pasan. Si el puerto 4566 está ocupado: `PUERTO_LOCALSTACK=4570 ./taller.sh`.

Para levantar y bajar LocalStack a mano: `docker compose -p n10-01 -f compose.localstack.yaml up -d --wait` y `docker compose -p n10-01 -f compose.localstack.yaml down -v`.

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
