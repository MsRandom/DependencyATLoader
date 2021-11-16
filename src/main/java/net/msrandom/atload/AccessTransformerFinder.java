package net.msrandom.atload;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class AccessTransformerFinder {
    private static final Logger LOGGER = LogManager.getLogger("GradleStart");

    private static final String MOD_ATD_CLASS = "net.minecraftforge.fml.common.asm.transformers.ModAccessTransformer";
    private static final String MOD_AT_METHOD = "addJar";

    public static void searchClasspath() {
        AtRegistrar atRegistrar = new AtRegistrar();

        URLClassLoader urlClassLoader = (URLClassLoader) ClientGameStarter.class.getClassLoader();
        for (URL url : urlClassLoader.getURLs()) {
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
        }
    }

    private static final class AtRegistrar {
        private static final Attributes.Name FMLAT = new Attributes.Name("FMLAT");

        @Nullable
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
            doStuff((LaunchClassLoader) getClass().getClassLoader());
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void doStuff(LaunchClassLoader classloader) {
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

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            return basicClass;
        }
    }
}
