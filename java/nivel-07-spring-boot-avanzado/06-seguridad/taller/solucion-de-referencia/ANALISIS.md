# Taller 07-06 — Seguridad (solución)

Spring Boot 4.1.1, Spring Security 7.1.1, Keycloak 26.0 (Docker, `start-dev`, puerto 42180). `mvn test` ejecuta 17 pruebas; con `-Dkc.url=http://localhost:42180` ejecuta además `KeycloakTest` (2 pruebas: roles y propiedad, y audiencia; 19 en total).

1. **Servidor de recursos con proveedor local.** `SeguridadConfig` usa `oauth2ResourceServer(jwt)`. Contra Keycloak real (`keycloak/realm-curso.json`, `docker-compose.keycloak.yml`): `spring.security.oauth2.resourceserver.jwt.issuer-uri=<kc>/realms/curso` y un mapper de roles de realm hacia el claim `roles`. `KeycloakTest` obtiene tokens reales (grant `password`) para `ana`, `sara` y `root`. Sin Keycloak, `JwtRealTest` firma tokens RS256 con un par RSA generado y el decodificador valida firma, emisor, audiencia y expiración.
2. **Tres roles** (`CLIENTE`, `SOPORTE`, `ADMIN`): por ruta (`/v1/admin/**` ADMIN, `/v1/soporte/**` SOPORTE o ADMIN, `/v1/productos/**` y `/actuator/health` públicas) y por método (`@PreAuthorize`).
3. **Autorización por recurso.** `@PreAuthorize("hasRole('ADMIN') or @propiedadPedidos.esDueno(#id, authentication.name)")`. Pruebas: `ana` lee el pedido 1 (200) y recibe 403 en el 3 (de `luis`); `root` lee el 3; el listado devuelve solo los propios. Con Keycloak real, igual.
4. **`@WithMockUser`** (`SeguridadMockTest`): permitido (200), sin autenticar (401), sin permiso (403).
5. **Revisión OWASP API Top 10 (de esta misma API), tres hallazgos:**
   - **API1 Broken Object Level Authorization**: sin el `@PreAuthorize` por propiedad, cualquier cliente lee `/v1/pedidos/3`. Corrección: la expresión de arriba; probada en las tres capas (mock, JWT firmado, Keycloak).
   - **API3 Exposición de propiedades** (campo `dueno`): la entidad `Pedido` lleva el dueño; se responde `PedidoResponse` (DTO) y la prueba comprueba que `$.dueno` no existe.
   - **API2 Autenticación rota / validación de token**: validar solo firma deja pasar tokens de otro emisor o de otra API. Corrección: `JwtValidators.createDefaultWithIssuer` más validador de audiencia; probados emisor, audiencia, firma ajena y expiración (401). Con Keycloak, el decodificador por `issuer-uri` valida emisor y expiración; la audiencia se valida con `spring.security.oauth2.resourceserver.jwt.audiences=api-pedidos` y un mapper `oidc-audience-mapper` en el cliente `api-curso`. Probado con tokens reales: el de `api-curso` da 200 y el de `otra-app` (mismo usuario `ana`, sin mapper de audiencia) da 401. Sin la propiedad, el decodificador por `issuer-uri` acepta tokens de cualquier cliente del realm.
   - Pendiente conocido (API4, consumo sin límites): no hay límite de tasa; se resuelve en un gateway o con un filtro (p. ej. Bucket4j); no implementado aquí.

## Hallazgos para el curso (README 07-06)

- `@propiedadPedidos.esDueno(...)` falla en ejecución con `EL1004E: Method call: Method esDueno(...) cannot be found` si el bean o el método no son `public` (SpEL no invoca métodos de un tipo no público). El README no muestra el bean; se añadió una nota.
- El proyecto necesita `spring-boot-starter-security-oauth2-resource-server` (Boot 4); sin un `JwtDecoder` (ni `issuer-uri` ni clave) el contexto no arranca, por eso las pruebas con `@WithMockUser` declaran un `@MockitoBean JwtDecoder`.
- `jwt()` de `spring-security-test` no usa el convertidor propio: hay que darle `.authorities(...)`.
- `CORS` y `CSRF`: se desactiva CSRF solo porque la API es sin estado y sin cookies (`sessionCreationPolicy(STATELESS)`).
