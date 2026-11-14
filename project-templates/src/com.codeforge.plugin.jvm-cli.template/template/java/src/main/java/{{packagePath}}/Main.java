package {{packageName}};

/** Begrüßt den Namen aus dem ersten Argument. */
public final class Main {
    private Main() {}

    static String greet(String name) {
        return "Hallo, " + (name == null || name.isBlank() ? "Welt" : name) + "!";
    }

    public static void main(String[] args) {
        System.out.println(greet(args.length > 0 ? args[0] : null));
    }
}
