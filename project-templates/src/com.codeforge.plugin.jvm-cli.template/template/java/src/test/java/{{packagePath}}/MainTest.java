package {{packageName}};

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MainTest {
    @Test
    public void greetsWorldByDefault() {
        assertEquals("Hallo, Welt!", Main.greet(null));
    }

    @Test
    public void greetsName() {
        assertEquals("Hallo, Thomas!", Main.greet("Thomas"));
    }
}
