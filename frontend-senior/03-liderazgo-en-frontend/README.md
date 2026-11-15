# 03 — Liderazgo técnico en frontend

Sesión común a las rutas de Angular y de React/Next. El contenido general de liderazgo (qué hace un senior, decisiones y ADR, revisión de código, deuda técnica y estimación, comunicación y mentoría) está completo en el [Nivel 12 de Java](../../java/nivel-12-liderazgo-tecnico/) y aplica sin cambios. Estúdialo primero. Esta sesión agrega lo que es particular del frontend.

## Lo particular del frontend

### 1. El ecosistema cambia rápido

Cada año aparecen bibliotecas, y los propios frameworks cambian sus recomendaciones. La responsabilidad de un líder es filtrar:

- **Invertir en fundamentos que no caducan:** el lenguaje, la plataforma web (HTML, CSS, HTTP, el navegador), accesibilidad, rendimiento, diseño de componentes, gestión de estado como concepto.
- **Adoptar con criterio:** ¿qué problema nuestro resuelve?, ¿quién la mantiene?, ¿cuánto cuesta salir de ella?
- **Mantenerse actualizado por incrementos:** una versión mayor a la vez, con las herramientas de migración. Un salto de cinco versiones es un proyecto de riesgo.
- **Un radar tecnológico del equipo:** adoptar, probar, evaluar, evitar.

### 2. Se trabaja con diseño y producto, no para ellos

- Participar **antes** de que el diseño esté cerrado: el costo técnico de una interacción se discute cuando aún puede cambiar.
- **Sistema de diseño** como lenguaje común: los mismos componentes en la herramienta de diseño y en el código.
- Explicar compromisos en términos de experiencia: "esta animación cuesta dos días y en móviles de gama baja irá lenta; esta otra da el mismo efecto".
- Defender al usuario: accesibilidad, rendimiento y estados de error son parte del alcance, no extras negociables al final.

### 3. El contrato con el backend

- Acordar el contrato **antes** de construir (especificación primero).
- Tipos generados desde la especificación.
- Simulacros del contrato para avanzar en paralelo.
- Decidir juntos qué se calcula en cada lado. Una regla duplicada en cliente y servidor termina desincronizada.
- Considerar un BFF cuando la interfaz necesita datos de muchos servicios.

### 4. Decisiones de frontend que merecen un ADR

| Decisión | Por qué es difícil de revertir |
|---|---|
| Framework y metaframework | Toda la base de código |
| Estrategia de renderizado | Infraestructura y forma de escribir el código |
| Gestión de estado | Atraviesa todas las funcionalidades |
| Sistema de estilos | Toca cada componente |
| Estructura del repositorio y límites | Organiza a los equipos |
| Biblioteca de componentes | Dependencia visual y de accesibilidad |
| Autenticación en el navegador | Seguridad |
| Estrategia de pruebas | Hábitos del equipo |

### 5. Calidad percibida

En el frontend la calidad se ve. Un líder define y cuida el estándar de "terminado":

- Funciona con teclado y con lector de pantalla.
- Tiene estados de carga, vacío y error.
- Funciona en móvil y con red lenta.
- No degrada las métricas web.
- Tiene pruebas del comportamiento.
- Está tras una bandera si es riesgoso.

Ese estándar se escribe, se acuerda y se automatiza donde se pueda.

### 6. Revisión de código en frontend

Además de lo general, mirar: accesibilidad del marcado, estados no contemplados, estado duplicado o derivado guardado, efectos innecesarios, peso de dependencias nuevas, textos sin internacionalizar, y que la prueba verifique comportamiento y no implementación.

### 7. Demostrar el trabajo

El frontend tiene una ventaja: se puede mostrar. Vistas previas por cambio, catálogo de componentes, demostraciones frecuentes. Mostrar pronto y a menudo reduce el riesgo de construir lo equivocado.

## Taller final de la ruta

1. **Radar.** Construye el radar tecnológico de tu equipo o el tuyo: diez elementos clasificados y justificados.
2. **ADR.** Escribe tres registros de decisión de frontend de un proyecto real.
3. **Definición de terminado.** Redacta la de tu equipo para una funcionalidad de interfaz e indica qué puntos se verifican de forma automática.
4. **Contrato.** Para una funcionalidad nueva, escribe la especificación del contrato con el backend y los simulacros antes de programar.
5. **Negociación.** Toma un diseño exigente y prepara la conversación con diseño: costo, riesgo y dos alternativas.
6. **Defensa.** Presenta en diez minutos la arquitectura del proyecto final de tu ruta: problema, decisiones, alternativas, costos y cómo evolucionaría.
7. **Balance.** Recorre todos los niveles de tu ruta: qué dominas, qué necesita repaso y tu plan para los próximos seis meses.

Rama `taller/fe-03-liderazgo`.

## Preguntas senior

1. ¿Cómo decides adoptar o no una biblioteca nueva?
2. ¿Cómo mantienes actualizada una aplicación grande sin detener al equipo?
3. Cuéntame de una vez que negociaste un diseño por razones técnicas o de accesibilidad.
4. ¿Qué incluye tu definición de "terminado" para una pantalla?
5. ¿Cómo coordinas el contrato con el equipo de backend?
6. ¿Qué fundamentos recomendarías estudiar a alguien que quiere durar en el frontend?
