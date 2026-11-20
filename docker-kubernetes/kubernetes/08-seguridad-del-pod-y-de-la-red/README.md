# 08 — Seguridad del pod y de la red

## Idea

Un hotel bien llevado. Tu llave abre tu habitación y nada más; no abre la cocina ni el cuarto de máquinas. Las puertas entre zonas están cerradas salvo las que hacen falta. Y el personal de limpieza no tiene la llave de la caja fuerte. Nada de eso supone que alguien sea un ladrón: supone que, si entra uno, llegue lo menos lejos posible.

## Lo esencial

Por defecto, Kubernetes es permisivo: un contenedor corre con el usuario que diga la imagen (muchas veces `root`), todos los pods pueden hablar con todos, y cada pod recibe una credencial para hablar con el servidor de la API. La seguridad consiste en ir cerrando eso.

### El contexto de seguridad del contenedor

| Campo | Qué hace |
|---|---|
| `runAsNonRoot: true` | El contenedor no arranca si la imagen quiere correr como `root` |
| `runAsUser` | Fuerza un identificador de usuario |
| `allowPrivilegeEscalation: false` | El proceso no puede ganar más privilegios de los que tiene |
| `capabilities.drop: ["ALL"]` | Quita los permisos especiales del núcleo que casi ninguna aplicación necesita |
| `readOnlyRootFilesystem: true` | Nadie puede escribir en el sistema de archivos del contenedor; lo que deba escribirse va en un volumen |
| `seccompProfile: RuntimeDefault` | Limita las llamadas al sistema permitidas |

### Estándares de seguridad de pods

Kubernetes trae tres niveles (`privileged`, `baseline`, `restricted`) y un mecanismo para exigirlos por espacio de nombres con una etiqueta. Con `enforce`, un pod que no cumple no se crea.

### Políticas de red

Una **NetworkPolicy** selecciona pods y dice qué tráfico entrante o saliente se les permite. Tres ideas:

- Mientras ningún pod esté seleccionado por una política, todo está permitido.
- En cuanto una política selecciona un pod, lo no permitido queda prohibido.
- Las políticas las hace cumplir el complemento de red del clúster. **Si el complemento no las soporta, se guardan sin error y no hacen nada.** Hay que probarlas siempre.

### Identidad y permisos

Cada pod corre con una **ServiceAccount**. Lo que esa cuenta puede hacer contra la API se define con `Role` (qué acciones sobre qué recursos, en un espacio de nombres) y `RoleBinding` (a quién se le da). Una aplicación corriente no necesita hablar con la API: se le puede quitar la credencial con `automountServiceAccountToken: false`.

## Ejemplo

### Antes

```yaml
spec:
  containers:
    - name: pedidos
      image: ghcr.io/ejemplo/pedidos:2.3.1
      securityContext:
        privileged: true      # "porque así dejó de fallar"
```

en un espacio de nombres sin ninguna política de red.

### Análisis

| Problema | Consecuencia |
|---|---|
| `privileged: true` | El contenedor tiene casi los mismos poderes que el nodo. Un fallo en la aplicación es un fallo en la máquina |
| Usuario `root` | Agrava cualquier vulnerabilidad |
| Sistema de archivos escribible | Un atacante puede dejar herramientas y modificar la aplicación |
| Credencial de la API montada | Si la cuenta tiene permisos de más, el atacante hereda el clúster |
| Sin políticas de red | Desde cualquier pod comprometido se alcanza la base de datos de cualquier otro equipo |

### Después

<!-- file: ejemplo/deployment.yaml -->
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedidos
spec:
  replicas: 3
  selector:
    matchLabels:
      app: pedidos
  template:
    metadata:
      labels:
        app: pedidos
    spec:
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: pedidos
          image: ghcr.io/ejemplo/pedidos:2.3.1
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
          volumeMounts:
            - name: temporales
              mountPath: /tmp
      volumes:
        - name: temporales
          emptyDir: {}
```

<!-- file: ejemplo/politicas-de-red.yaml -->
```yaml
# 1. Nadie entra a ningún pod de este espacio de nombres…
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: denegar-entrada
spec:
  podSelector: {}
  policyTypes:
    - Ingress
---
# 2. …salvo la web, que puede llegar a pedidos por su puerto.
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: web-a-pedidos
spec:
  podSelector:
    matchLabels:
      app: pedidos
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: web
      ports:
        - protocol: TCP
          port: 8080
```

<!-- file: ejemplo/espacio-de-nombres.yaml -->
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: tienda
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/warn: restricted
```

