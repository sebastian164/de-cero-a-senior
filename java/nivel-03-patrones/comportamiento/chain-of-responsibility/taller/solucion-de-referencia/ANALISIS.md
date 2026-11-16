# Chain of Responsibility — análisis

1. **Variante**: tubería (cada eslabón recibe la cadena y decide si continúa). Los eslabones de negocio se comportan como la variante clásica (el primero que puede atiende y corta), pero el de métricas necesita envolver al resto, algo que la variante clásica no permite. Con la tubería se cubren ambos casos con un solo mecanismo. Orden: métricas → spam → FAQ → nivel 2 (crítico o corporativo) → nivel 1 → final.

2. **Tiquete que nadie atiende**: `SinAtender` es el eslabón final explícito y lo manda a triage manual. Además, si alguien arma una cadena sin eslabón final, `Soporte` lanza `IllegalStateException` en lugar de devolver `null`.

3. **Reordenar**:
   - FAQ antes que spam: un spam que menciona "contraseña" recibe una respuesta automática; se le confirma al spammer que la dirección está viva y se gastan respuestas (`reordenarFaqYSpamRespondeAlSpam`).
   - Nivel 1 antes que nivel 2: los tiquetes críticos y de clientes corporativos caen en la cola de nivel 1 y se incumple el acuerdo de servicio (`reordenarNivelesMandaLosCriticosANivel1`).

4. **Métricas**: `Metricas` toma la hora, llama a `cadena.continuar` y registra cuánto tardó el resto y quién lo resolvió. Recibe el reloj como `LongSupplier` para probarlo con un reloj falso.

5. **Spring Security**: `Eslabon` equivale a `Filter`/`OncePerRequestFilter`; `Cadena` a `FilterChain`; `cadena.continuar(t)` a `chain.doFilter(request, response)`. No llamarlo equivale a cortar la cadena (como un filtro de autenticación que responde 401). `Soporte` cumple el papel de `FilterChainProxy`/`SecurityFilterChain`, que arma la lista ordenada; el orden de los filtros importa por las mismas razones del punto 3.
