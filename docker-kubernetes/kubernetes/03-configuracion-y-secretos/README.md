# 03 — Configuración y secretos

## Idea

Un actor y su guion. El mismo actor puede hacer la función de la tarde y la de la noche; lo que cambia es el papel que le entregan antes de salir. Si para cambiar una línea del guion hubiera que contratar a otro actor, el teatro quebraría. La imagen es el actor; la configuración es el guion.

## Lo esencial

La imagen es la misma en todos los ambientes. Lo que cambia entra desde fuera.

| Objeto | Para qué | Cuidado |
|---|---|---|
| **ConfigMap** | Configuración no sensible: direcciones, niveles de registro, banderas | Cualquiera con acceso de lectura al espacio de nombres lo ve |
| **Secret** | Contraseñas, llaves, certificados | Va codificado en base64, **no cifrado**. Base64 se revierte con una orden |

### Dos formas de entregarla al contenedor

| Forma | Cómo llega | Cuando el objeto cambia |
|---|---|---|
| Variables de entorno (`env`, `envFrom`) | Se leen al arrancar el proceso | **No se actualizan.** Hay que recrear el pod |
| Archivos montados (`volumes`) | Cada clave es un archivo | Se actualizan solos al cabo de un rato, pero la aplicación tiene que volver a leerlos |

### Qué hace seguro a un Secret

Nada, por sí solo. Lo protegen cuatro cosas alrededor:

1. Que **no** esté en el repositorio en claro.
2. Permisos: quién puede leer Secrets en ese espacio de nombres.
3. Cifrado en reposo activado en el clúster.
4. Un gestor externo (el del proveedor de nube, Vault) que sea la fuente real, con un operador que lo sincroniza.

## Ejemplo

### Antes

```yaml
containers:
  - name: pedidos
    image: ghcr.io/ejemplo/pedidos:2.3.1-produccion
    env:
      - name: DB_URL
        value: jdbc:postgresql://db.produccion.interna:5432/pedidos
      - name: DB_PASSWORD
        value: Pr0duccion!2026
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Una imagen por ambiente (`-produccion`) | Lo que se probó no es lo que se despliega |
| Dirección escrita en el Deployment | Hay un Deployment distinto por ambiente y se desincronizan |
| Contraseña en claro en el manifiesto | Está en el repositorio, en su historial y en la pantalla de quien haga `kubectl get -o yaml` |

### Después

<!-- file: ejemplo/configmap.yaml -->
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pedidos-config
data:
  DB_URL: jdbc:postgresql://db:5432/pedidos
  NIVEL_REGISTRO: INFO
```

<!-- file: ejemplo/deployment.yaml -->
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedidos
spec:
  replicas: 2
  selector:
    matchLabels:
      app: pedidos
  template:
    metadata:
      labels:
        app: pedidos
    spec:
      containers:
        - name: pedidos
          image: ghcr.io/ejemplo/pedidos:2.3.1
          envFrom:
            - configMapRef:
                name: pedidos-config
          env:
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: pedidos-db
                  key: password
```

El Secret no se guarda en el repositorio. Se crea aparte:

```bash
kubectl create secret generic pedidos-db --from-literal=password="$(openssl rand -base64 24)"
kubectl get secret pedidos-db -o jsonpath='{.data.password}' | base64 -d    # se lee sin esfuerzo
```

- Una sola imagen. Cada ambiente tiene su ConfigMap y su Secret con los mismos nombres.
- El Deployment es idéntico en todos los ambientes.

## Errores comunes

- Subir un Secret al repositorio "porque está en base64".
- Cambiar un ConfigMap y esperar que los pods lo noten si lo leen por variables de entorno.
- Meter en un ConfigMap lo que es constante en todos los ambientes: eso va en la imagen.
- Referenciar una clave que no existe: el pod se queda en `CreateContainerConfigError`.
- Imprimir la configuración completa en los registros al arrancar, secretos incluidos.
- Un solo Secret enorme compartido por todos los servicios.

## Taller

**Objetivo:** sacar toda la configuración de Bitácora de los manifiestos y desplegar la misma imagen en dos ambientes.

Parte de tus manifiestos de la sesión 02. No hay archivos de inicio.

1. Mueve `MENSAJE`, `VERSION` y `DESTINO_API` a ConfigMaps. Los Deployment no deben contener ningún valor que cambie por ambiente.
2. Crea un Secret con `CLAVE_API` sin que su valor quede en ningún archivo del repositorio. Comprueba que `POST /api/notas` responde 401 sin la cabecera y 201 con ella.
3. Lee el valor del Secret con `kubectl`. Escribe qué te dice eso sobre la seguridad de un Secret.
4. Cambia `MENSAJE` en el ConfigMap y aplica. ¿Cambia la respuesta de `/api/info`? Haz lo necesario para que cambie y explica por qué hizo falta.
5. Ahora entrega una clave del ConfigMap como **archivo montado** en un pod de prueba. Cambia el valor y mide cuánto tarda en verse dentro del contenedor.
6. Referencia a propósito una clave que no existe. Diagnostica el estado del pod y anota el mensaje exacto del evento.
7. Crea un segundo espacio de nombres, `bitacora-pruebas`, con otro `MENSAJE` y otra `CLAVE_API`. Los Deployment y Service deben ser **los mismos archivos**, sin copiar ni editar.
8. Marca un ConfigMap como inmutable. Intenta cambiarlo. ¿Para qué sirve esa opción?
9. Investiga y describe en media página cómo llevarías los secretos al repositorio de forma segura o cómo los traerías de un gestor externo. No hace falta implementarlo.

**Criterios de aceptación**

- `git grep` en tu rama no encuentra el valor de ninguna clave.
- Los mismos archivos de Deployment y Service sirven para los dos espacios de nombres.
- Los pasos 4 y 5 explican, con mediciones, la diferencia entre variables de entorno y archivos.

**Entrega:** rama `taller/k8s-03`, con los manifiestos y `taller/solucion/ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

En Spring Boot, las variables de entorno se convierten en propiedades sin escribir código (`SPRING_DATASOURCE_URL` es `spring.datasource.url`), y los archivos montados pueden leerse con `spring.config.import=configtree:`. En un frontend, recuerda que lo que se compila dentro del paquete de JavaScript es público: ahí nunca va un secreto, venga de donde venga.

## Preguntas senior

1. ¿Por qué un Secret no es seguro por sí mismo y qué lo hace seguro?
2. Variables de entorno o archivos montados: ¿de qué depende?
3. ¿Cómo logras que un cambio de configuración se despliegue de forma controlada y se pueda revertir?
4. ¿Qué va en la imagen, qué en un ConfigMap y qué en un Secret?
5. ¿Cómo rotarías una contraseña de base de datos sin corte?
6. ¿Qué riesgos tiene que la aplicación imprima su configuración al arrancar?
