package cn.genlite.domain;

import java.util.UUID;

/** Transient visual receipt; published only after the enclosing transfer is durable. */
public record CollectorTransfer(UUID generator, UUID collector, Position from, Position to, int amount) {}
