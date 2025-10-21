package it.unibo.labso.p2p.common;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class NetUtils {
    private NetUtils() {}
    public static BufferedReader reader(Socket s, int timeoutMs) throws IOException {
        s.setSoTimeout(timeoutMs);
        return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
    }
    public static BufferedWriter writer(Socket s) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }
    public static void sendLine(BufferedWriter w, String line) throws IOException {
        w.write(line); w.write('\n'); w.flush();
    }
}
