package it.unibo.labso.p2p.master;

import it.unibo.labso.p2p.common.Token;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class TokenStore {
    private final Map<String, Token> byValue = new ConcurrentHashMap<>();

    Token issue(String resource, String toPeerId, Duration ttl) {
        Token t = new Token(
                UUID.randomUUID().toString(),
                resource,
                toPeerId,
                Instant.now().plus(ttl)
        );
        byValue.put(t.value(), t);
        return t;
    }

    Optional<Token> get(String value) {
        Token t = byValue.get(value);
        if (t == null) return Optional.empty();
        if (Instant.now().isAfter(t.expiresAt())) {
            byValue.remove(value);
            return Optional.empty();
        }
        return Optional.of(t);
    }

    void revoke(String value) {
        byValue.remove(value);
    }
}
