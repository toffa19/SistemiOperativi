package it.unibo.labso.p2p;

import it.unibo.labso.p2p.peer.PeerMain;
import java.io.File;
import java.net.InetAddress;

public final class Client {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: Client <masterHost> <masterPort>");
            System.exit(2);
        }
        String host = args[0];
        String port = args[1];

        // cartella dati di default (se vuoi, sostituisci con un terzo argomento esplicito)
        String baseDir;
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            baseDir = "data" + File.separator + "peer-" + hostName;
        } catch (Exception e) {
            baseDir = "data" + File.separator + "peer-default";
        }
        new File(baseDir).mkdirs();

        PeerMain.main(new String[]{host, port, baseDir});
    }
}
