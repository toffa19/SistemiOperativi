package it.unibo.labso.p2p.master;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server del nodo master: accetta connessioni e delega a ClientHandler.
 * - Mutua esclusione sul registro demandata a Registry.
 * - Shutdown pulito: chiude la ServerSocket e interrompe il pool.
 */
final class MasterServer {
    private final int port;
    private volatile boolean running = false;

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Registry registry = new Registry();
    private final TokenStore tokens = new TokenStore();
    private final DownloadLog downloadLog = new DownloadLog();

    private ServerSocket serverSocket;   // chiusa in shutdown()
    private Thread acceptorThread;       // thread che esegue la accept-loop

    MasterServer(int port) {
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("invalid port: " + port);
        this.port = port;
    }

    /**
     * Avvia il server (idempotente). L'acceptor gira in background.
     */
    synchronized void start() {
        if (running) return;
        running = true;

        acceptorThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                serverSocket.setReuseAddress(true);
                System.out.println("[MASTER] listening on " + port + " (commands: listdata | inspectNodes | log | quit)");

                while (running) {
                    try {
                        Socket s = serverSocket.accept();
                        pool.submit(new ClientHandler(s, registry, tokens, downloadLog));
                    } catch (IOException e) {
                        if (running) System.err.println("[MASTER] accept error: " + e.getMessage());
                        // se non running, lo shutdown ha chiuso la socket: esci dal loop
                    }
                }
            } catch (IOException e) {
                if (running) System.err.println("[MASTER] fatal error: " + e.getMessage());
            } finally {
                closeServerSocketQuietly();
            }
        }, "master-acceptor");

        acceptorThread.setDaemon(true);
        acceptorThread.start();
    }

    /**
     * Arresta il server: chiude la ServerSocket per sbloccare accept(),
     * interrompe i task nel pool e interrompe il thread acceptor.
     */
    synchronized void shutdown() {
        if (!running) return;
        running = false;
        closeServerSocketQuietly(); // sblocca accept()
        pool.shutdownNow();
        if (acceptorThread != null) acceptorThread.interrupt();
    }

    Registry registry() { return registry; }
    TokenStore tokens() { return tokens; }
    DownloadLog downloadLog() { return downloadLog; }

    private void closeServerSocketQuietly() {
        ServerSocket ss = this.serverSocket;
        this.serverSocket = null;
        if (ss != null && !ss.isClosed()) {
            try { ss.close(); } catch (IOException ignored) {}
        }
    }
}
