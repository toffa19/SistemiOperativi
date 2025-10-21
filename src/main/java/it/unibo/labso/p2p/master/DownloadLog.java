package it.unibo.labso.p2p.master;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DownloadLog {
    static final class Entry {
        final Instant ts = Instant.now();
        final String resource;
        final String fromPeerId;
        final String toPeerId;
        final boolean ok;
        Entry(String resource, String fromPeerId, String toPeerId, boolean ok) {
            this.resource = resource; this.fromPeerId = fromPeerId; this.toPeerId = toPeerId; this.ok = ok;
        }
        @Override public String toString() {
            return ts + " " + resource + " da:" + fromPeerId + " a:" + toPeerId + " esito:" + (ok?"OK":"FAIL");
        }
    }

    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

    void addSuccess(String resource, String fromPeerId, String toPeerId) {
        entries.add(new Entry(resource, fromPeerId, toPeerId, true));
    }
    void addFailure(String resource, String fromPeerId, String toPeerId) {
        entries.add(new Entry(resource, fromPeerId, toPeerId, false));
    }
    List<Entry> snapshot() { return List.copyOf(entries); }
}
