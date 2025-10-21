package it.unibo.labso.p2p.peer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;

final class PeerServer {
    private final int port;
    private final ResourceManager rm;
    private final Semaphore single = new Semaphore(1); // mutua esclusione
    private volatile boolean running = false;
    private int boundPort;

    PeerServer(int port, ResourceManager rm){ this.port=port; this.rm=rm; }

    void start() throws IOException {
        Thread t = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                boundPort = ss.getLocalPort(); running = true;
                System.out.println("[PEER] server on port " + boundPort);
                while (running) new Thread(new PeerRequestHandler(ss.accept(), rm, single)).start();
            } catch (IOException e) { System.err.println("[PEER] server error: " + e.getMessage()); }
        }, "peer-acceptor");
        t.setDaemon(true); t.start();
    }

    int getBoundPort(){ return boundPort; }
}
