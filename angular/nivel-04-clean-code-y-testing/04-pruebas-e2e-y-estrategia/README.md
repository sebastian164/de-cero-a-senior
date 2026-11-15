# 04 — Pruebas de extremo a extremo y estrategia de pruebas

## Idea

Antes de abrir un restaurante se prueba cada receta por separado, y además se hace una cena de ensayo completa: reserva, mesa, pedido, cocina, cuenta. La cena de ensayo es lenta y cara, así que se hacen pocas, pero detecta lo que ninguna prueba de receta ve: que el mesero no encuentra la cocina.

## Lo esencial

### Extremo a extremo (E2E)

Un navegador real, manejado por un programa, recorre la aplicación como un usuario. La herramienta habitual hoy es **Playwright** (también Cypress).

```ts
test('un cliente puede hacer un pedido', async ({ page }) => {
  await page.goto('/catalogo');
  await page.getByRole('button', { name: 'Agregar Teclado al carrito' }).click();
  await page.getByRole('link', { name: 'Carrito' }).click();

  await expect(page.getByRole('heading', { name: 'Tu carrito' })).toBeVisible();
  await expect(page.getByText('Total: $ 120.000')).toBeVisible();

  await page.getByRole('button', { name: 'Confirmar pedido' }).click();
  await expect(page.getByRole('status')).toHaveText(/pedido confirmado/i);
});
```

Principios:

- **Selectores por rol y texto,** no por clases CSS ni rutas del DOM.
- **Esperas automáticas** a condiciones (`expect(...).toBeVisible()`), nunca pausas fijas.
- **Pruebas independientes:** cada una prepara sus datos.
- **Pocas y valiosas:** los recorridos críticos del negocio (ingresar, comprar, pagar), no cada validación.
- **Autenticación reutilizada** guardando el estado de sesión, en lugar de iniciar sesión por la interfaz en cada prueba.

### El backend en las pruebas E2E

| Opción | A favor | En contra |
|---|---|---|
| Backend real en un ambiente de pruebas | Verifica la integración | Lento, frágil, datos compartidos |
| Backend simulado (interceptando la red) | Rápido, determinista | No detecta cambios del contrato |
| Pruebas de contrato + simulado | Lo mejor de ambos | Más infraestructura |

Lo habitual: la mayoría con red simulada, y unas pocas pruebas de humo contra el ambiente real.

### Otras pruebas del frontend

| Tipo | Qué detecta | Herramienta |
|---|---|---|
| Accesibilidad automática | Contraste, etiquetas, roles | axe |
| Regresión visual | Cambios de apariencia no intencionales | Capturas comparadas |
| Rendimiento | Tamaño del paquete, métricas web | Lighthouse, presupuestos de tamaño |
| Tipos | Errores de contrato | El compilador, en modo estricto |
| Análisis estático | Errores comunes | ESLint |

### Estrategia

```
          E2E: pocos recorridos críticos
       Componentes: comportamiento de la interfaz
   Unitarias: reglas, adaptadores, validadores, almacenes
Estático: tipos estrictos y lint  (la base más barata)
```

En frontend, muchos equipos dan más peso a las pruebas de componentes que a las unitarias puras (la forma de "trofeo"), porque es ahí donde vive la mayoría de los errores de interfaz. La distribución correcta depende de dónde está la lógica de tu aplicación.

Criterios para decidir dónde va una prueba: lo más abajo posible que todavía dé confianza; cada regla se prueba una vez, en su nivel; y los recorridos E2E verifican que las piezas encajan, no cada caso.

### En la integración continua

- Estático y unitarias en cada *push*; E2E en cada *pull request* o antes de desplegar.
- Ejecución en paralelo y con repetición limitada.
- Trazas, videos y capturas guardados cuando algo falla.
- Una prueba inestable se arregla o se retira: destruye la confianza en toda la batería.

## Errores comunes

- Probar todo por la interfaz: baterías de horas que nadie ejecuta.
- Pausas fijas (`waitForTimeout`).
- Pruebas que dependen del orden o de datos que dejó otra.
- Selectores frágiles.
- Reintentar hasta que pase en lugar de arreglar la inestabilidad.
- E2E contra producción con datos reales.
- Ninguna prueba E2E: la aplicación "pasa todo" y no arranca.

## Taller

1. Escribe tres pruebas E2E para los recorridos críticos de una aplicación tuya.
2. Simula la red en dos de ellas, incluido un caso de error del servidor.
3. Agrega una comprobación automática de accesibilidad a una página.
4. Configura un presupuesto de tamaño del paquete que haga fallar la construcción.
5. Diseña la estrategia de pruebas de la aplicación: qué se prueba en cada nivel y por qué. Una página.
6. Mide cuánto tarda cada nivel y propón el reparto en la integración continua.

Rama `taller/ng-n4-04-e2e`.

## Preguntas senior

1. ¿Qué recorridos merecen una prueba E2E?
2. Pirámide frente a trofeo: ¿de qué depende?
3. ¿Cómo manejas el backend en las pruebas E2E?
4. ¿Qué haces con una prueba inestable?
5. ¿Qué verificaciones automáticas pondrías además de las pruebas funcionales?
