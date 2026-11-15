# 02 — Seguridad y autenticación en React y Next.js

Los fundamentos de seguridad en el navegador (XSS, CSRF, almacenamiento de credenciales, CSP, dependencias) no dependen del framework y están en la [sesión de seguridad de la ruta Angular](../../../angular/nivel-06-angular-avanzado/03-seguridad-en-el-frontend/). Esta sesión cubre lo específico de React y, sobre todo, de Next.js, donde el frontend incluye código de servidor.

## Idea

Con Next.js, tu proyecto de "frontend" consulta bases de datos y expone endpoints. Eso lo convierte en un backend, con todas sus responsabilidades de seguridad. La frontera entre servidor y cliente es también una frontera de confianza, y es fácil cruzarla sin darse cuenta.

## Lo esencial

### En React

- React escapa por defecto todo lo que se interpola en JSX: protege de XSS.
- La puerta de salida se llama `dangerouslySetInnerHTML`, y el nombre es una advertencia. Solo con contenido saneado.
- Otros vectores: `href` construidos con datos del usuario (`javascript:`), y propiedades propagadas desde datos no confiables (`{...datosDelUsuario}`).

### En Next.js: cinco reglas

**1. Toda acción de servidor es un endpoint público.** Cualquiera puede invocarla con cualquier argumento, sin pasar por tu interfaz. Dentro de cada una: autenticar, validar con esquema y autorizar sobre el recurso concreto.

**2. Todo lo que se pasa a un componente de cliente llega al navegador.** Aunque no se muestre. Proyecta los datos en el servidor.

**3. La autorización va junto a los datos, no en la interfaz ni solo en el código que intercepta peticiones.** Ocultar un botón o redirigir desde un interceptor no protege nada si la consulta o la acción no comprueban.

**4. Las variables `NEXT_PUBLIC_` son públicas.** Se incrustan en el paquete del navegador.

**5. Protege los módulos de servidor** con `server-only`.

### Capa de acceso a datos

El patrón recomendado: un único lugar por donde pasan todas las lecturas y escrituras, que comprueba la sesión y los permisos y devuelve solo lo que ese usuario puede ver.

```ts
// pedidos/datos/acceso.ts
import 'server-only';

export async function obtenerPedido(id: string): Promise<PedidoVisible | null> {
  const usuario = await obtenerUsuarioActual();            // memorizado por petición
  if (!usuario) return null;

  const pedido = await bd.pedido.findUnique({ where: { id } });
  if (!pedido) return null;
  if (pedido.clienteId !== usuario.id && !usuario.esAdministrador) return null;   // autorización por recurso

  return {                                                 // proyección: solo lo que puede salir
    id: pedido.id,
    estado: pedido.estado,
    total: pedido.total,
    lineas: pedido.lineas,
  };
}
```

Las páginas y las acciones usan esta función; nunca consultan la base directamente. Si alguien olvida comprobar en una página, la comprobación ya ocurrió.

```ts
// Antes: confía en que la interfaz solo ofrece los pedidos propios
'use server';
export async function eliminarPedido(id: string) {
  await bd.pedido.delete({ where: { id } });               // cualquiera puede borrar cualquier pedido
}

// Después
'use server';
export async function eliminarPedido(entrada: unknown): Promise<Resultado> {
  const usuario = await obtenerUsuarioActual();
  if (!usuario) return { ok: false, error: 'No autenticado' };

  const id = z.string().uuid().safeParse(entrada);
  if (!id.success) return { ok: false, error: 'Solicitud inválida' };

  const pedido = await bd.pedido.findUnique({ where: { id: id.data } });
  if (!pedido || pedido.clienteId !== usuario.id) return { ok: false, error: 'Pedido no encontrado' };

  await bd.pedido.delete({ where: { id: pedido.id } });
  revalidateTag(`pedidos-${usuario.id}`);
  return { ok: true };
}
```

### Autenticación

- **Sesión en una cookie** `HttpOnly`, `Secure` y `SameSite`, firmada o cifrada. JavaScript no puede leerla.
- **No implementes la criptografía ni el flujo desde cero:** usa una biblioteca de autenticación mantenida o un proveedor de identidad (OpenID Connect).
- **Comprobación en tres niveles:** el interceptor de peticiones para redirigir temprano (comodidad, no seguridad); la capa de acceso a datos (la definitiva); y la interfaz para no mostrar lo que no aplica.
- **Sesión con vida limitada,** renovación y cierre efectivo en el servidor.
- **CSRF:** las acciones de servidor comparan el origen de la petición; los manejadores de ruta que usen cookies necesitan su propia protección.

### Otros puntos

- **Cabeceras de seguridad** (CSP, HSTS, `frame-ancestors`) desde la configuración o el interceptor.
- **Límite de tasa** en acciones y endpoints sensibles (ingreso, registro, recuperación de clave).
- **Errores:** no devolver al cliente mensajes internos; Next.js los oculta en producción para los componentes de servidor, y tú debes hacer lo mismo en las acciones.
- **Redirecciones abiertas:** validar el parámetro de retorno.
- **Dependencias:** auditoría automática; las vulnerabilidades del propio framework se corrigen actualizando sin demora.
- **Registros:** sin tokens, cookies ni datos personales.
- **Subida de archivos:** validar tipo y tamaño en el servidor; nunca servirlos desde el mismo origen sin tratamiento.

## Errores comunes

- Acciones de servidor sin autenticación ni autorización "porque el botón solo le aparece al dueño".
- Autorización solo en el interceptor de peticiones.
- Registros completos de la base pasados a componentes de cliente.
- Secretos con `NEXT_PUBLIC_`.
- Sesión en `localStorage`.
- Identificadores secuenciales expuestos sin comprobar la propiedad.
- `dangerouslySetInnerHTML` con contenido de usuarios.
- Confiar en la validación del formulario.
- Consultas a la base repartidas por las páginas, cada una con su propia (o ninguna) comprobación.

## Taller

1. Invoca directamente una acción de servidor de tu aplicación (desde la consola o con una herramienta HTTP) con el identificador de un recurso ajeno. Si funciona, corrígela.
2. Implementa una capa de acceso a datos con autorización y proyección, y haz que páginas y acciones pasen por ella.
3. Inspecciona lo que llega al navegador en dos páginas y elimina lo que sobra.
4. Busca secretos en el paquete de cliente construido.
5. Agrega `server-only` a los módulos sensibles.
6. Configura cabeceras de seguridad y una CSP.
7. Agrega límite de tasa al ingreso.
8. Escribe pruebas que verifiquen que un usuario no puede leer ni modificar recursos de otro.

Rama `taller/rx-n6-02-seguridad`.

## Preguntas senior

1. ¿Por qué una acción de servidor debe tratarse como un endpoint público?
2. ¿Dónde se comprueba la autorización en una aplicación Next.js y por qué no basta el interceptor?
3. ¿Qué es una capa de acceso a datos y qué problema previene?
4. ¿Qué datos pueden filtrarse al cruzar del servidor al cliente?
5. ¿Cómo manejas la sesión de forma segura?
