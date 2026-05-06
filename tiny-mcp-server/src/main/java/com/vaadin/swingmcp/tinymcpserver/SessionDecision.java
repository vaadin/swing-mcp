package com.vaadin.swingmcp.tinymcpserver;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of {@link MCPHandler#setAcceptNewSession}'s policy callback.
 * The callback receives a snapshot of currently active sessions and
 * returns one of:
 * <ul>
 *   <li>{@link Reject} — refuse the new session ({@code initialize} fails
 *       with HTTP 409).</li>
 *   <li>{@link Accept} — accept without evicting any existing session
 *       (only valid if the cap permits it; otherwise the policy should
 *       use {@link AcceptAndEvict} or {@link Reject}).</li>
 *   <li>{@link AcceptAndEvict} — accept the new session and evict the
 *       listed existing sessions. Eviction inserts a tombstone (carrying
 *       the decision's {@code evictionReason}) for each evicted id, so
 *       the displaced client gets a clean error on its next call, and
 *       runs {@code onSessionClosed}. An empty list is a no-op
 *       equivalent to {@link Accept}.</li>
 * </ul>
 *
 * <p>Eviction always happens <em>outside</em> the handler's session-guard
 * lock and blocks until any in-flight request on the evicted session has
 * completed, so {@code onSessionClosed} listeners observe the session
 * after it is fully quiesced.
 */
public sealed interface SessionDecision
        permits SessionDecision.Reject, SessionDecision.Accept, SessionDecision.AcceptAndEvict {

    /** Refuse the new session; {@code initialize} fails with HTTP 409. */
    record Reject() implements SessionDecision {}

    /** Accept the new session without evicting any existing session. */
    record Accept() implements SessionDecision {}

    /**
     * Accept the new session and evict the listed sessions. The list is
     * defensively copied; an empty list is permitted and behaves like
     * {@link Accept}. The {@code evictionReason} is written to the
     * tombstone for each evicted session and surfaces verbatim in the
     * 404 error the displaced client receives on its next call — so it
     * should describe, in the calling application's terms, why the old
     * session was closed and what the user should do about it.
     */
    record AcceptAndEvict(List<MCPSession> sessions, String evictionReason) implements SessionDecision {
        public AcceptAndEvict {
            sessions = List.copyOf(sessions);
            Objects.requireNonNull(evictionReason, "evictionReason");
            if (evictionReason.isBlank()) {
                throw new IllegalArgumentException("evictionReason must not be blank");
            }
        }
    }
}
