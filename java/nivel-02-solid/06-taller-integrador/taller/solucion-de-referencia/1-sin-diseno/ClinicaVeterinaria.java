import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Parte 1: "como sea", en una sola clase. */
public class ClinicaVeterinaria {

    private final List<String[]> citas = new ArrayList<>(); // mascota, dueño, correo, tipo, fecha, precio
    private final String archivo;

    public ClinicaVeterinaria(String archivo) {
        this.archivo = archivo;
    }

    public void registrarCita(String mascota, String dueno, String correo, String tipo, LocalDateTime fecha) {
        long precio;
        if (tipo.equals("CONSULTA")) {
            precio = 60000;
        } else if (tipo.equals("VACUNACION")) {
            precio = 45000;
        } else if (tipo.equals("CIRUGIA")) {
            boolean tieneConsulta = false;
            for (String[] c : citas) {
                if (c[0].equals(mascota) && c[3].equals("CONSULTA") && LocalDateTime.parse(c[4]).isBefore(fecha)) {
                    tieneConsulta = true;
                }
            }
            if (!tieneConsulta) {
                throw new RuntimeException("La cirugía requiere una consulta previa");
            }
            precio = 400000;
        } else {
            throw new RuntimeException("Tipo desconocido");
        }
        String[] cita = {mascota, dueno, correo, tipo, fecha.toString(), String.valueOf(precio)};
        citas.add(cita);
        try (FileWriter w = new FileWriter(archivo, true)) {
            w.write(String.join(";", cita) + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[CORREO a " + correo + "] Hola " + dueno + ", " + mascota + " tiene " + tipo + " el " + fecha);
    }

    public void imprimirReporte(LocalDate dia) {
        long total = 0;
        System.out.println("Reporte del " + dia);
        for (String[] c : citas) {
            if (LocalDateTime.parse(c[4]).toLocalDate().equals(dia)) {
                System.out.println(c[4] + " " + c[0] + " (" + c[1] + ") " + c[3] + " " + c[5]);
                total += Long.parseLong(c[5]);
            }
        }
        System.out.println("Total facturado: " + total);
    }

    public static void main(String[] args) throws IOException {
        Path archivo = Files.createTempFile("citas", ".txt");
        ClinicaVeterinaria clinica = new ClinicaVeterinaria(archivo.toString());
        LocalDateTime hoy = LocalDateTime.of(2026, 5, 4, 9, 0);
        clinica.registrarCita("Firulais", "Ana", "ana@correo.example", "CONSULTA", hoy);
        clinica.registrarCita("Firulais", "Ana", "ana@correo.example", "CIRUGIA", hoy.plusHours(2));
        clinica.registrarCita("Michi", "Luis", "luis@correo.example", "VACUNACION", hoy.plusHours(3));
        try {
            clinica.registrarCita("Michi", "Luis", "luis@correo.example", "CIRUGIA", hoy.plusHours(4));
        } catch (RuntimeException e) {
            System.out.println("Rechazada: " + e.getMessage());
        }
        clinica.imprimirReporte(hoy.toLocalDate());
        System.out.println("Líneas en el archivo: " + Files.readAllLines(archivo).size());
        Files.delete(archivo);
    }
}
