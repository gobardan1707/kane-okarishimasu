package com.bitchat.android.security

import java.util.Date

/**
 * Represents the verification status of a peer connection
 */
enum class PinVerificationStatus {
    /** No verification required (feature not applicable or disabled) */
    NOT_REQUIRED,

    /** Verification initiated, waiting for responder to enter PIN */
    PENDING_INITIATOR,

    /** Received verification request, responder needs to enter PIN */
    PENDING_RESPONDER,

    /** PIN successfully verified, chat enabled */
    VERIFIED,

    /** Verification blocked or failed (currently not used, but reserved for future) */
    BLOCKED
}

/**
 * Represents a PIN verification session for a specific connection
 *
 * @property pin The 6-character alphanumeric PIN code
 * @property sessionId Unique identifier for this connection session (MAC address + timestamp)
 * @property initiatorPeerID The peer ID of the user who initiated the chat
 * @property responderPeerID The peer ID of the user who must enter the PIN
 * @property connectionAddress Bluetooth MAC address of the connection
 * @property timestamp When this PIN session was created
 * @property isVerified Whether the PIN has been successfully verified
 * @property attemptCount Number of PIN entry attempts (for future rate limiting if needed)
 */
data class PinSession(
    val pin: String,
    val sessionId: String,
    val initiatorPeerID: String,
    val responderPeerID: String,
    val connectionAddress: String,
    val timestamp: Date = Date(),
    var isVerified: Boolean = false,
    var attemptCount: Int = 0
)

/**
 * Represents the current PIN verification state for the UI
 *
 * @property status Current verification status
 * @property pin The PIN code (only populated for initiator)
 * @property peerID The peer being verified
 * @property sessionId The session ID for this verification (from the request)
 * @property errorMessage Optional error message if verification failed
 */
data class PinVerificationUIState(
    val status: PinVerificationStatus = PinVerificationStatus.NOT_REQUIRED,
    val pin: String? = null,
    val peerID: String? = null,
    val sessionId: String? = null,
    val errorMessage: String? = null
)

/**
 * Request sent by initiator to responder to start PIN verification
 */
data class PinVerificationRequest(
    val sessionId: String,
    val initiatorPeerID: String,
    val timestamp: Long
) {
    /**
     * TLV types for PIN verification request
     */
    private enum class TLVType(val value: UByte) {
        SESSION_ID(0x01u),
        INITIATOR_PEER_ID(0x02u),
        TIMESTAMP(0x03u);

        companion object {
            fun fromValue(value: UByte): TLVType? {
                return values().find { it.value == value }
            }
        }
    }

    /**
     * Encode to TLV binary data
     */
    fun encode(): ByteArray {
        val sessionIdData = sessionId.toByteArray(Charsets.UTF_8)
        val initiatorPeerIDData = initiatorPeerID.toByteArray(Charsets.UTF_8)
        val timestampData = java.nio.ByteBuffer.allocate(8).putLong(timestamp).array()

        val result = mutableListOf<Byte>()

        // TLV for session ID
        result.add(TLVType.SESSION_ID.value.toByte())
        result.add(sessionIdData.size.toByte())
        result.addAll(sessionIdData.toList())

        // TLV for initiator peer ID
        result.add(TLVType.INITIATOR_PEER_ID.value.toByte())
        result.add(initiatorPeerIDData.size.toByte())
        result.addAll(initiatorPeerIDData.toList())

        // TLV for timestamp
        result.add(TLVType.TIMESTAMP.value.toByte())
        result.add(timestampData.size.toByte())
        result.addAll(timestampData.toList())

        return result.toByteArray()
    }

    companion object {
        /**
         * Decode from TLV binary data
         */
        fun decode(data: ByteArray): PinVerificationRequest? {
            var offset = 0
            var sessionId: String? = null
            var initiatorPeerID: String? = null
            var timestamp: Long? = null

            while (offset + 2 <= data.size) {
                val typeValue = data[offset].toUByte()
                val type = TLVType.fromValue(typeValue)
                offset += 1

                val length = data[offset].toUByte().toInt()
                offset += 1

                if (offset + length > data.size) return null

                val value = data.sliceArray(offset until offset + length)
                offset += length

                when (type) {
                    TLVType.SESSION_ID -> sessionId = String(value, Charsets.UTF_8)
                    TLVType.INITIATOR_PEER_ID -> initiatorPeerID = String(value, Charsets.UTF_8)
                    TLVType.TIMESTAMP -> {
                        if (value.size == 8) {
                            timestamp = java.nio.ByteBuffer.wrap(value).getLong()
                        }
                    }
                    null -> continue
                }
            }

            return if (sessionId != null && initiatorPeerID != null && timestamp != null) {
                PinVerificationRequest(sessionId, initiatorPeerID, timestamp)
            } else {
                null
            }
        }
    }
}

/**
 * Response sent by responder back to initiator with entered PIN
 */
