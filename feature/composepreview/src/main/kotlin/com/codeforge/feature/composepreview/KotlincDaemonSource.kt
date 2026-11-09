/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

/**
 * Java-Quelle des warmen Compiler-Prozesses. Wird per `java KotlincDaemon.java <kotlin-lib>` (Source-Launcher, JDK 11+)
 * gestartet, damit kein `javac`-Schritt und keine vorgebaute JAR nötig sind.
 *
 * Protokoll (stdin/stdout, UTF-8): Anfrage = eine Zeile, Argumente getrennt durch U+0001.
 * Antwort = Compiler-Ausgabe, dann eine Zeile `U+0002EXIT:<code>`. Beim Start einmal `U+0002READY`.
 */
internal const val KOTLINC_DAEMON_SOURCE = """
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class KotlincDaemon {
    /** D8 als Bibliothek (kein System.exit): 0 = ok, 1 = Fehler (Meldung in ps). */
    static int runD8(String jar, String[] args, PrintStream ps, Map<String, URLClassLoader> loaders) {
        try {
            URLClassLoader cl = loaders.get(jar);
            if (cl == null) {
                cl = new URLClassLoader(new URL[] { new File(jar).toURI().toURL() }, ClassLoader.getPlatformClassLoader());
                loaders.put(jar, cl);
            }
            Class<?> originC = Class.forName("com.android.tools.r8.origin.Origin", true, cl);
            Object unknown = originC.getMethod("unknown").invoke(null);
            Class<?> cmdC = Class.forName("com.android.tools.r8.D8Command", true, cl);
            Object builder = cmdC.getMethod("parse", String[].class, originC).invoke(null, args, unknown);
            Method build = builder.getClass().getMethod("build");
            build.setAccessible(true);
            Object command = build.invoke(builder);
            Class.forName("com.android.tools.r8.D8", true, cl).getMethod("run", cmdC).invoke(null, command);
            return 0;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            ps.println(c);
            return 1;
        }
    }

    public static void main(String[] a) throws Exception {
        File lib = new File(a[0]);
        File[] jars = lib.listFiles((d, n) -> n.endsWith(".jar") && !n.startsWith("compose-compiler-plugin"));
        if (jars == null || jars.length == 0) { System.err.println("keine Compiler-JARs in " + lib); System.exit(3); }
        Arrays.sort(jars);
        List<URL> urls = new ArrayList<>();
        for (File j : jars) urls.add(j.toURI().toURL());
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
        Thread.currentThread().setContextClassLoader(cl);
        System.setProperty("kotlin.home", lib.getParentFile().getAbsolutePath());
        System.setProperty("java.awt.headless", "true");
        Class<?> k = Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", true, cl);
        Method exec = k.getMethod("exec", PrintStream.class, String[].class);
        PrintStream proto = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        proto.println("\u0002READY");
        Map<String, URLClassLoader> d8Loaders = new HashMap<>();
        String line;
        while ((line = in.readLine()) != null) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(buf, true, "UTF-8");
            PrintStream so = System.out, se = System.err;
            System.setOut(ps); System.setErr(ps);
            int code;
            try {
                String[] parts = line.split("\u0001", -1);
                if (parts.length > 2 && parts[0].equals("D8")) {
                    code = runD8(parts[1], Arrays.copyOfRange(parts, 2, parts.length), ps, d8Loaders);
                } else {
                    Object compiler = k.getDeclaredConstructor().newInstance();
                    Object r = exec.invoke(compiler, ps, parts);
                    try { code = (Integer) r.getClass().getMethod("getCode").invoke(r); }
                    catch (Exception e) { code = ((Enum<?>) r).ordinal(); }
                }
            } catch (Throwable t) {
                Throwable c = t.getCause() != null ? t.getCause() : t;
                c.printStackTrace(ps);
                code = (c instanceof OutOfMemoryError) ? 4 : 2;
            } finally {
                System.setOut(so); System.setErr(se);
            }
            proto.print(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            proto.print("\n\u0002EXIT:" + code + "\n");
            proto.flush();
        }
    }
}
"""
