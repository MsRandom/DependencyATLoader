package net.msrandom.atload;

public class GameStarter {
    public static String[] hackRun(String[] args) {
        AccessTransformerFinder.searchClasspath();
        String[] newArgs = new String[args.length + 2];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = "--tweakClass";
        newArgs[args.length + 1] = "net.msrandom.atload.CoremodTweaker";
        return newArgs;
    }
}
