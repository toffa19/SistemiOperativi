package it.unibo.labso.p2p.master;

import it.unibo.labso.p2p.common.*;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;

/**
 * Comandi supportati:
 *  - REGISTER {peerId,host,port,resources:[...]}
 *  - LISTDATA_REMOTE
 *  - LIST_PEERS
 *  - WHO_HAS {resource}
 *  - DOWNLOAD_TOKEN_REQ {resource, requesterPeerId}
 *  - DOWNLOAD_TOKEN_REL {token,resource,fromPeerId,requesterPeerId}
 *  - DOWNLOAD_FAILED {resource, peerId, requesterPeerId}
 *  - PEER_QUIT {peerId}
 *  - QUIT
 */
final class ClientHandler implements Runnable {
    private final Socket socket;
    private final Registry reg;
    private final TokenStore tokens;
    private final DownloadLog dlog;

    ClientHandler(Socket s, Registry r, TokenStore t, DownloadLog dlog){ socket=s; reg=r; tokens=t; this.dlog=dlog; }

    /* ==== DTO ==== */
    record RegisterReq(String peerId, String host, int port, List<String> resources) {}
    record ListResp(Map<String, List<String>> index) {}
    record PeersResp(Map<String, PeerRef> peers) {}
    record WhoHasReq(String resource) {}
    record TokenReq(String resource, String requesterPeerId) {}
    record TokenResp(String token, String resource, PeerRef peer) {}
    record TokenRel(String token, String resource, String fromPeerId, String requesterPeerId) {}
    record DownloadFailedReq(String resource, String peerId, String requesterPeerId) {}
    record PeerQuitReq(String peerId) {}

    @Override public void run() {
        try (socket; var in = NetUtils.reader(socket, 15000); var out = NetUtils.writer(socket)) {
            for (String line; (line = in.readLine()) != null; ) {
                if (line.isBlank()) continue;
                String[] parts = line.split(" ", 2);
                String cmd = parts[0];
                String json = parts.length > 1 ? parts[1] : "";

                try {
                    switch (cmd) {
                        case "REGISTER" -> {
                            var req = JsonCodec.fromJson(json, RegisterReq.class);
                            reg.upsertPeer(new PeerRef(req.peerId(), req.host(), req.port()), req.resources());
                            NetUtils.sendLine(out, "OK");
                        }
                        case "LISTDATA_REMOTE" -> {
                            var snap = reg.snapshotAll();
                            NetUtils.sendLine(out, "OK " + JsonCodec.toJson(new ListResp(snap)));
                        }
                        case "LIST_PEERS" -> {
                            var snap = reg.snapshotPeers();
                            NetUtils.sendLine(out, "OK " + JsonCodec.toJson(new PeersResp(snap)));
                        }
                        case "WHO_HAS" -> {
                            var req = JsonCodec.fromJson(json, WhoHasReq.class);
                            var refs = reg.whoHas(req.resource());
                            NetUtils.sendLine(out, "OK " + JsonCodec.toJson(refs));
                        }
                        case "DOWNLOAD_TOKEN_REQ" -> {
                            var req = JsonCodec.fromJson(json, TokenReq.class);
                            var candidates = reg.whoHas(req.resource());
                            if (candidates.isEmpty()) { NetUtils.sendLine(out, "ERR NO_RESOURCE"); break; }
                            var chosen = candidates.getFirst(); // politica: primo
                            var tok = tokens.issue(req.resource(), req.requesterPeerId(), Duration.ofSeconds(60));
                            NetUtils.sendLine(out, "OK " + JsonCodec.toJson(new TokenResp(tok.value(), tok.resource(), chosen)));
                        }
                        case "DOWNLOAD_TOKEN_REL" -> {
                            var rel = JsonCodec.fromJson(json, TokenRel.class);
                            tokens.revoke(rel.token());
                            // consideriamo release come "successo" lato client
                            dlog.addSuccess(rel.resource(), rel.fromPeerId(), rel.requesterPeerId());
                            NetUtils.sendLine(out, "OK");
                        }
                        case "DOWNLOAD_FAILED" -> {
                            var req = JsonCodec.fromJson(json, DownloadFailedReq.class);
                            reg.removePeerResource(req.peerId(), req.resource());
                            dlog.addFailure(req.resource(), req.peerId(), req.requesterPeerId());
                            NetUtils.sendLine(out, "OK");
                        }
                        case "PEER_QUIT" -> {
                            var req = JsonCodec.fromJson(json, PeerQuitReq.class);
                            reg.removePeer(req.peerId());
                            NetUtils.sendLine(out, "OK");
                        }
                        case "QUIT" -> { NetUtils.sendLine(out, "BYE"); return; }
                        default -> NetUtils.sendLine(out, "ERR UNKNOWN_CMD");
                    }
                } catch (Exception e) {
                    NetUtils.sendLine(out, "ERR " + e.getClass().getSimpleName() + ":" + e.getMessage());
                }
            }
        } catch (IOException ignored) {}
    }
}
