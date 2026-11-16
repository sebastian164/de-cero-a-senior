public class Main {

    public static void main(String[] args) {
        Biblioteca centro = new Biblioteca("Centro");
        centro.agregar(new Libro("Cien años de soledad", "García Márquez", 2));
        centro.agregar(new Libro("Rayuela", "Cortázar", 0));
        centro.agregar(new Libro("Ficciones", "Borges", 1));

        Biblioteca norte = new Biblioteca("Norte");
        norte.agregar(new Libro("Cien años de soledad", "García Márquez", 2));

        centro.prestar("Cien años de soledad");
        try {
            centro.prestar("Rayuela");
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
        try {
            centro.devolver("Rayuela"); // el error de la versión original
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }

        for (Libro libro : centro.libros()) System.out.println(centro.nombre() + " - " + libro);
        for (Libro libro : norte.libros()) System.out.println(norte.nombre() + " - " + libro);

        // Referencia vs objeto: dos referencias, un solo objeto.
        Libro ficciones = new Libro("Ficciones", "Borges", 1);
        Libro otraReferencia = ficciones;
        otraReferencia.prestar();
        System.out.println("Disponibles vistos desde la primera referencia: " + ficciones.disponibles());
        // Dos objetos con los mismos datos son objetos distintos.
        Libro copia = new Libro("Ficciones", "Borges", 1);
        System.out.println("ficciones == otraReferencia: " + (ficciones == otraReferencia));
        System.out.println("ficciones == copia: " + (ficciones == copia));
    }
}
