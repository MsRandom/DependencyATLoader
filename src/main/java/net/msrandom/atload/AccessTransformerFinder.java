package net.msrandom.atload;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class AccessTransformerFinder {
    private static final Logger LOGGER = LogManager.getLogger("GradleStart");

    private static final String MOD_ATD_CLASS = "net.minecraftforge.fml.common.asm.transformers.ModAccessTransformer";
    private static final String MOD_AT_METHOD = "addJar";

    private static final Attributes.Name FMLAT = new Attributes.Name("FMLAT");

    public static void searchClasspath() throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
        AtRegistrar atRegistrar = new AtRegistrar();

        ClassLoader classLoader = ClientGameStarter.class.getClassLoader();
        URL[] urls;

        if (classLoader instanceof URLClassLoader) {
            urls = ((URLClassLoader) classLoader).getURLs();
        } else {
            Field ucpField = classLoader.getClass().getSuperclass().getDeclaredField("ucp");
            ucpField.setAccessible(true);

            Object ucp = ucpField.get(classLoader);
            Method getURLs = ucp.getClass().getDeclaredMethod("getURLs");
            getURLs.setAccessible(true);
            urls = (URL[]) getURLs.invoke(ucp);
        }

        for (URL url : urls) {
            try {
                searchMod(url, atRegistrar);
            } catch (IOException | InvocationTargetException | IllegalAccessException | URISyntaxException e) {
                LOGGER.warn("AccessTransformerFinder failed to search for mod jar at url {}", url, e);
            }
        }
    }

    private static void searchMod(URL url, AtRegistrar atRegistrar) throws IOException, InvocationTargetException, IllegalAccessException, URISyntaxException {
        if (!url.getProtocol().startsWith("file"))
            return;

        File mod = new File(url.toURI().getPath());

        if (!mod.exists())
            return;

        if (mod.toString().endsWith("jar")) {
            try (JarFile jar = new JarFile(mod)) {
                Manifest manifest = jar.getManifest();
                if (manifest != null) {
                    atRegistrar.addJar(jar, manifest);
                }
            }
        } else if (mod.isDirectory()) {
            processDirectoryAT(mod, atRegistrar);
        }
    }

    private static void processDirectoryAT(File mod, AtRegistrar atRegistrar) throws IOException, InvocationTargetException, IllegalAccessException {
        // For subprojects to be able to load ATs, in 1.13+ this is not an issue as all mods are loaded the same way,
        // which includes classpath mods, and loading their accesstransformer.cfg file.
        // In FG 2.3 and below, this was never addressed AFAIK(there is a chance that FG looked for the directory metadata and loaded it, don't know.),
        // if your dependency was not a Jar, you'd just never get the access transformer applied. So this is a solution to that.
        File meta = new File(mod, "META-INF");
        File manifestFile = new File(meta, "MANIFEST.MF");
        if (manifestFile.exists()) {
            File tempJar = new File(mod.getAbsolutePath() + ".jar");
            boolean createJar = false;
            if (tempJar.exists()) {
                try (JarFile jar = new JarFile(tempJar)) {
                    String fmlat = jar.getManifest().getMainAttributes().getValue(FMLAT);
                    if (fmlat == null || fmlat.isEmpty()) {
                        createJar = true;
                    } else {
                        for (String at : fmlat.split(" ")) {
                            try (
                                    InputStream jarAt = jar.getInputStream(jar.getJarEntry("META-INF/" + at));
                                    InputStream atFile = new FileInputStream(new File(meta, at))
                            ) {
                                if (!IOUtils.contentEquals(jarAt, atFile)) {
                                    createJar = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (IOException exception) {
                    LOGGER.debug("File " + tempJar.getName() + " is probably corrupted, attempting to recreate.", exception);
                    createJar = true;
                }
            } else {
                createJar = true;
            }
            if (createJar) {
                Manifest manifest;
                try (InputStream manifestInput = new FileInputStream(manifestFile)) {
                    manifest = new Manifest(manifestInput);
                }

                String fmlat = manifest.getMainAttributes().getValue(FMLAT);
                if (fmlat != null && !fmlat.isEmpty()) {
                    try (JarOutputStream jarOutput = new JarOutputStream(new FileOutputStream(tempJar))) {
                        jarOutput.putNextEntry(new JarEntry("META-INF/"));
                        jarOutput.closeEntry();
                        jarOutput.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
                        try (InputStream manifestInput = new FileInputStream(manifestFile)) {
                            IOUtils.copy(manifestInput, jarOutput);
                        }
                        jarOutput.closeEntry();
                        for (String at : fmlat.split(" ")) {
                            jarOutput.putNextEntry(new JarEntry("META-INF/" + at + "/"));
                            try (InputStream fileInput = new FileInputStream(new File(meta, at))) {
                                IOUtils.copy(fileInput, jarOutput);
                            }
                            jarOutput.closeEntry();
                        }
                    }
                }
            }
            if (tempJar.exists()) {
                try (JarFile jar = new JarFile(tempJar)) {
                    atRegistrar.addJar(jar, jar.getManifest());
                }
            }
        }
    }

    private static final class AtRegistrar {
        private Method addJar = null;

        private AtRegistrar() {
            try {
                Class<?> modAtdClass = Class.forName(MOD_ATD_CLASS);
                try {
                    addJar = modAtdClass.getDeclaredMethod(MOD_AT_METHOD, JarFile.class, String.class);
                } catch (NoSuchMethodException | SecurityException ignored) {
                    LOGGER.error("Failed to find method {}.{}", MOD_ATD_CLASS, MOD_AT_METHOD);
                }
            } catch (ClassNotFoundException e) {
                LOGGER.error("Failed to find class {}", MOD_ATD_CLASS);
            }
        }

        public void addJar(JarFile jarFile, Manifest manifest) throws InvocationTargetException, IllegalAccessException {
            if (addJar != null) {
                String ats = manifest.getMainAttributes().getValue(FMLAT);
                if (ats != null && !ats.isEmpty()) {
                    addJar.invoke(null, jarFile, ats);
                }
            }
        }
    }

    public static final class Remapper implements IClassTransformer {
        public Remapper() {
            remapModifiers((LaunchClassLoader) getClass().getClassLoader());
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void remapModifiers(LaunchClassLoader classloader) {
            Class<? extends IClassTransformer> clazz = null;
            IClassTransformer instance = null;

            for (IClassTransformer transformer : classloader.getTransformers()) {
                if (transformer.getClass().getCanonicalName().endsWith(MOD_ATD_CLASS)) {
                    clazz = transformer.getClass();
                    instance = transformer;
                }
            }

            if (clazz == null) {
                LOGGER.log(Level.ERROR, "ModAccessTransformer was somehow not found.");
                return;
            }
            Collection<Object> modifiers;
            try {
                Field f = clazz.getSuperclass().getDeclaredFields()[1];
                f.setAccessible(true);
                modifiers = ((com.google.common.collect.Multimap) f.get(instance)).values();
            } catch (Throwable t) {
                LOGGER.log(Level.ERROR, "AccessTransformer.modifiers field was somehow not found...");
                return;
            }

            if (modifiers.isEmpty()) {
                return;
            }

            Field nameField = null;
            try {
                Optional<Object> mod = modifiers.stream().findFirst();
                nameField = mod.get().getClass().getFields()[0];
                nameField.setAccessible(true);
            } catch (Throwable t) {
                LOGGER.log(Level.ERROR, "AccessTransformer.Modifier.name field was somehow not found...");
            }

            Map<String, String> nameMap = Maps.newHashMap();
            try {
                readCsv(getClass().getResourceAsStream("/fields.csv"), nameMap);
                readCsv(getClass().getResourceAsStream("/methods.csv"), nameMap);
            } catch (IOException e) {
                LOGGER.log(Level.ERROR, "Could not load CSV files!");
                e.printStackTrace();
                return;
            }

            for (Object modifier : modifiers) {
                String name;
                try {
                    name = (String) nameField.get(modifier);
                    String newName = nameMap.get(name);
                    if (newName != null) {
                        nameField.set(modifier, newName);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        private void readCsv(InputStream file, Map<String, String> map) throws IOException {
            if (file != null) {
                LOGGER.log(Level.DEBUG, "Reading CSV file: {}", file);
                Splitter split = Splitter.on(',').trimResults().limit(3);
                Scanner scanner = new Scanner(file);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("searge"))
                        continue;

                    List<String> splits = split.splitToList(line);
                    map.put(splits.get(0), splits.get(1));
                }
            }
        }

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            return basicClass;
        }
    }
}
