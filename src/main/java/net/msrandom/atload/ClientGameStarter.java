package net.msrandom.atload;

import net.minecraftforge.legacydev.MainClient;

public class ClientGameStarter {
    public static void main(String[] args) throws Exception {
        MainClient.main(GameStarter.hackRun(args));
    }
}
