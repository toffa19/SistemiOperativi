package it.unibo.labso.p2p.master;

import it.unibo.labso.p2p.common.PeerRef;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registro risorse↔peer con mutua esclusione “stretta”.
 * Tutte le operazioni (letture e scritture) passano da un unico lock,
 * così durante gli aggiornamenti nessuna lettura corre in parallelo.
 */
final class Registry {

    // risorsa -> set di peerId che la possiedono
    private final Map<String, Set<String>> resourceToPeers = new HashMap<>();
    // peerId -> riferimento del peer (host, port)
    private final Map<String, PeerRef> peers = new HashMap<>();
    // peerId -> set di risorse possedute
    private final Map<String, Set<String>> peerToResources = new HashMap<>();

    // lock "rigido" per rispettare la lettura più conservativa della specifica
    private final Lock lock = new ReentrantLock(true); // fairness opzionale

    /**
     * Inserisce/aggiorna un peer e le sue risorse (sostituzione atomica).
     */
    void upsertPeer(PeerRef ref, Collection<String> resources) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(resources, "resources");

        lock.lock();
        try {
            // registra/aggiorna anagrafica peer
            peers.put(ref.id(), ref);

            // rimuovi vecchie associazioni risorsa->peer
            Set<String> old = peerToResources.getOrDefault(ref.id(), Collections.emptySet());
            for (String r : old) {
                Set<String> ids = resourceToPeers.get(r);
                if (ids != null) {
                    ids.remove(ref.id());
                    if (ids.isEmpty()) resourceToPeers.remove(r);
                }
            }

            // aggiungi nuove associazioni
            Set<String> newSet = new HashSet<>(resources);
            peerToResources.put(ref.id(), newSet);
            for (String r : newSet) {
                resourceToPeers.computeIfAbsent(r, k -> new HashSet<>()).add(ref.id());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ritorna snapshot completo risorsa -> lista di peerId.
     * (Restituisce copie per evitare aliasing dall'esterno.)
     */
    Map<String, List<String>> snapshotAll() {
        lock.lock();
        try {
            Map<String, List<String>> copy = new HashMap<>();
            for (var e : resourceToPeers.entrySet()) {
                copy.put(e.getKey(), List.copyOf(e.getValue()));
            }
            return copy;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Elenca i PeerRef che hanno la risorsa richiesta, nell'ordine naturale di inserimento.
     */
    List<PeerRef> whoHas(String resource) {
        Objects.requireNonNull(resource, "resource");
        lock.lock();
        try {
            Set<String> ids = resourceToPeers.getOrDefault(resource, Collections.emptySet());
            List<PeerRef> out = new ArrayList<>(ids.size());
            for (String id : ids) {
                PeerRef ref = peers.get(id);
                if (ref != null) out.add(ref);
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Rimuove completamente un peer (es. quit/disconnessione) e tutte le sue risorse.
     */
    void removePeer(String peerId) {
        Objects.requireNonNull(peerId, "peerId");
        lock.lock();
        try {
            Set<String> owned = peerToResources.remove(peerId);
            if (owned != null) {
                for (String r : owned) {
                    Set<String> ids = resourceToPeers.get(r);
                    if (ids != null) {
                        ids.remove(peerId);
                        if (ids.isEmpty()) resourceToPeers.remove(r);
                    }
                }
            }
            peers.remove(peerId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Snapshot di tutti i peer conosciuti: peerId -> PeerRef.
     */
    Map<String, PeerRef> snapshotPeers() {
        lock.lock();
        try {
            Map<String, PeerRef> out = new HashMap<>();
            for (var e : peers.entrySet()) out.put(e.getKey(), e.getValue());
            return out;
        } finally { lock.unlock(); }
    }


    /**
     * Rimuove l'associazione (peerId, resource), utile quando un download fallisce
     * e il master deve “sfidare” la tabella.
     */
    void removePeerResource(String peerId, String resource) {
        Objects.requireNonNull(peerId, "peerId");
        Objects.requireNonNull(resource, "resource");
        lock.lock();
        try {
            Set<String> res = peerToResources.get(peerId);
            if (res != null) {
                res.remove(resource);
                if (res.isEmpty()) peerToResources.remove(peerId);
            }
            Set<String> ids = resourceToPeers.get(resource);
            if (ids != null) {
                ids.remove(peerId);
                if (ids.isEmpty()) resourceToPeers.remove(resource);
            }
        } finally {
            lock.unlock();
        }
    }
}
