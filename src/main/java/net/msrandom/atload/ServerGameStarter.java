package net.msrandom.atload;

import net.minecraftforge.legacydev.MainClient;
import net.minecraftforge.legacydev.MainServer;

public class ServerGameStarter {
    public static void main(String[] args) throws Exception {
        MainServer.main(GameStarter.hackRun(args));
    }
}
