# Solución de referencia: taller 07

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

Necesita JDK 21 y Maven. El dominio de la tienda (el del módulo hexagonal) no se copia: el `pom.xml` lo toma de `hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src` por ruta relativa, así que ejecútala dentro del repositorio del curso. Las pruebas usan dobles en memoria y no necesitan Docker.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export PATH=$JAVA_HOME/bin:$PATH   # o tu JDK 21
cd webflux/07-el-banco-hexagonal-reactivo/taller/solucion-de-referencia
mvn test                                  # 19 pruebas
```

Para ejecutarla contra PostgreSQL:

Levanta el entorno del curso (desde la raíz del repositorio):

```bash
docker compose -f webflux/entorno/compose.yaml up -d --wait
```

```bash
cd webflux/07-el-banco-hexagonal-reactivo/taller/solucion-de-referencia
docker compose -f ../../../entorno/compose.yaml exec -T postgres psql -U banco -d banco -c "create database pedidos07"
for f in esquema.sql datos.sql; do docker compose -f ../../../entorno/compose.yaml exec -T postgres psql -q -v ON_ERROR_STOP=1 -U banco -d pedidos07 < $f; done
mvn -q package -DskipTests
java -jar target/t07-solucion-1.0.0.jar --opcion=c        # a | b | c
curl -X POST localhost:8080/pedidos/m1/confirmar
```

El razonamiento, las mediciones y las decisiones están en [ANALISIS.md](ANALISIS.md).
