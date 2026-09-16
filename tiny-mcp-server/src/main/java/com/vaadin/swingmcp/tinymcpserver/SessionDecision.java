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
 *
 * <p>Immutable.
 *
 * @implNote the private constructor is what seals the hierarchy to the three
 * nested subclasses — only they can reach it. {@code sealed}/{@code permits}
 * would say so declaratively, but this module compiles at Java 11.
 */
public abstract class SessionDecision {

    private SessionDecision() {
    }

    /** Refuse the new session; {@code initialize} fails with HTTP 409. */
    public static final class Reject extends SessionDecision {
        public Reject() {
        }
    }

    /** Accept the new session without evicting any existing session. */
    public static final class Accept extends SessionDecision {
        public Accept() {
        }
    }

    /**
     * Accept the new session and evict the listed sessions. The list is
     * defensively copied; an empty list is permitted and behaves like
     * {@link Accept}. The {@code evictionReason} is written to the
     * tombstone for each evicted session and surfaces verbatim in the
     * 404 error the displaced client receives on its next call — so it
     * should describe, in the calling application's terms, why the old
     * session was closed and what the user should do about it.
     */
    public static final class AcceptAndEvict extends SessionDecision {

        private final List<MCPSession> sessions;
        private final String evictionReason;

        /**
         * @param evictionReason not blank; surfaces verbatim to the displaced client
         * @throws IllegalArgumentException if {@code evictionReason} is blank
         */
        public AcceptAndEvict(List<MCPSession> sessions, String evictionReason) {
            Objects.requireNonNull(evictionReason, "evictionReason");
            if (evictionReason.isBlank()) {
                throw new IllegalArgumentException("evictionReason must not be blank");
            }
            this.sessions = List.copyOf(sessions);
            this.evictionReason = evictionReason;
        }

        public List<MCPSession> sessions() {
            return sessions;
        }

        public String evictionReason() {
            return evictionReason;
        }
    }
}
