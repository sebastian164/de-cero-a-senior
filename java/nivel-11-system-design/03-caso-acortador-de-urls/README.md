# 03 — Caso: acortador de URLs

Un problema pequeño en apariencia, con buenas decisiones escondidas: generación de identificadores, lecturas masivas y caché.

## 1. Requisitos

**Funcionales**

- Dada una URL larga, devolver una corta.
- Al visitar la corta, redirigir a la larga.
- Opcional: alias personalizado, expiración, estadísticas de clics.

**No funcionales**

- Muchísimas más lecturas que escrituras (del orden de 100 a 1).
- La redirección debe ser rápida (decenas de milisegundos) y altamente disponible.
- Un enlace creado no debe perderse.
- Los códigos no deben ser adivinables en secuencia.

**Fuera de alcance:** cuentas de usuario avanzadas, moderación.

## 2. Estimación

- 100 millones de URLs nuevas al mes: unas 40 escrituras por segundo.
- Con proporción 100 a 1: unas 4.000 lecturas por segundo; pico de 20.000.
- En cinco años: 6.000 millones de registros. A 500 bytes cada uno, unos 3 TB.
- Longitud del código: con 62 caracteres (a-z, A-Z, 0-9), 7 posiciones dan 62⁷ ≈ 3,5 billones de combinaciones. Suficiente.

## 3. API

```
POST /v1/urls        { "url": "https://…", "alias": "opcional", "expiraEn": "opcional" }
                     → 201 { "corta": "https://c.example/aZ3kP9x" }

GET  /{codigo}       → 301 o 302 con Location: <url larga>
                     → 404 si no existe o expiró
```

**301 o 302:** con 301 (permanente) el navegador guarda la redirección y no vuelve a consultar: menos carga, pero se pierden las estadísticas. Con 302 (temporal) cada visita pasa por el servicio. Se elige según si importan las métricas.

## 4. Datos

```
urls( codigo PK, url_larga, creado_en, expira_en, usuario_id )
```

El acceso es por clave: `codigo → url_larga`. No hay uniones. Encaja en un almacén clave-valor o en una tabla relacional con índice sobre la clave primaria. Con 40 escrituras por segundo, una base relacional basta al principio.

## 5. La decisión central: cómo generar el código

| Estrategia | Cómo | A favor | En contra |
|---|---|---|---|
| *Hash* de la URL (primeros 7 caracteres) | MD5 o SHA y truncar | Sin estado; misma URL, mismo código | Colisiones: hay que detectarlas y reintentar |
| Contador global en base 62 | Un número secuencial convertido | Sin colisiones; corto | Predecible; el contador es un punto de contención |
| Rangos de contador por instancia | Cada instancia reserva un bloque de números | Sin contención por petición | Coordinación para repartir rangos |
| Aleatorio con comprobación | Generar y verificar unicidad | Impredecible | Una lectura extra; colisiones crecen con el llenado |

Elección razonable: **rangos de contador** (cada instancia pide un bloque de, por ejemplo, 10.000 números a un coordinador) y codificación en base 62, con una permutación reversible del número para que los códigos consecutivos no se parezcan. Ojo: una permutación afín (`n·A + B` módulo 62⁷) oculta la adyacencia pero es lineal: dos códigos consecutivos delatan el paso y, con él, todos los demás. Si los códigos deben ser impredecibles de verdad, usa la estrategia aleatoria o una permutación con clave secreta.

```java
static String aBase62(long numero) {
    String alfabeto = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    StringBuilder codigo = new StringBuilder();
    do {
        codigo.append(alfabeto.charAt((int) (numero % 62)));
        numero /= 62;
    } while (numero > 0);
    return codigo.reverse().toString();
}
```

## 6. Alto nivel

```
Cliente → CDN / balanceador → Servicio de redirección → Caché → Base de datos
                            → Servicio de creación ───────────→ Base de datos
                                      │
                                      └→ cola → procesamiento de estadísticas
```

- **Lectura:** se consulta la caché; si no está, la base, y se guarda. La mayoría del tráfico se concentra en pocos enlaces populares, así que la caché absorbe casi todo.
- **Escritura:** se genera el código y se inserta. Poco volumen.
- **Estadísticas:** cada clic publica un evento en una cola; no se escribe en la base durante la redirección.
- Lectura y escritura se separan porque escalan de forma muy distinta.

## 7. Profundización

- **Caché:** tiempo de vida largo (los enlaces casi no cambian), expulsión por uso menos reciente. También se guardan los "no existe" por poco tiempo.
- **Disponibilidad:** varias instancias sin estado, réplicas de lectura, caché distribuida.
- **Si la carga crece diez veces:** particionar por *hash* del código; cada consulta va a una sola partición.
- **Expiración:** eliminación perezosa al leer y un proceso periódico de limpieza.
- **Abuso:** límite de tasa en la creación, validación de la URL, listas de bloqueo.
- **Alias personalizado:** restricción única; conflicto devuelve `409`.

## Compromisos tomados

| Decisión | Se gana | Se cede |
|---|---|---|
| 302 en lugar de 301 | Estadísticas | Más tráfico al servicio |
| Contador por rangos | Sin colisiones ni contención | Un componente coordinador |
| Estadísticas asíncronas | Redirección rápida | Los conteos llegan con retraso |
| Caché con vida larga | Latencia mínima | Cambios en un enlace tardan en verse |

## Taller

1. Implementa el servicio con Spring Boot: creación, redirección, expiración y alias.
2. Implementa dos estrategias de generación y compáralas: longitud del código, colisiones y predecibilidad.
3. Agrega la caché y mide la latencia de redirección con y sin ella bajo carga.
4. Agrega el conteo de clics de forma asíncrona.
5. Escribe el documento de diseño con el método de los seis pasos, con tus propias estimaciones.
6. Responde: ¿qué cambiaría si las URLs pudieran editarse después de creadas?

Rama `taller/n11-03-acortador`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Por qué separar el camino de lectura del de escritura?
2. Compara las estrategias de generación de códigos.
3. 301 frente a 302 y su efecto en el diseño.
4. ¿Cómo evitarías que alguien enumere todos los enlaces?
5. ¿Dónde está el punto único de fallo de tu diseño?
