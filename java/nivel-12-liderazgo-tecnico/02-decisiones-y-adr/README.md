# 02 — Decisiones técnicas y ADR

## Idea

Seis meses después, alguien encuentra una decisión extraña en el sistema y pregunta por qué se hizo así. Si la respuesta es "nadie se acuerda", pasan dos cosas malas: o se deshace algo que tenía una buena razón, o se conserva por miedo algo que ya no tiene sentido. Registrar las decisiones evita ambas.

## Lo esencial

### Cómo se toma una buena decisión técnica

1. **Define el problema** y por qué importa ahora. Muchas discusiones técnicas son en realidad desacuerdos sobre cuál es el problema.
2. **Explicita los criterios:** qué atributos de calidad pesan y cuáles son las restricciones (plazo, presupuesto, conocimientos del equipo).
3. **Genera alternativas reales**, incluida "no hacer nada".
4. **Evalúa los costos de cada una**, no solo los beneficios.
5. **Mira la reversibilidad:**

| Tipo | Ejemplos | Cómo decidir |
|---|---|---|
| Puerta de doble sentido (reversible) | Una biblioteca interna, el nombre de una clase, una caché | Rápido; quien esté más cerca del problema |
| Puerta de un solo sentido (difícil de revertir) | Modelo de datos, lenguaje, contrato público, proveedor de nube | Despacio, con datos y con más personas |

6. **Decide, registra y comunica.**
7. **Revisa** cuando cambien los supuestos.

Tratar todas las decisiones como irreversibles paraliza; tratarlas todas como reversibles hipoteca el futuro.

### Herramientas para reducir la incertidumbre

- **Prototipo desechable (*spike*):** tiempo acotado para responder una pregunta concreta. El código se descarta.
- **Prueba de concepto** sobre el punto de mayor riesgo, no sobre lo fácil.
- **Medición:** un experimento con datos gana a diez opiniones.
- **Posponer:** decidir en el último momento responsable, cuando hay más información y sin que el retraso cueste.

### ADR: registro de decisión de arquitectura

Un documento corto, uno por decisión, guardado en el repositorio junto al código.

```markdown
# ADR-007: Usar PostgreSQL como almacén principal

Estado: Aceptada (2026-03-14). Reemplaza a ADR-003.

## Contexto
Qué problema hay, qué restricciones y qué fuerzas están en juego.

## Decisión
Qué se decidió, en una o dos frases afirmativas.

## Alternativas consideradas
Cada una con el motivo por el que se descartó.

## Consecuencias
Positivas, negativas y lo que queda pendiente o en riesgo.
```

Reglas:

- **Inmutables.** Si la decisión cambia, se escribe un ADR nuevo que reemplaza al anterior. La historia se conserva.
- **Cortos:** una página.
- **Con las alternativas descartadas:** es la parte más valiosa, porque evita rediscutir lo mismo.
- **Con las consecuencias negativas:** un ADR sin costos es propaganda.

Para decisiones grandes se usa antes un documento de propuesta (RFC) que se discute; el ADR registra el resultado.

### Decidir en grupo

- El consenso total es lento y a veces imposible. Alternativa: **consultar a todos, decide uno** (quien tiene la responsabilidad).
- **"No estoy de acuerdo, pero me comprometo":** una vez tomada la decisión, se apoya.
- Separar las ideas de las personas.
- Cuidado con los sesgos: apego a lo que ya se invirtió, preferencia por lo conocido, deslumbramiento por lo nuevo y el peso de la voz con más autoridad.

## Ejemplo

```markdown
# ADR-012: Monolito modular en lugar de microservicios para el lanzamiento

Estado: Aceptada

## Contexto
Equipo de 7 personas, lanzamiento en 4 meses, dominio todavía poco conocido,
sin experiencia operando sistemas distribuidos y presupuesto de infraestructura limitado.

## Decisión
Construir un único desplegable organizado en módulos con fronteras verificadas
automáticamente. Cada módulo es dueño de sus tablas y se comunica por API pública o eventos.

## Alternativas consideradas
- Microservicios desde el inicio: descartada por costo operativo y porque las
  fronteras del dominio aún no son estables.
- Monolito en capas sin módulos: descartada porque dificultaría una extracción posterior.

## Consecuencias
+ Un solo despliegue, transacciones locales, pruebas simples.
+ Las fronteras quedan listas para extraer un módulo si hace falta.
- Todo escala junto.
- Un fallo grave afecta a todo el sistema.
- Requiere disciplina y verificación automática para que los módulos no se enreden.

Revisar si: el equipo supera las 20 personas, o un módulo necesita escalar por separado.
```

## Errores comunes

- Decidir por moda, por currículum o por lo que usa una empresa mil veces más grande.
- No considerar alternativas: la primera idea se convierte en la decisión.
- Discutir soluciones sin acordar el problema.
- Decisiones que solo existen en la cabeza de alguien o en un hilo de chat.
- ADR escritos meses después para justificar lo hecho.
- Parálisis por análisis en decisiones reversibles.
- Reabrir decisiones cerradas sin información nueva.

## Taller

1. Escribe tres ADR de decisiones reales de un proyecto tuyo, incluidas las alternativas descartadas y las consecuencias negativas.
2. Clasifica diez decisiones recientes como reversibles o no. ¿Se les dedicó el tiempo proporcional?
3. Encuentra una decisión de tu sistema cuya razón nadie conoce. Investígala y documéntala.
4. Plantea un *spike*: una pregunta, un límite de tiempo y el criterio que decidirá la respuesta.
5. Redacta una propuesta de una página para un cambio técnico que quieras impulsar, dirigida a alguien que no es técnico.

Rama `taller/n12-02-decisiones`.

## Preguntas senior

1. ¿Cómo tomas una decisión técnica cuando el equipo está dividido?
2. ¿Qué es una decisión reversible y por qué importa distinguirla?
3. ¿Qué debe tener un ADR y por qué es inmutable?
4. Cuéntame de una decisión técnica tuya que resultó equivocada. ¿Cómo lo supiste y qué hiciste?
5. ¿Cómo evitas adoptar una tecnología por moda?
