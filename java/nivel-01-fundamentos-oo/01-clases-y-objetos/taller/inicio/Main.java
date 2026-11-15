public class Main {

    static String[] titulos = {"Cien años de soledad", "Rayuela", "Ficciones"};
    static String[] autores = {"García Márquez", "Cortázar", "Borges"};
    static int[] disponibles = {2, 0, 1};

    public static void main(String[] args) {
        prestar(0);
        prestar(1);
        devolver(1);
        for (int i = 0; i < titulos.length; i++) {
            System.out.println(titulos[i] + " (" + autores[i] + "): " + disponibles[i]);
        }
    }

    static void prestar(int i) {
        if (disponibles[i] > 0) {
            disponibles[i]--;
        } else {
            System.out.println("No hay ejemplares de " + titulos[i]);
        }
    }

    static void devolver(int i) {
        disponibles[i]++;
    }
}
