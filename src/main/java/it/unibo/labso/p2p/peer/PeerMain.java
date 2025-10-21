package it.unibo.labso.p2p.peer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

public final class PeerMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PeerMain <masterHost> <masterPort> <resourcesDir>");
            System.exit(2);
        }
        String masterHost = args[0]; int masterPort = Integer.parseInt(args[1]);
        var rm = new ResourceManager(args[2]);
        var server = new PeerServer(0, rm);
        server.start();
        while (server.getBoundPort() == 0) Thread.sleep(10);

        String peerId = "peer-" + UUID.randomUUID();
        MasterClient master = new MasterClient(masterHost, masterPort, peerId, server.getBoundPort());

        try {
            master.register(rm.list());
        } catch (Exception e) {
            System.err.println("[PEER] impossibile contattare il master " + masterHost + ":" + masterPort + " - " + e.getMessage());
            System.exit(1);
            return;
        }

        // hook di shutdown: se il processo viene chiuso, prova ad avvisare il master
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { master.peerQuit(); } catch (Exception ignored) {}
        }));

        new PeerCli(peerId, rm, server, master).run();
    }
}