data class PinVerificationResponse(
    val sessionId: String,
    val enteredPin: String,
    val responderPeerID: String
) {
    /**
     * TLV types for PIN verification response
     */
    private enum class TLVType(val value: UByte) {
        SESSION_ID(0x01u),
        ENTERED_PIN(0x02u),
        RESPONDER_PEER_ID(0x03u);

        companion object {
            fun fromValue(value: UByte): TLVType? {
                return values().find { it.value == value }
            }
        }
    }

    /**
     * Encode to TLV binary data
     */
    fun encode(): ByteArray {
        val sessionIdData = sessionId.toByteArray(Charsets.UTF_8)
        val enteredPinData = enteredPin.toByteArray(Charsets.UTF_8)
        val responderPeerIDData = responderPeerID.toByteArray(Charsets.UTF_8)

        val result = mutableListOf<Byte>()

        // TLV for session ID
        result.add(TLVType.SESSION_ID.value.toByte())
        result.add(sessionIdData.size.toByte())
        result.addAll(sessionIdData.toList())

        // TLV for entered PIN
        result.add(TLVType.ENTERED_PIN.value.toByte())
        result.add(enteredPinData.size.toByte())
        result.addAll(enteredPinData.toList())

        // TLV for responder peer ID
        result.add(TLVType.RESPONDER_PEER_ID.value.toByte())
        result.add(responderPeerIDData.size.toByte())
        result.addAll(responderPeerIDData.toList())

        return result.toByteArray()
    }

    companion object {
        /**
         * Decode from TLV binary data
         */
        fun decode(data: ByteArray): PinVerificationResponse? {
            var offset = 0
            var sessionId: String? = null
            var enteredPin: String? = null
            var responderPeerID: String? = null

            while (offset + 2 <= data.size) {
                val typeValue = data[offset].toUByte()
                val type = TLVType.fromValue(typeValue)
                offset += 1

                val length = data[offset].toUByte().toInt()
                offset += 1

                if (offset + length > data.size) return null

                val value = data.sliceArray(offset until offset + length)
                offset += length

                when (type) {
                    TLVType.SESSION_ID -> sessionId = String(value, Charsets.UTF_8)
                    TLVType.ENTERED_PIN -> enteredPin = String(value, Charsets.UTF_8)
                    TLVType.RESPONDER_PEER_ID -> responderPeerID = String(value, Charsets.UTF_8)
                    null -> continue
                }
            }

            return if (sessionId != null && enteredPin != null && responderPeerID != null) {
                PinVerificationResponse(sessionId, enteredPin, responderPeerID)
            } else {
                null
            }
        }
    }
}

/**
 * Result of PIN verification sent back to responder
 */
data class PinVerificationResult(
    val sessionId: String,
    val success: Boolean,
    val errorMessage: String? = null
) {
    /**
     * TLV types for PIN verification result
     */
    private enum class TLVType(val value: UByte) {
        SESSION_ID(0x01u),
        SUCCESS(0x02u),
        ERROR_MESSAGE(0x03u);

        companion object {
            fun fromValue(value: UByte): TLVType? {
                return values().find { it.value == value }
            }
        }
    }

    /**
     * Encode to TLV binary data
     */
    fun encode(): ByteArray {
        val sessionIdData = sessionId.toByteArray(Charsets.UTF_8)
        val successData = byteArrayOf(if (success) 1 else 0)

        val result = mutableListOf<Byte>()

        // TLV for session ID
        result.add(TLVType.SESSION_ID.value.toByte())
        result.add(sessionIdData.size.toByte())
        result.addAll(sessionIdData.toList())

        // TLV for success
        result.add(TLVType.SUCCESS.value.toByte())
        result.add(successData.size.toByte())
        result.addAll(successData.toList())

        // TLV for error message (optional)
        errorMessage?.let {
            val errorMessageData = it.toByteArray(Charsets.UTF_8)
            result.add(TLVType.ERROR_MESSAGE.value.toByte())
            result.add(errorMessageData.size.toByte())
            result.addAll(errorMessageData.toList())
        }

        return result.toByteArray()
    }

    companion object {
        /**
         * Decode from TLV binary data
         */
        fun decode(data: ByteArray): PinVerificationResult? {
            var offset = 0
            var sessionId: String? = null
            var success: Boolean? = null
            var errorMessage: String? = null

            while (offset + 2 <= data.size) {
                val typeValue = data[offset].toUByte()
                val type = TLVType.fromValue(typeValue)
                offset += 1

                val length = data[offset].toUByte().toInt()
                offset += 1

                if (offset + length > data.size) return null

                val value = data.sliceArray(offset until offset + length)
                offset += length

                when (type) {
                    TLVType.SESSION_ID -> sessionId = String(value, Charsets.UTF_8)
                    TLVType.SUCCESS -> {
                        if (value.isNotEmpty()) {
                            success = value[0] != 0.toByte()
                        }
                    }
                    TLVType.ERROR_MESSAGE -> errorMessage = String(value, Charsets.UTF_8)
                    null -> continue
                }
            }

            return if (sessionId != null && success != null) {
                PinVerificationResult(sessionId, success, errorMessage)
            } else {
                null
            }
        }
    }
}
