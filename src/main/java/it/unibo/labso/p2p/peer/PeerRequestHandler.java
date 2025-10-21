package it.unibo.labso.p2p.peer;

import it.unibo.labso.p2p.common.NetUtils;
import java.io.*; import java.net.*; import java.nio.file.*;

final class PeerRequestHandler implements Runnable {
    private final Socket s; private final ResourceManager rm; private final java.util.concurrent.Semaphore single;
    PeerRequestHandler(Socket s, ResourceManager rm, java.util.concurrent.Semaphore sem){ this.s=s; this.rm=rm; this.single=sem; }

    @Override public void run() {
        try (s; var in = NetUtils.reader(s, 15000); var out = NetUtils.writer(s)) {
            String line = in.readLine(); // DOWNLOAD <name> <token>
            if (line == null) return;
            String[] p = line.split("\\s+", 3);
            if (p.length < 3 || !"DOWNLOAD".equals(p[0])) { NetUtils.sendLine(out, "ERR"); return; }
            String resource = p[1];
            // semplice: non validiamo il token lato peer (si può estendere)
            single.acquire();
            try {
                var file = rm.resolve(resource);
                if (Files.notExists(file)) { NetUtils.sendLine(out, "ERR NO_SUCH_RESOURCE"); return; }
                long size = Files.size(file);
                NetUtils.sendLine(out, "OK " + size);
                try (var inFile = Files.newInputStream(file)) { inFile.transferTo(s.getOutputStream()); }
            } finally {
                single.release();
            }
        } catch (Exception ignored) {}
    }
}
