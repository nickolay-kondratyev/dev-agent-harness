package com.asgard.core.exception.base

import com.asgard.AsgardEnvironment
import com.asgard.core.data.userMessage.UserMessage
import com.asgard.core.data.value.MessageWithValues
import com.asgard.core.data.value.Val
import com.asgard.core.exception.ExceptionMessageUtil
import com.asgard.core.exception.surface.AsgardTimeoutException
import com.asgard.core.out.LogLevel
import com.asgard.core.util.getStackTraceAsString

/** Base exception other exceptions should derive from it.
 *
 *  Important note: when adding values, do not embed the values
 *  into the message of exception but rather pass them individually
 *  with usage of [[com.asgard.core.data.Val]] so that we can deal with
 *  sensitive values appropriately.
 *
 *  NOTE: [AsgardTimeoutException] is NOT derived from this class, as it must
 *  be derived from [kotlinx.coroutines.CancellationException] to properly represent
 *  a timeout in coroutines (as timout exceptions are cancellation exceptions).
 *  And [CancellationException](http://www.glassthought.com/notes/0q84qdazu9qam5shlrthro5)
 *  special in kotlin co-routines.
 *  */
open class AsgardBaseException : RuntimeException {
  val values: List<Val>

  /**
   * Controls whether stack traces should be hidden/omited from logging.
   * Override in subclasses for exceptions where stack traces are noise
   * (e.g., validation errors, expected business logic failures).
   *
   * Defaults to false - stack traces are shown by default.
   */
  open val shouldOmitStackTraceFromLogging: Boolean = false

  /**
   * ### Set to DATA_ERROR when its data error related exception.
   * Set this when the exception is related to input data errors,
   * this is meant to be used to make it easier to set the log level to
   * [com.asgard.core.out.LogLevel.DATA_ERROR] instead of ERROR.
   *
   * NOTE: when setting this value to true you likely want to set the
   * [shouldOmitStackTraceFromLogging] to true as well.*/
  open val logLevel: LogLevel = LogLevel.ERROR

  constructor(messageWithValues: MessageWithValues) :
    this(messageWithValues.message, *messageWithValues.values.toTypedArray())

  constructor(messageWithValues: MessageWithValues, cause: Throwable) :
    this(messageWithValues.message, cause, *messageWithValues.values.toTypedArray())

  constructor(message: String, vararg values: Val) :
    this(message, null, *values)

  constructor(cause: Throwable) :
    this(cause.message ?: "", cause, *emptyArray<Val>())

  constructor(message: String, cause: Throwable?, vararg values: Val) :
    super(message, cause) {

    this.values = values.toList()
  }

  override fun toString(): String {
    val kClass = this::class
    val stringBuilder = StringBuilder()
    stringBuilder.append("exc=$kClass")

    // Format message with values for debugging
    val formattedMessage = ExceptionMessageUtil.formatMessageAddingValuesInNonRelease(message ?: "", values.toTypedArray())
    stringBuilder.append(" message=$formattedMessage")

    if (AsgardEnvironment.buildType == AsgardEnvironment.BuildType.TEST_BUILD) {
      stringBuilder.append(" values=$values")
    }

    if (cause != null) {
      stringBuilder.append(" cause.stackTrace=${getStackTraceAsString(cause!!)}")
    }

    return stringBuilder.toString()
  }
}

fun AsgardBaseException.toUserMessage(): UserMessage =
  UserMessage(
    this.message ?: "",
    this.values,
  )

fun Throwable.toUserMessage(): UserMessage {
  if (this is AsgardBaseException) {
    return this.toUserMessage()
  }

  return UserMessage(
    this.message ?: "",
    emptyList(),
  )
}

public fun asgardError(
  message: String,
  values: () -> List<Val>,
): Nothing = throw AsgardBaseException(message, *values().toTypedArray())

fun Throwable.isDataError(): Boolean = (this is AsgardBaseException) && this.logLevel == LogLevel.DATA_ERROR

fun Throwable.getLogLevel(): LogLevel =
  if (this is AsgardBaseException) {
    this.logLevel
  } else {
    LogLevel.ERROR
  }
