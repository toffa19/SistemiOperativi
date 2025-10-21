package it.unibo.labso.p2p.peer;

import it.unibo.labso.p2p.common.*;
import java.io.*; import java.net.*; import java.util.*;

final class MasterClient {
    private final String host; private final int port; private final String peerId; private final int localPort;
    MasterClient(String h, int p, String peerId, int localPort){ host=h; port=p; this.peerId=peerId; this.localPort=localPort; }

    record RegisterReq(String peerId, String host, int port, java.util.List<String> resources) {}
    record ListResp(java.util.Map<String, java.util.List<String>> index) {}
    record PeersResp(java.util.Map<String, PeerRef> peers) {}
    record WhoHasReq(String resource) {}
    record TokenResp(String token, String resource, PeerRef peer) {}
    record TokenRel(String token, String resource, String fromPeerId, String requesterPeerId) {}

    void register(java.util.List<String> resources) throws IOException {
        try (var s = new Socket(host, port);
             var in = NetUtils.reader(s, 8000); var out = NetUtils.writer(s)) {
            String json = JsonCodec.toJson(new RegisterReq(peerId, InetAddress.getLocalHost().getHostAddress(), localPort, resources));
            NetUtils.sendLine(out, "REGISTER " + json);
            mustOk(in.readLine());
        }
    }

    void peerQuit() throws IOException {
        try (var s = new Socket(host, port);
             var in = NetUtils.reader(s, 5000); var out = NetUtils.writer(s)) {
            NetUtils.sendLine(out, "PEER_QUIT " + JsonCodec.toJson(Map.of("peerId", peerId)));
            mustOk(in.readLine());
        }
    }

    Map<String, List<String>> listRemote() throws IOException {
        try (var s = new Socket(host, port);
             var in = NetUtils.reader(s, 8000); var out = NetUtils.writer(s)) {
            NetUtils.sendLine(out, "LISTDATA_REMOTE");
            String resp = mustOk(in.readLine());
            return JsonCodec.fromJson(resp, ListResp.class).index();
        }
    }

    Map<String, PeerRef> listPeers() throws IOException {
        try (var s = new Socket(host, port);
             var in = NetUtils.reader(s, 8000); var out = NetUtils.writer(s)) {
            NetUtils.sendLine(out, "LIST_PEERS");
            String resp = mustOk(in.readLine());
            return JsonCodec.fromJson(resp, PeersResp.class).peers();
        }
    }

    List<PeerRef> whoHas(String resource) throws IOException {
        try (var s = new Socket(host, port);
             var in = NetUtils.reader(s, 8000); var out = NetUtils.writer(s)) {
            NetUtils.sendLine(out, "WHO_HAS " + JsonCodec.toJson(new WhoHasReq(resource)));
            String resp = mustOk(in.readLine());
            PeerRef[] arr = JsonCodec.fromJson(resp, PeerRef[].class);
            return java.util.Arrays.asList(arr);
        }
    }

    TokenResp requestToken(String resource) throws IOException {
        try (var s = new Socket(host, port);
             var in = NetUtils.reader(s, 8000); var out = NetUtils.writer(s)) {
            NetUtils.sendLine(out, "DOWNLOAD_TOKEN_REQ " + JsonCodec.toJson(Map.of(
                    "resource", resource, "requesterPeerId", peerId)));
            String resp = in.readLine();
            if (resp == null || !resp.startsWith("OK")) throw new IOException(resp);
            return JsonCodec.fromJson(resp.substring(3).trim(), TokenResp.class);
        }
    }

    void releaseTokenSuccess(String token, String resource, String fromPeerId) throws IOException {
        try (var s = new Socket(host, port);
             var in = NetUtils.reader(s, 8000); var out = NetUtils.writer(s)) {
            NetUtils.sendLine(out, "DOWNLOAD_TOKEN_REL " + JsonCodec.toJson(
                    new TokenRel(token, resource, fromPeerId, peerId)));
            mustOk(in.readLine());
        }
    }

    void notifyDownloadFailed(String resource, String fromPeerId) throws IOException {
        try (var s = new Socket(host, port);
             var in = NetUtils.reader(s, 8000); var out = NetUtils.writer(s)) {
            NetUtils.sendLine(out, "DOWNLOAD_FAILED " + JsonCodec.toJson(Map.of(
                    "resource", resource,
                    "peerId", fromPeerId,
                    "requesterPeerId", peerId
            )));
            mustOk(in.readLine());
        }
    }

    private static String mustOk(String line) throws IOException {
        if (line == null) throw new IOException("master closed");
        if (!line.startsWith("OK")) throw new IOException(line);
        // Gestisci sia "OK" che "OK <payload>"
        if (line.length() == 2) return "";
        // se c'è spazio/payload dopo "OK"
        int i = 2;
        if (line.charAt(2) == ' ') i = 3;   // tollera "OK " oppure "OK<qualcosa>"
        if (i >= line.length()) return "";
        return line.substring(i).trim();
    }
}
