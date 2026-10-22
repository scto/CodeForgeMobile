public class TargetApp {
    public static void main(String[] args) {
        System.out.println("TargetApp started");
        int a = 10;
        int b = 20;
        int sum = calculate(a, b);
        System.out.println("Sum is " + sum);
    }

    private static int calculate(int x, int y) {
        int result = x + y; // Breakpoint here
        return result;
    }
}
