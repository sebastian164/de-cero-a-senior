# 03 — Seguridad en el frontend

## Idea

Todo lo que envías al navegador queda en manos del usuario: puede leerlo, modificarlo y ejecutarlo como quiera. La interfaz no puede guardar secretos ni tomar decisiones de seguridad definitivas. Su responsabilidad es otra: no convertirse en la puerta por donde entra un ataque contra sus propios usuarios.

## Lo esencial

### Principio base

**El cliente no es de confianza.** Autorización, validación y reglas críticas se aplican en el servidor. Lo que hace el frontend (ocultar un botón, una guarda de ruta, validar un formulario) es experiencia de usuario.

### XSS (*cross-site scripting*)

Un atacante logra que tu página ejecute su código, con los permisos de la víctima: puede leer lo que ve el usuario y actuar en su nombre.

Angular protege por defecto: todo valor interpolado se trata como texto y se sanea según el contexto. Las formas de romper esa protección:

| Riesgo | Alternativa |
|---|---|
| `bypassSecurityTrustHtml` y similares con contenido que no controlas | Sanear en el servidor; mostrar como texto |
| `[innerHTML]` con contenido de usuarios | Texto plano o un formato seguro y limitado |
| Manipular el DOM directamente (`nativeElement.innerHTML`) | Enlaces de plantilla |
| Construir URLs o plantillas concatenando datos del usuario | Enlaces de propiedad; validación |
| Bibliotecas de terceros que insertan HTML | Auditarlas |

Defensa en profundidad: **Política de Seguridad de Contenido (CSP)** que restringe qué scripts pueden ejecutarse, y *Trusted Types* donde se pueda.

### CSRF

Un sitio malicioso hace que el navegador de la víctima envíe una petición a tu aplicación con sus *cookies*. Defensas: *cookies* `SameSite`, y el token anti-CSRF que `HttpClient` gestiona junto con el servidor. Aplica cuando la autenticación va por *cookies*.

### Almacenamiento de credenciales

| Dónde | Riesgo |
|---|---|
| `localStorage` / `sessionStorage` | Legible por cualquier script: un XSS lo roba |
| Memoria | Se pierde al recargar; aún expuesto a XSS mientras la página vive |
| *Cookie* `HttpOnly`, `Secure`, `SameSite` | No legible por JavaScript; requiere protección CSRF |

Para aplicaciones sensibles, el patrón BFF mantiene los tokens en el servidor.

### Otros puntos

- **Dependencias:** cada paquete de npm es código que se ejecuta con los permisos de tu aplicación. Auditoría automática, versiones fijadas, revisar lo que se instala.
- **Secretos:** ninguna clave privada en el código del cliente ni en variables de entorno que terminen en el paquete.
- **Datos sensibles:** no en la URL, no en registros, no en almacenamiento local sin necesidad.
- **Redirecciones abiertas:** validar el parámetro "volver a" contra rutas internas.
- **Enlaces externos:** `rel="noopener noreferrer"`.
- **Cabeceras de seguridad** desde el servidor: CSP, `Strict-Transport-Security`, `X-Content-Type-Options`, `frame-ancestors` contra el secuestro de clics.
- **Scripts de terceros** (analítica, chat): tienen acceso total a la página. Limítalos y cárgalos con integridad de subrecursos.

## Ejemplo

```ts
// Peligroso: el comentario de un usuario se inserta como HTML confiable
this.html = this.sanitizer.bypassSecurityTrustHtml(comentario.texto);
// plantilla: <div [innerHTML]="html"></div>

// Seguro: Angular lo trata como texto
// plantilla: <p>{{ comentario.texto }}</p>

// Redirección validada
protected volver(): void {
  const destino = this.ruta.snapshot.queryParamMap.get('volverA') ?? '/';
  const esInterna = destino.startsWith('/') && !destino.startsWith('//');
  this.router.navigateByUrl(esInterna ? destino : '/');
}
```

## Errores comunes

- Confiar en las guardas y en los botones ocultos como seguridad.
- `bypassSecurityTrust…` para "hacer que funcione".
- Claves de API privadas en el paquete.
- Tokens de larga duración en `localStorage`.
- Datos personales en parámetros de la URL.
- Dependencias sin actualizar durante años.
- CSP ausente o con `unsafe-inline` por todas partes. Con Angular, la CSP estricta de `script-src` exige desactivar el CSS crítico en línea del compilador de producción (`optimization.styles.inlineCritical: false`): inserta un `<script>` en línea que la política bloquea (comprobado en las 54 pantallas de la verificación).
- Mensajes de error que revelan estructura interna.

## Taller

1. Demuestra un XSS en una aplicación de prueba usando `bypassSecurityTrustHtml` con un comentario malicioso, y corrígelo.
2. Demuestra con las herramientas del navegador que se puede saltar una guarda y que el servidor es quien debe rechazar.
3. Ejecuta una auditoría de dependencias en un proyecto tuyo y resuelve los hallazgos graves.
4. Busca secretos en el paquete de producción construido.
5. Define una CSP para tu aplicación y ajústala hasta que no bloquee nada legítimo.
6. Revisa dónde guarda la sesión tu aplicación y redacta el riesgo que asume.

Rama `taller/ng-n6-03-seguridad`.

## Preguntas senior

1. ¿Por qué el frontend no puede ser responsable de la autorización?
2. ¿Cómo protege Angular contra XSS y cómo se rompe esa protección?
3. XSS frente a CSRF: diferencia y defensas.
4. ¿Dónde guardas la sesión y qué riesgo asumes con cada opción?
5. ¿Cómo gestionas el riesgo de las dependencias y de los scripts de terceros?
