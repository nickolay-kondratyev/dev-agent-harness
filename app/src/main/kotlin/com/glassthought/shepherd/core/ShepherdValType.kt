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

    /** Current iteration number in a doer/reviewer loop (0-based). */
    val ITERATION_COUNT = ValTypeV2(
        typeName = "ITERATION_COUNT",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** Maximum iterations allowed in a doer/reviewer loop. */
    val MAX_ITERATIONS = ValTypeV2(
        typeName = "MAX_ITERATIONS",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** Name of a sub-part (doer or reviewer) being spawned or referenced. */
    val SUB_PART_NAME = ValTypeV2(
        typeName = "SUB_PART_NAME",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** Remaining context window percentage reported by an agent. */
    val CONTEXT_WINDOW_REMAINING = ValTypeV2(
        typeName = "CONTEXT_WINDOW_REMAINING",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** HandshakeGuid identifying an agent session in server callbacks. */
    val HANDSHAKE_GUID = ValTypeV2(
        typeName = "HANDSHAKE_GUID",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** Result string from a /signal/done callback (e.g., "completed", "pass"). */
    val RESULT = ValTypeV2(
        typeName = "RESULT",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** Reason string from a /signal/fail-workflow callback. */
    val REASON = ValTypeV2(
        typeName = "REASON",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )

    /** Role of the sub-part (DOER/REVIEWER) for logging context. */
    val ROLE = ValTypeV2(
        typeName = "ROLE",
        userSpecificity = UserSpecificity.USER_AGNOSTIC,
        expectedValClass = String::class,
    )
}
