package com.bitchat.android.security

import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages PIN verification for peer connections.
 * Generates random PINs, tracks verification sessions, and validates PIN entries.
 *
 * This is a singleton that provides thread-safe PIN management across the application.
 */
object PinVerificationManager {
    private const val TAG = "PinVerificationManager"
    private const val PIN_LENGTH = 6
    private const val ALPHANUMERIC_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed confusing chars like I, O, 0, 1

    // Thread-safe map of active PIN sessions
    // Key: sessionId (connectionAddress_timestamp)
    private val activeSessions = ConcurrentHashMap<String, PinSession>()

    // Map peer connections to their session IDs for quick lookup
    // Key: peerID, Value: sessionId
    private val peerToSession = ConcurrentHashMap<String, String>()

    // Secure random generator for cryptographically strong PINs
    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically random 6-character alphanumeric PIN
     * Uses uppercase letters (excluding I, O) and digits (excluding 0, 1) to avoid confusion
     *
     * @return A 6-character PIN code (e.g., "B7K2M9")
     */
    fun generatePin(): String {
        return (1..PIN_LENGTH)
            .map { ALPHANUMERIC_CHARS[secureRandom.nextInt(ALPHANUMERIC_CHARS.length)] }
            .joinToString("")
    }

    /**
     * Creates a new PIN verification session
     *
     * @param connectionAddress Bluetooth MAC address of the connection
     * @param initiatorPeerID Peer ID of the user initiating chat
     * @param responderPeerID Peer ID of the user who must verify
     * @return The created PinSession
     */
    fun createSession(
        connectionAddress: String,
        initiatorPeerID: String,
        responderPeerID: String
    ): PinSession {
        val pin = generatePin()
        val sessionId = generateSessionId(connectionAddress)

        val session = PinSession(
            pin = pin,
            sessionId = sessionId,
            initiatorPeerID = initiatorPeerID,
            responderPeerID = responderPeerID,
            connectionAddress = connectionAddress
        )

        activeSessions[sessionId] = session
        peerToSession[responderPeerID] = sessionId

        Log.d(TAG, "Created PIN session: $sessionId for initiator=$initiatorPeerID, responder=$responderPeerID")

        return session
    }

    /**
     * Validates an entered PIN against the session
     *
     * @param sessionId The session ID to validate against
     * @param enteredPin The PIN entered by the responder
     * @return true if PIN is correct, false otherwise
     */
    fun validatePin(sessionId: String, enteredPin: String): Boolean {
        val session = activeSessions[sessionId] ?: run {
            Log.w(TAG, "Validation attempted for non-existent session: $sessionId")
            return false
        }

        session.attemptCount++

        // Case-insensitive comparison
        val isValid = session.pin.equals(enteredPin, ignoreCase = true)

        if (isValid) {
            session.isVerified = true
            Log.d(TAG, "PIN verified successfully for session: $sessionId")
        } else {
            Log.d(TAG, "Invalid PIN attempt ${session.attemptCount} for session: $sessionId")
        }

        return isValid
    }

    /**
     * Gets the verification status for a specific peer
     *
     * @param peerID The peer to check
     * @return PinVerificationStatus
     */
    fun getVerificationStatus(peerID: String): PinVerificationStatus {
        val sessionId = peerToSession[peerID] ?: return PinVerificationStatus.NOT_REQUIRED
        val session = activeSessions[sessionId] ?: return PinVerificationStatus.NOT_REQUIRED

        return if (session.isVerified) {
            PinVerificationStatus.VERIFIED
        } else if (session.initiatorPeerID == peerID) {
            PinVerificationStatus.PENDING_INITIATOR
        } else {
            PinVerificationStatus.PENDING_RESPONDER
        }
    }

    /**
     * Gets a PIN session by session ID
     *
     * @param sessionId The session ID
     * @return PinSession or null if not found
     */
    fun getSession(sessionId: String): PinSession? {
        return activeSessions[sessionId]
    }

    /**
     * Gets the session for a specific peer
     *
     * @param peerID The peer ID
     * @return PinSession or null if not found
     */
    fun getSessionForPeer(peerID: String): PinSession? {
        val sessionId = peerToSession[peerID] ?: return null
        return activeSessions[sessionId]
    }

    /**
     * Checks if a peer is verified
     *
     * @param peerID The peer to check
     * @return true if verified, false otherwise
     */
    fun isVerified(peerID: String): Boolean {
        val session = getSessionForPeer(peerID) ?: return false
        return session.isVerified
    }

    /**
     * Marks a session as verified (used when receiving verification confirmation)
     *
     * @param sessionId The session to mark as verified
     */
    fun markAsVerified(sessionId: String) {
        activeSessions[sessionId]?.let { session ->
            session.isVerified = true
            Log.d(TAG, "Marked session as verified: $sessionId")
        }
    }

    /**
     * Removes a PIN session (called when connection is closed)
     *
     * @param sessionId The session ID to remove
     */
    fun removeSession(sessionId: String) {
        activeSessions.remove(sessionId)?.let { session ->
            peerToSession.remove(session.responderPeerID)
            peerToSession.remove(session.initiatorPeerID)
            Log.d(TAG, "Removed PIN session: $sessionId")
        }
    }

    /**
     * Removes all sessions for a specific peer (called on disconnect)
     *
     * @param peerID The peer ID
     */
    fun removePeerSessions(peerID: String) {
        val sessionId = peerToSession[peerID]
        if (sessionId != null) {
            removeSession(sessionId)
        }
    }

    /**
     * Removes sessions for a specific connection address
     *
     * @param connectionAddress The Bluetooth MAC address
     */
    fun removeConnectionSessions(connectionAddress: String) {
        val sessionsToRemove = activeSessions.values
            .filter { it.connectionAddress == connectionAddress }
            .map { it.sessionId }

        sessionsToRemove.forEach { removeSession(it) }

        if (sessionsToRemove.isNotEmpty()) {
            Log.d(TAG, "Removed ${sessionsToRemove.size} sessions for connection: $connectionAddress")
        }
    }

    /**
     * Clears all PIN sessions (useful for testing or app reset)
     */
    fun clearAllSessions() {
        activeSessions.clear()
        peerToSession.clear()
        Log.d(TAG, "Cleared all PIN sessions")
    }

    /**
     * Generates a unique session ID based on connection address and timestamp
     *
     * @param connectionAddress The Bluetooth MAC address
     * @return A unique session ID
     */
    private fun generateSessionId(connectionAddress: String): String {
        return "${connectionAddress}_${System.currentTimeMillis()}"
    }

    /**
     * Gets statistics about active sessions (useful for debugging)
     */
    fun getStats(): String {
        return "Active sessions: ${activeSessions.size}, Peer mappings: ${peerToSession.size}"
    }

    /**
     * Checks if a peer needs PIN verification
     * A peer needs verification if they have a session that isn't verified yet
     *
     * @param peerID The peer to check
     * @return true if verification is required
     */
    fun requiresVerification(peerID: String): Boolean {
        val session = getSessionForPeer(peerID) ?: return false
        return !session.isVerified
    }
}
