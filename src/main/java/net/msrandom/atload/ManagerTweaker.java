package net.msrandom.atload;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class ManagerTweaker implements ITweaker {
    private static final Logger LOGGER = LogManager.getLogger("GradleStart");
    private static final String TWEAKER_SORT_FIELD = "tweakSorting";

    @Override
    @SuppressWarnings("unchecked")
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        String atTweaker = "net.msrandom.atload.AccessTransformerTweaker";
        ((List<String>) Launch.blackboard.get("TweakClasses")).add(atTweaker);

        try {
            Field f = Class.forName("net.minecraftforge.fml.relauncher.CoreModManager", true, classLoader).getDeclaredField(TWEAKER_SORT_FIELD);
            f.setAccessible(true);
            ((Map<String, Integer>) f.get(null)).put(atTweaker, 1001);
        } catch (Throwable t) {
            LOGGER.log(Level.ERROR, "Something went wrong with the adding the AT tweaker adding.");
            t.printStackTrace();
        }
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }
}
