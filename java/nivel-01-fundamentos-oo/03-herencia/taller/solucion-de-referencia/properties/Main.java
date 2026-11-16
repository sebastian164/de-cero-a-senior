import java.io.StringWriter;
import java.util.Properties;

/** Punto 5: el problema concreto de Properties extends Hashtable<Object,Object>. */
public class Main {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.setProperty("puerto", "8080");
        props.put("timeout", 30);          // compila: put viene de Hashtable<Object,Object>
        System.out.println("getProperty(\"timeout\") = " + props.getProperty("timeout"));
        System.out.println("get(\"timeout\") = " + props.get("timeout"));
        try {
            props.store(new StringWriter(), null);
            System.out.println("store: sin error");
        } catch (ClassCastException e) {
            System.out.println("store: ClassCastException");
        }
    }
}
