package net.msrandom.atload;

import java.lang.reflect.InvocationTargetException;

public class GameStarter {
    public static String[] hackRun(String[] args) throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        AccessTransformerFinder.searchClasspath();
        String[] newArgs = new String[args.length + 2];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = "--tweakClass";
        newArgs[args.length + 1] = "net.msrandom.atload.tweaker.ManagerTweaker";
        return newArgs;
    }
}
