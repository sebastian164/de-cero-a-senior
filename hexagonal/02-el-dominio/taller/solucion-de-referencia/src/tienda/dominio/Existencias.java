package tienda.dominio;

/** Las unidades disponibles de un producto. Entidad: su identidad es el producto. Solo ella decide si alcanza. */
public final class Existencias {

    private final String producto;
    private int unidades;

    private Existencias(String producto, int unidades) {
        if (producto == null || producto.isBlank()) {
            throw new ReglaDeNegocioViolada("las existencias son de un producto");
        }
        if (unidades < 0) {
            throw new ReglaDeNegocioViolada("las existencias no pueden ser negativas");
        }
        this.producto = producto;
        this.unidades = unidades;
    }

    public static Existencias de(String producto, int unidades) {
        return new Existencias(producto, unidades);
    }

    public boolean alcanzanPara(int cantidad) {
        return cantidad <= unidades;
    }

    /** No se vende más de lo que hay. */
    public void reservar(int cantidad) {
        exigirPositiva(cantidad);
        if (!alcanzanPara(cantidad)) {
            throw new ReglaDeNegocioViolada("no hay existencias suficientes de " + producto);
        }
        unidades -= cantidad;
    }

    public void devolver(int cantidad) {
        exigirPositiva(cantidad);
        unidades = Math.addExact(unidades, cantidad);
    }

    private static void exigirPositiva(int cantidad) {
        if (cantidad < 1) {
            throw new ReglaDeNegocioViolada("la cantidad debe ser mayor que cero");
        }
    }

    public String producto() {
        return producto;
    }

    public int unidades() {
        return unidades;
    }
}
