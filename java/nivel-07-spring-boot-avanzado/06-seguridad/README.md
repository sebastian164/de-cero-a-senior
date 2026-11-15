# 06 — Seguridad con Spring Security

## Idea

En un edificio hay dos preguntas distintas: quién eres (te identificas en la recepción) y a dónde puedes entrar (tu carné abre unas puertas y otras no). Confundirlas o saltarse una es la causa de casi todos los incidentes.

## Lo esencial

- **Autenticación:** verificar la identidad.
- **Autorización:** decidir qué puede hacer esa identidad.
- **Arquitectura:** una cadena de filtros (`SecurityFilterChain`) procesa cada petición antes de que llegue al controlador. Es el patrón Chain of Responsibility.
- **`SecurityContext`:** guarda al usuario autenticado durante la petición, ligado al hilo.

### Mecanismos

| Mecanismo | Uso |
|---|---|
| Sesión con cookie | Aplicaciones web con servidor que renderiza |
| JWT como *bearer token* | APIs sin estado |
| OAuth 2.0 / OpenID Connect | Delegar la autenticación en un proveedor de identidad (Keycloak, Entra ID, Auth0) |

Para una API moderna, lo habitual es que la aplicación sea un **servidor de recursos** que valida tokens emitidos por un proveedor de identidad. No implementes tu propio servidor de autenticación.

### JWT, lo que hay que saber

- Tres partes: cabecera, datos (*claims*), firma.
- Está **firmado, no cifrado**: cualquiera puede leer su contenido. No guardes datos sensibles.
- No se puede revocar fácilmente: usa vida corta y *refresh tokens*.
- Valida siempre firma, emisor, audiencia y expiración.

### Autorización

- Por ruta, en la cadena de filtros.
- Por método: `@PreAuthorize("hasRole('ADMIN')")`.
- **Por recurso:** que el usuario sea administrador no basta; hay que comprobar que **ese** pedido es **suyo**. Omitirlo es la vulnerabilidad más frecuente en APIs (acceso a objetos de otros usuarios cambiando el identificador).

## Ejemplo

```java
@Configuration
@EnableMethodSecurity
class SeguridadConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/productos/**").permitAll()
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())      // API sin estado y sin cookies de sesión
                .build();
    }
}

@Service
class ConsultaPedidos {

    @PreAuthorize("hasRole('ADMIN') or @propiedadPedidos.esDueno(#id, authentication.name)")
    PedidoResponse buscar(Long id) { ... }
}
```

## Riesgos que todo backend debe cubrir

| Riesgo | Defensa |
|---|---|
| Inyección SQL | Consultas parametrizadas; nunca concatenar |
| Control de acceso roto | Autorización por recurso, denegar por defecto |
| Exposición de datos | DTOs, no entidades; no registrar secretos ni datos personales |
| Contraseñas | `BCrypt` o `Argon2` mediante `PasswordEncoder`; nunca texto plano ni hashes rápidos |
| CSRF | Activo si hay sesión con cookies |
| CORS | Orígenes explícitos, nunca `*` con credenciales |
| Dependencias vulnerables | Análisis automático y actualización |
| Fuerza bruta | Límites de tasa, bloqueo temporal |
| Secretos | Fuera del repositorio, rotación |

Referencia obligada: OWASP Top 10 y OWASP API Security Top 10.

## Errores comunes

- Desactivar CSRF o abrir CORS "para que funcione" sin entender.
- Validar el rol pero no la propiedad del recurso.
- Guardar el JWT con datos sensibles o con vida de semanas.
- Autorizar solo en el frontend.
- Registrar tokens o contraseñas.
- Mensajes distintos para "usuario no existe" y "contraseña incorrecta".
- Endpoints de Actuator expuestos sin protección.

## Taller

1. Configura una API como servidor de recursos con un proveedor de identidad local (Keycloak en Docker).
2. Define tres roles y protege rutas y métodos.
3. Implementa autorización por recurso: un usuario solo ve sus pedidos. Escribe la prueba que demuestra que no puede ver los de otro.
4. Escribe pruebas con `@WithMockUser` para acceso permitido, no autenticado (401) y sin permiso (403).
5. Revisa un proyecto tuyo contra el OWASP API Top 10 y lista tres hallazgos con su corrección.

Rama `taller/n7-06-seguridad`.

## Preguntas senior

1. Autenticación frente a autorización, con un ejemplo de fallo de cada una.
2. ¿Dónde guardas un token en el navegador y por qué?
3. ¿Cuándo es correcto desactivar CSRF?
4. ¿Cómo revocas un JWT antes de que expire?
5. ¿Qué es el acceso roto a nivel de objeto y cómo lo previenes de forma sistemática?
6. OAuth 2.0 frente a OpenID Connect.
