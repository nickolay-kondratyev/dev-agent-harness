package com.glassthought.chainsaw.core.initializer.data

/**
 * Describes the runtime environment in which the application is running.
 *
 * Allows dependency wiring (e.g. [com.glassthought.chainsaw.core.initializer.Initializer])
 * to make environment-aware decisions — for example, substituting real external services
 * with test doubles when [isTest] is `true`.
 *
 * Sealed so that `when` branches on environment type are exhaustive at compile time.
 */
sealed interface Environment {

    /** `true` when running inside a test context; `false` in production. */
    val isTest: Boolean

    companion object {
        /** Returns a [ProductionEnvironment] — the default for production use. */
        fun production(): Environment = ProductionEnvironment()

        /** Returns a [TestEnvironment] — for test contexts. */
        fun test(): Environment = TestEnvironment()
    }
}

/** Production environment: [isTest] is always `false`. */
internal class ProductionEnvironment : Environment {
    override val isTest: Boolean = false
}

/** Test environment: [isTest] is always `true`. */
internal class TestEnvironment : Environment {
    override val isTest: Boolean = true
}
