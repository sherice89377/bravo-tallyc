package tally.test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * A tiny test runner (the repository uses the JDK alone). Every public static void method whose
 * name starts with "test" in the listed classes is a test; it passes when it returns.
 * Exit status 0 when every test passes, 1 otherwise.
 */
public final class TestRunner {
    private static final List<Class<?>> SUITES = List.of(
            FrontEndTest.class, InterpreterTest.class, VmTest.class, CompilerTest.class);

    private TestRunner() {}

    public static void main(String[] args) throws Exception {
        int passed = 0;
        int failed = 0;
        for (Class<?> suite : SUITES) {
            for (Method m : suite.getDeclaredMethods()) {
                if (!m.getName().startsWith("test") || !Modifier.isStatic(m.getModifiers())) continue;
                try {
                    m.invoke(null);
                    passed++;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    failed++;
                    System.out.println("FAIL " + suite.getSimpleName() + "." + m.getName() + ": " + e.getCause());
                }
            }
        }
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
