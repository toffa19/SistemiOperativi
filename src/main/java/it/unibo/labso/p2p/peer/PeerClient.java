package it.unibo.labso.p2p.peer;

import it.unibo.labso.p2p.common.NetUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class PeerClient {

    private PeerClient() {}

    public static void download(String host, int port, String resource, String token, Path dest) throws IOException {
        try (Socket s = new Socket(host, port)) {
            s.setSoTimeout(15_000);

            // invia richiesta ma NON chiudere il writer (chiuderebbe anche il socket)
            BufferedWriter out = NetUtils.writer(s);
            NetUtils.sendLine(out, "DOWNLOAD " + resource + " " + token);

            // leggi header grezzo: "OK <size>\n"
            InputStream in = s.getInputStream();
            String head = readLine(in);
            if (head == null || !head.startsWith("OK"))
                throw new IOException("download refused: " + head);

            String[] parts = head.split("\\s+");
            if (parts.length < 2) throw new IOException("invalid header: " + head);

            long size;
            try { size = Long.parseLong(parts[1]); }
            catch (NumberFormatException e) { throw new IOException("invalid size in header: " + head, e); }

            if (dest.getParent() != null) Files.createDirectories(dest.getParent());

            try (OutputStream fos = Files.newOutputStream(
                    dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                long copied = copyN(in, fos, size);
                if (copied != size) throw new IOException("size mismatch: expected=" + size + " copied=" + copied);
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(64);
        int b; boolean readSomething = false;
        while ((b = in.read()) != -1) {
            readSomething = true;
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        return (!readSomething && b == -1) ? null : sb.toString();
    }

    private static long copyN(InputStream in, OutputStream out, long n) throws IOException {
        byte[] buf = new byte[8192];
        long remaining = n;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int r = in.read(buf, 0, toRead);
            if (r == -1) break;
            out.write(buf, 0, r);
            remaining -= r;
        }
        return n - remaining;
    }
}
