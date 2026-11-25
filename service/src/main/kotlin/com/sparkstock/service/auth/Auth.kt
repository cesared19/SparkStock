package com.sparkstock.service.auth

import com.sparkstock.service.models.User

/** Restricts an endpoint to SparkyTC admins. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AdminOnly

/** Holds the user of the current request, set by the AuthenticationInterceptor. */
object CurrentUser {
    private val user = ThreadLocal<User?>()
    private val token = ThreadLocal<String?>()

    fun get(): User = user.get() ?: throw IllegalStateException("No signed-in user")

    /** The SparkyTC session token of the current request, to call SparkyTC as this user. */
    fun token(): String = token.get() ?: throw IllegalStateException("No signed-in user")

    fun set(value: User, sessionToken: String) {
        user.set(value)
        token.set(sessionToken)
    }

    fun clear() {
        user.remove()
        token.remove()
    }
}
