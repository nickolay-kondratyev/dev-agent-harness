package com.glassthought.shepherd.core

import com.asgard.core.data.value.UserSpecificity
import com.asgard.core.data.value.ValTypeV2

/**
 * Project-specific [ValTypeV2] definitions for TICKET_SHEPHERD structured logging.
 *
 * Extends asgard's [ValType] enum with semantically specific types for shepherd domain values.
 * Per CLAUDE.md: "ValType must be semantically specific to the value being logged."
 */
object ShepherdValType {

    /** Payload identifier (handshakeGuid + counter) used in the ACK protocol. */
    val PAYLOAD_ID = ValTypeV2(
        typeName = "PAYLOAD_ID",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** Current attempt number in a retry loop (1-based). */
    val ATTEMPT_NUMBER = ValTypeV2(
        typeName = "ATTEMPT_NUMBER",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** Maximum number of attempts allowed in a retry policy. */
    val MAX_ATTEMPTS = ValTypeV2(
        typeName = "MAX_ATTEMPTS",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )
}
