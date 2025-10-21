package it.unibo.labso.p2p.master;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class MasterMain {

    private MasterMain() { }

    public static void main(String[] args) throws Exception {
        int port = 9000;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("usage: MasterMain <port>");
                System.exit(2);
                return;
            }
        }

        MasterServer server = new MasterServer(port);
        server.start();

        // chiusura pulita se il processo termina
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

        System.out.println("master> listdata | inspectNodes | log | quit");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                switch (line) {
                    case "listdata" -> System.out.println(server.registry().snapshotAll());
                    case "inspectNodes" -> System.out.println(server.registry().snapshotPeers());
                    case "log" -> System.out.println(server.downloadLog().snapshot());
                    case "quit" -> {
                        server.shutdown();
                        System.out.println("[MASTER] bye");
                        return;
                    }
                    default -> System.out.println("listdata | inspectNodes | log | quit");
                }
            }
        }
    }
}
