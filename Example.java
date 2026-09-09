public class Example {
    public static void main(String[] args) {
        int total = 0;
        for (int i = 0; i < 5; i++) {
            total = total + i;
            System.out.println(total);
        }

        while (total < 10) {
            total = total + 1;
            System.out.println("still going");
        }
    }
}