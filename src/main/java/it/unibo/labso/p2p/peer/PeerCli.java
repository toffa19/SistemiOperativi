package it.unibo.labso.p2p.peer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

final class PeerCli implements Runnable {
    private final String peerId; private final ResourceManager rm; private final PeerServer server; private final MasterClient master;
    PeerCli(String id, ResourceManager rm, PeerServer srv, MasterClient mc){ peerId=id; this.rm=rm; server=srv; master=mc; }

    @Override public void run() {
        System.out.println("peer> listdata local | listdata remote | listpeers | whohas <name> | add <name> <content> | download <name> | quit");
        try (var br = new BufferedReader(new InputStreamReader(System.in))) {
            for (String line; (line = br.readLine()) != null; ) {
                var cmd = line.trim();
                if (cmd.isEmpty()) continue;
                try {
                    var head = cmd.split("\\s+", 2)[0];
                    switch (head) {
                        case "quit" -> { safeQuit(); return; }
                        case "listdata" -> {
                            if (cmd.equals("listdata local")) System.out.println(rm.list());
                            else if (cmd.equals("listdata remote")) System.out.println(master.listRemote());
                            else System.out.println("usage: listdata local|remote");
                        }
                        case "listpeers" -> System.out.println(master.listPeers());
                        case "whohas" -> {
                            var parts = cmd.split("\\s+", 2);
                            if (parts.length < 2) { System.out.println("usage: whohas <name>"); break; }
                            System.out.println(master.whoHas(parts[1]));
                        }
                        case "add" -> {
                            var rest = cmd.substring(4);
                            int sp = rest.indexOf(' ');
                            if (sp <= 0) { System.out.println("usage: add <name> <content>"); break; }
                            String name = rest.substring(0, sp);
                            String content = rest.substring(sp+1);
                            rm.add(name, content);
                            master.register(rm.list());
                            System.out.println("ok");
                        }
                        case "download" -> {
                            var parts = cmd.split("\\s+", 2);
                            if (parts.length < 2) { System.out.println("usage: download <name>"); break; }
                            downloadWithRetry(parts[1]);
                        }
                        default -> System.out.println("peer> listdata local | listdata remote | listpeers | whohas <name> | add <name> <content> | download <name> | quit");
                    }
                } catch (Exception e) {
                    System.out.println("ERR: " + e.getMessage());
                }
            }
        } catch (Exception ignored) {}
    }

    private void downloadWithRetry(String name) throws Exception {
        // ciclo: chiedi token → prova peer suggerito → se fallisce: DOWNLOAD_FAILED e ripeti
        while (true) {
            MasterClient.TokenResp tok = master.requestToken(name);
            var from = tok.peer();
            Path dest = rm.resolve("downloaded_" + name);
            try {
                PeerClient.download(from.host(), from.port(), name, tok.token(), dest);
                master.releaseTokenSuccess(tok.token(), tok.resource(), from.id());
                System.out.println("saved -> " + dest);
                return;
            } catch (Exception e) {
                // notifica fallimento e riprova con un altro peer
                try { master.notifyDownloadFailed(name, from.id()); } catch (Exception ignore) {}
                System.out.println("retrying with another peer... (" + e.getMessage() + ")");
                // il master, dopo la notifica, rimuove (peer,resource) e il prossimo token dà un altro peer o NO_RESOURCE
                // Se il master non ha altri peer, lancia ERR NO_RESOURCE e qui esce con eccezione
            }
        }
    }

    private void safeQuit() {
        try { master.peerQuit(); } catch (Exception ignored) {}
    }
}
