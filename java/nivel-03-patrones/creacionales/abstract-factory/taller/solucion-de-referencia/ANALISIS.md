# Abstract Factory — análisis

## 1. Fábrica y dos familias
`FabricaDocumento` declara un método de creación por producto. `FabricaHtml` y `FabricaTexto` son las dos familias. `GeneradorReporte` es el cliente y solo recibe la fábrica: es imposible combinar un encabezado HTML con una tabla de texto.

## 2. Tercera familia (Markdown)
Se **agregó** una clase (`FabricaMarkdown`). Ninguna clase existente cambió: ni la interfaz, ni las otras fábricas, ni el cliente. Solo cambia quien elige la fábrica (en `Main`, la lista).

## 3. Cuarto producto (Gráfico)
Cambiaron: la interfaz `FabricaDocumento` (un método nuevo), **las tres** fábricas existentes (cada una implementa `crearGrafico`), y el cliente (para usarlo). Se agregó la interfaz `Grafico`. Cada familia tiene su propio gráfico (Markdown usa una lista con barras, no el de la familia texto), para que ninguna familia dependa de otra familia concreta.

| Cambio | Clases nuevas | Clases modificadas |
|---|---|---|
| Familia nueva | 1 | 0 |
| Producto nuevo | 1 | 1 interfaz + N fábricas + cliente |

Conclusión: Abstract Factory es abierto a familias y cerrado a productos. Si los productos cambian más que las familias, el patrón cuesta caro. La prueba `cadaFamiliaCreaLosCuatroProductos` comprueba que la interfaz quedó con 4 métodos.

## 4. Con perfiles de Spring
Cada familia es una clase `@Configuration` con `@Profile`; el contenedor entrega la familia del perfil activo y el cliente la recibe inyectada. Esto se comprobó con un proyecto Spring de pruebas (prueba `PerfilesYEstrategiasTest.elPerfilEligeLaFamiliaCompleta`): con `spring.profiles.active=html` el bean `GeneradorReporte` usa piezas HTML, con `markdown` usa piezas Markdown.

```java
@Configuration @Profile("html")
class FamiliaHtml {
    @Bean Encabezado encabezado() { return t -> "<h1>" + t + "</h1>"; }
    @Bean Pie pie() { return t -> "<footer>" + t + "</footer>"; }
}
@Configuration @Profile("markdown")
class FamiliaMarkdown {
    @Bean Encabezado encabezado() { return t -> "# " + t; }
    @Bean Pie pie() { return t -> "_" + t + "_"; }
}
```
