# Dónde guarda la sesión esta aplicación y qué riesgo asume

| Dato | Dónde | Quién lo lee | Riesgo |
|---|---|---|---|
| Bandera `rol` (solo para la guarda y los botones) | `localStorage` | Cualquier script de la página; cualquier persona con las herramientas del navegador | Se **falsifica** en un segundo (punto 2). No concede nada: el servidor decide con el token |
| Token de la sesión real (`Sesion.token`) | Memoria de la aplicación | El código de la página mientras vive | Un XSS puede leerlo mientras la pestaña esté abierta; se pierde al recargar (hay que iniciar sesión otra vez). Es la opción con menor exposición sin servidor de apoyo |

Riesgo que se asume: **si hay un XSS, el atacante actúa con la sesión de la víctima** mientras la pestaña esté abierta. Mitigaciones aplicadas: sin `bypassSecurityTrust…` con datos de usuarios, CSP que bloquea scripts en línea, sin secretos en el paquete. Alternativa para datos sensibles: cookie `HttpOnly; Secure; SameSite=Strict` emitida por el servidor (no legible por JavaScript, pero con protección CSRF) o el patrón BFF, con los tokens solo en el servidor.
