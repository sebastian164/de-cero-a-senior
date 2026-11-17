package precios;

import org.junit.jupiter.api.Test;
import precios.Modelo.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static precios.Modelo.d;
import static precios.Promocion.*;

class MotorTest {
    Producto cafe = new Producto("Café", d("10.00"), "alimentos");
    Producto taza = new Producto("Taza", d("20.00"), "hogar");
    Combo desayuno = new Combo("Desayuno", d("25.00"), List.of(cafe, taza));
    Combo gran = new Combo("Gran desayuno", d("40.00"), List.of(desayuno, cafe));
    Map<String, BigDecimal> iva = Map.of("alimentos", d("0.05"), "hogar", d("0.19"));

    Motor motor(Promocion... ps) { return new Motor(List.of(ps), iva, d("5.00"), d("100.00")); }

    @Test void combos_anidados_cuentan_sus_productos() {
        Map<Producto, Integer> u = new LinkedHashMap<>();
        gran.aplanar(2, u);
        assertThat(u).containsEntry(cafe, 4).containsEntry(taza, 2);
    }

    @Test void el_impuesto_de_un_combo_se_calcula_sobre_su_precio_repartido_no_sobre_el_de_lista() {
        // Desayuno: 25,00 por café (10) + taza (20). Parte del café = 25*10/30 = 8,3333; de la taza = 16,6667.
        // Impuestos = 8,3333*5 % + 16,6667*19 % = 0,4167 + 3,1667 = 3,58 (con precios de lista habrían sido 4,30).
        Resultado r = motor().calcular(new Carrito(List.of(new Linea(desayuno, 1))));
        assertThat(r.subtotal()).isEqualByComparingTo("25.00");
        assertThat(r.impuestos()).isEqualByComparingTo("3.58");
        assertThat(r.envio()).isEqualByComparingTo("5.00");
        assertThat(r.total()).isEqualByComparingTo("33.58");           // 25 + 3,58 + 5
    }

    @Test void un_combo_anidado_con_oferta_reparte_su_precio_en_proporcion_a_la_lista_de_cada_parte() {
        // Oferta: 36,00 por [Desayuno (lista 30), café (10)], lista total 40. Al desayuno le tocan 36*30/40 = 27 y al café 9.
        // Dentro del desayuno: café 27*10/30 = 9 y taza 18. Valores: café 9 + 9 = 18 (2 unidades), taza 18.
        // Impuestos = 18*5 % + 18*19 % = 0,90 + 3,42 = 4,32.
        Combo oferta = new Combo("Oferta", d("36.00"), List.of(desayuno, cafe));
        Resultado r = motor().calcular(new Carrito(List.of(new Linea(oferta, 1))));
        assertThat(r.subtotal()).isEqualByComparingTo("36.00");
        assertThat(r.impuestos()).isEqualByComparingTo("4.32");
        assertThat(r.total()).isEqualByComparingTo("45.32");           // 36 + 4,32 + 5
        // Si el combo vale lo mismo que su lista (Gran desayuno: 40), el reparto no cambia nada: café 20, taza 20.
        Resultado g = motor().calcular(new Carrito(List.of(new Linea(gran, 1))));
        assertThat(g.impuestos()).isEqualByComparingTo("4.80");        // 20*5 % + 20*19 %
        assertThat(g.total()).isEqualByComparingTo("49.80");
    }

    @Test void un_dos_por_uno_sobre_un_producto_que_tambien_viene_en_un_combo_usa_su_precio_medio() {
        // 1 Desayuno + 1 café suelto. Café: 8,3333 (en el combo) + 10 = 18,3333 por 2 unidades, precio medio 9,1667.
        // 2x1 café: 1 unidad gratis = 9,17. Subtotal 35,00; base 25,83; factor 25,83/35 = 0,738.
        // Impuestos = 18,3333*0,738*5 % + 16,6667*0,738*19 % = 0,6765 + 2,337 = 3,0135 -> 3,01.
        Resultado r = motor(new DosPorUno("2x1 café", "Café"))
                .calcular(new Carrito(List.of(new Linea(desayuno, 1), new Linea(cafe, 1))));
        assertThat(r.subtotal()).isEqualByComparingTo("35.00");
        assertThat(r.descuentos().get(0).monto()).isEqualByComparingTo("9.17");
        assertThat(r.impuestos()).isEqualByComparingTo("3.01");
        assertThat(r.total()).isEqualByComparingTo("33.84");           // 25,83 + 3,01 + 5
    }

    @Test void dos_por_uno_y_volumen_se_acumulan_y_se_explican() {
        Resultado r = motor(new DosPorUno("2x1 café", "Café"), new Volumen("Volumen taza", "Taza", 3, d("10")))
                .calcular(new Carrito(List.of(new Linea(cafe, 4), new Linea(taza, 3))));
        assertThat(r.subtotal()).isEqualByComparingTo("100.00");
        assertThat(r.descuentos()).extracting(Descuento::promocion).containsExactly("2x1 café", "Volumen taza");
        assertThat(r.descuentos().get(0).monto()).isEqualByComparingTo("20.00");
        assertThat(r.descuentos().get(1).monto()).isEqualByComparingTo("6.00");
        assertThat(r.explicacion()).contains("2x1 café").contains("Volumen taza");
    }

    @Test void promociones_excluyentes_aplica_solo_la_mejor() {
        Motor m = motor(new Porcentaje("10%", d("10"), "general"), new ValorFijo("5 fijos", d("5"), "general"));
        Resultado r = m.calcular(new Carrito(List.of(new Linea(taza, 4))));
        assertThat(r.descuentos()).extracting(Descuento::promocion).containsExactly("10%");
    }

    @Test void el_orden_de_la_lista_cambia_el_resultado_y_solo_se_define_ahi() {
        Carrito c = new Carrito(List.of(new Linea(taza, 4)), Set.of("PROMO"));
        Resultado a = motor(new ValorFijo("fijo", d("10"), null), new Cupon("cupón", "PROMO", d("10"))).calcular(c);
        Resultado b = motor(new Cupon("cupón", "PROMO", d("10")), new ValorFijo("fijo", d("10"), null)).calcular(c);
        assertThat(a.total()).isNotEqualByComparingTo(b.total());
    }

    @Test void impuestos_por_categoria_y_envio_gratis_sobre_el_monto() {
        Motor m = motor();
        Resultado chico = m.calcular(new Carrito(List.of(new Linea(cafe, 2)))); // 20 + 5% = 21 + envío 5
        assertThat(chico.impuestos()).isEqualByComparingTo("1.00");
        assertThat(chico.envio()).isEqualByComparingTo("5.00");
        assertThat(chico.total()).isEqualByComparingTo("26.00");
        Resultado grande = m.calcular(new Carrito(List.of(new Linea(taza, 5)))); // 100 + 19%
        assertThat(grande.envio()).isEqualByComparingTo("0");
        assertThat(grande.total()).isEqualByComparingTo("119.00");
        assertThat(grande.explicacion()).contains("Envío gratis");
    }

    @Test void agregar_una_promocion_es_agregar_una_clase() {
        record SinDescuentoLosMartes(String nombre) implements Promocion {
            public Optional<Descuento> evaluar(Contexto c) { return Optional.of(new Descuento(nombre, d("1.00"), "nueva")); }
        }
        Resultado r = motor(new SinDescuentoLosMartes("nueva")).calcular(new Carrito(List.of(new Linea(taza, 1))));
        assertThat(r.descuentos()).hasSize(1);
    }
}
