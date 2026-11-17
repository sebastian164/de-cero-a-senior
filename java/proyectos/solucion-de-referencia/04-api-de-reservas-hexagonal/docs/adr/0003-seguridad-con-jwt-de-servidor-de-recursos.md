# ADR 0003: la API es un servidor de recursos que valida JWT

**Estado:** aceptada.

**Contexto.** Cada usuario debe ver y modificar solo lo suyo; el administrador, todo. La identidad no puede venir de algo que el cliente fabrique a su gusto (una cabecera `X-Usuario`).

**Decisión.** La API no autentica ni guarda contraseñas: valida tokens JWT firmados (Spring Security como servidor de recursos OAuth2). El usuario es el `sub` del token y es administrador si el claim `roles` incluye `ADMIN`. Quién emite los tokens se configura fuera: `issuer-uri` de un proveedor de identidad, o `public-key-location` con una clave pública (lo que usan las pruebas y `dev/Token.java`). Sin token válido, 401 con `application/problem+json`. La regla "lo suyo o todo" vive en `ServicioReservas`, que recibe un `Actor` ya resuelto; el dominio no sabe de seguridad.

**Alternativas.** Sesiones con usuario y clave propios: obliga a custodiar credenciales y no es lo que hace un servicio en un sistema real. Cabeceras de confianza: cualquiera las falsifica (hay una prueba que lo comprueba).

**Consecuencias.** Las pruebas generan una clave RSA al vuelo y firman sus propios tokens: cubren token válido, de otra clave, vencido y basura. **No se probó contra un proveedor de identidad real** (Keycloak u otro); `dev/Token.java` genera claves de juguete que nunca deben usarse fuera de tu máquina. El rol viaja en el token: revocar un permiso exige esperar a que venza (aquí, una hora).
