package contenedor.alcances;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Scope("prototype")
public class Carrito {
    public final List<String> productos = new ArrayList<>();
}
