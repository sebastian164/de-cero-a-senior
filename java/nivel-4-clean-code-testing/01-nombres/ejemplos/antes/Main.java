import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<int[]> l = new ArrayList<>();
        l.add(new int[] {1, 45, 0});
        l.add(new int[] {2, 10, 1});
        l.add(new int[] {3, 95, 0});
        System.out.println(proc(l));
    }

    static List<Integer> proc(List<int[]> l) {
        List<Integer> r = new ArrayList<>();
        for (int[] x : l) {
            if (x[1] > 30 && x[2] == 0) {
                r.add(x[0]);
            }
        }
        return r;
    }
}