- El contenedor corre sin privilegios, sin poder escalar y sin poder escribir fuera de `/tmp`.
- El espacio de nombres rechaza cualquier pod que no cumpla el nivel `restricted`.
- Todo el tráfico entrante está cerrado y se abre solo lo necesario.

Los manifiestos se aplicaron en un clúster kind (Kubernetes v1.37.0, red `kindnet`, que sí hace cumplir las políticas de red): el espacio de nombres rechaza lo que no cumple `restricted` y el `Deployment` del ejemplo no produce avisos de seguridad.

## Errores comunes

- Resolver un error de permisos con `privileged: true` o corriendo como `root`.
- Escribir políticas de red y no comprobar que se cumplen.
- Cerrar la entrada y olvidar la salida, o cerrar la salida y olvidar la resolución de nombres.
- Olvidar que el tráfico que llega desde la entrada del clúster también necesita permiso.
- Dar a una cuenta de servicio permisos de administrador "para que funcione".
- Activar `readOnlyRootFilesystem` sin saber dónde escribe la aplicación.

## Taller

**Objetivo:** endurecer Bitácora hasta el nivel `restricted` y cerrar la red, sin que deje de funcionar.

No hay archivos de inicio. Parte de tus manifiestos de la sesión 07.

1. **Inventario.** Para cada pod de Bitácora anota: con qué usuario corre, si puede escribir en su sistema de archivos, si tiene montada una credencial de la API y a qué otros pods puede llegar. Usa `kubectl exec`.
2. Añade al espacio de nombres la etiqueta de aviso (`warn`) del nivel `restricted` y vuelve a aplicar tus manifiestos. Lee los avisos: son tu lista de tareas.
3. Corrige la API hasta que cumpla. Con `readOnlyRootFilesystem`, ¿qué pasa con `/salud/listo`? Resuélvelo sin desactivar la opción.
4. Corrige la web. La imagen de nginx necesita escribir en algunas rutas: descubre cuáles leyendo los registros y dale solo esas.
5. Cambia la etiqueta a `enforce`. Intenta crear un pod privilegiado en el espacio de nombres y guarda el mensaje de rechazo.
6. Un Deployment que no cumple no falla al aplicarlo. Encuentra dónde aparece el error.
7. **Red.** Antes de escribir nada, demuestra desde un pod temporal que puedes llegar a la API directamente.
8. Aplica una política que cierre toda la entrada del espacio de nombres. Comprueba que el pod temporal ya no llega. **Si sigue llegando, tu complemento de red no aplica políticas:** averígualo y resuélvelo antes de seguir (puede implicar recrear el clúster con otro complemento).
9. Abre solo lo necesario: la web puede llegar a la API; el controlador de entrada puede llegar a la web y a la API; nadie más. Si desplegaste PostgreSQL, decide quién puede llegar a él.
10. Cierra también la salida de la API. ¿Qué deja de funcionar que no esperabas? Ábrelo con la regla más estrecha que puedas.
11. **Permisos.** Quita la credencial de la API a los pods que no la usan. Crea una cuenta de servicio que solo pueda leer ConfigMaps de su espacio de nombres y demuéstralo con `kubectl auth can-i`.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| El espacio de nombres exige `restricted` | La etiqueta y un pod privilegiado rechazado |
| La aplicación funciona completa | La página guarda y lista notas |
| Un pod ajeno no llega a la API | Prueba desde un pod temporal, antes y después |
| Ningún pod de Bitácora tiene credencial de la API | `kubectl exec` buscando el archivo de la credencial |
| Cada regla abierta tiene una razón escrita | `ANALISIS.md` |

**Entrega:** rama `taller/k8s-08`, con los manifiestos y `taller/solucion/ANALISIS.md`, incluida la tabla del inventario antes y después.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

En una empresa, estas reglas no son opcionales: las impone la plataforma con controladores de admisión, y un manifiesto que no cumple se rechaza en el flujo de integración continua antes de llegar al clúster. Saber por qué existe cada una te ahorra la tentación de pedir una excepción.

## Preguntas senior

1. ¿Por qué no basta con que la imagen no use `root`?
2. ¿Qué ganas con un sistema de archivos de solo lectura?
3. ¿Cómo sabes que una política de red se está aplicando?
4. Describe el modelo "denegar por defecto" y sus costos operativos.
5. ¿Por qué una aplicación normal no debería tener credenciales de la API de Kubernetes?
6. Un pod queda comprometido. Con lo visto aquí, ¿qué limita el daño y qué no?
