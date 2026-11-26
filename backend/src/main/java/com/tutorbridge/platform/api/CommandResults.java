package com.tutorbridge.platform.api;

import java.util.Set;
import java.util.UUID;

/**
 * Answers, for the command status lookup (TX16, TB-22), whether the actor of a receipt may still see the
 * result it names (INV-22): a receipt never skips a permission taken away since. A module declares one bean
 * for the result types its commands record with {@link CommandReceipts#succeed}; platform cannot read the
 * tables behind them.
 *
 * <p>A result type that no module declares stays hidden (Huy, 24/11/2026): the status then has no
 * {@code resultType} and no {@code resultId}, as when the actor lost access.
 */
public interface CommandResults {

    /** The {@code command_receipts.result_type} values this module answers for. One module per type. */
    Set<String> resultTypes();

    /** Whether the actor may see this result now. The result may be gone (INV-26): then false. */
    boolean visibleTo(Actor actor, String resultType, UUID resultId);
}
