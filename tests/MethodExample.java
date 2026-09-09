public class MethodExample {
    public static void main(String[] args) {
        System.out.println(square(5));
        System.out.println(average(2.0, 4.0));
        if (enabled(true, 1)) {
            announce(greeting("Ada"));
        }
    }

    public static int square(int number) {
        return number * number;
    }

    public static double average(double a, double b) {
        return (a + b) / 2.0;
    }

    public static boolean enabled(boolean flag, int count) {
        return flag && count > 0;
    }

    public static void announce(String text) {
        System.out.println(text);
        return;
    }

    public static String greeting(String name) {
        return "Hello " + name;
    }
}
