# Dónde guarda esta aplicación el token y qué riesgo asume (punto 6)

**Esta aplicación no tiene autenticación real**: la sesión de n3-05 (guardas e interceptores) guarda una sesión simulada en memoria (señal) y no hay token que robar. Esta nota describe lo que se haría y lo que se asumiría en una aplicación real equivalente.

**Decisión:** cookie `HttpOnly; Secure; SameSite=Lax` emitida por el servidor (o por un BFF que guarda los tokens del proveedor de identidad y entrega al navegador solo la cookie de sesión). El JavaScript de la aplicación nunca ve el token.

**Riesgo que se asume:**

1. *CSRF.* Al usar cookies, el navegador las adjunta solas. Mitigación: `SameSite=Lax` (o `Strict` donde se pueda), token anti-CSRF que `HttpClient` reenvía en cabecera (`withXsrfConfiguration`) y verificación de `Origin` en el servidor.
2. *XSS sigue siendo grave aunque la cookie no se lea:* un script inyectado puede hacer peticiones autenticadas desde la página de la víctima (no puede exfiltrar el token, pero sí actuar mientras la sesión viva). Mitigación: CSP estricta (ver n6-03), sin `bypassSecurityTrust…`, dependencias auditadas.
3. *Sesión robada por el equipo del usuario (malware)*: fuera del alcance del frontend; se acota con vida corta de la sesión, renovación y revocación en el servidor.

**Alternativa descartada: token en `localStorage`.** Cualquier script de la página lo lee; un solo XSS o una sola dependencia comprometida lo exfiltra. Solo sería aceptable con tokens de vida muy corta, alcance mínimo y una CSP que haga improbable el XSS, y aun así se prefiere la cookie.

**Dónde no va ningún secreto:** claves privadas de terceros, ni en el código ni en variables de entorno que terminen en el paquete. El script de n6-03 busca secretos en el paquete de producción construido.
