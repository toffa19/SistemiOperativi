package it.unibo.labso.p2p.common;

import java.time.Instant;

public record Token(String value, String resource, String grantedToPeerId, Instant expiresAt) {}
