package com.sparkstock.service.services

import com.sparkstock.service.config.SparkStockProperties
import com.sparkstock.service.dto.LoginResponseDTO
import com.sparkstock.service.dto.UserDTO
import com.sparkstock.service.models.User
import com.sparkstock.service.repositories.UserRepository
import com.sparkstock.service.sparkytc.SparkyTCClient
import com.sparkstock.service.sparkytc.SparkyTCUnauthorizedException
import com.sparkstock.service.sparkytc.SparkyTCUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sign-in goes through SparkyTC: SparkStock forwards the credentials to SparkyTC's login once and keeps the
 * SparkyTC session token it gets back. Each request is checked against SparkyTC again once the cached check is
 * older than `sparkstock.sparkytc.session-cache-seconds`, so a deactivated or demoted account stops working here too.
 */
@Service
class AuthService(
    private val sparkyTC: SparkyTCClient,
    private val userRepository: UserRepository,
    private val properties: SparkStockProperties,
) {
    private data class CachedSession(val userId: UUID, val checkedAt: Instant)

    // keyed by a hash of the token, so the tokens themselves aren't kept around in memory dumps longer than needed
    private val sessions = ConcurrentHashMap<String, CachedSession>()

    @Transactional
    fun login(username: String, password: String): LoginResponseDTO {
        require(username.isNotBlank() && password.isNotEmpty()) { "Enter your SparkyTC username and password" }
        val login = sparkyTC.login(username.trim(), password)
        if (!login.user.isActive) throw SparkyTCUnauthorizedException("This account is disabled")
        val user = sync(login.user)
        sessions[hash(login.token)] = CachedSession(user.id, Instant.now())
        return LoginResponseDTO(token = login.token, expiresAt = login.expiresAt.toInstant(), user = UserDTO.of(user))
    }

    @Transactional
    fun authenticate(token: String): User? {
        val key = hash(token)
        val cached = sessions[key]
        val maxAge = properties.sparkytc.sessionCacheSeconds
        if (cached != null && cached.checkedAt.isAfter(Instant.now().minusSeconds(maxAge))) {
            val user = userRepository.findById(cached.userId).orElse(null)
            if (user != null && user.isActive) return user
        }
        val remote = try {
            sparkyTC.me(token)
        } catch (_: SparkyTCUnauthorizedException) {
            sessions.remove(key)
            return null
        }
        val user = sync(remote)
        if (!user.isActive) {
            sessions.remove(key)
            return null
        }
        sessions[key] = CachedSession(user.id, Instant.now())
        return user
    }

    fun logout(token: String) {
        sessions.remove(hash(token))
        sparkyTC.logout(token)
    }

    /** Refreshes the local copy of every SparkyTC account (admins only, it uses the admin's session). */
    @Transactional
    fun syncAllUsers(adminToken: String): List<User> {
        val remote = sparkyTC.users(adminToken)
        remote.forEach { sync(it) }
        val remoteIds = remote.map { it.userID }.toSet()
        // accounts deleted in SparkyTC can't sign in any more
        userRepository.findAll().filter { it.id !in remoteIds && it.isActive }.forEach { it.isActive = false }
        return userRepository.findAllByOrderByDisplayNameAsc()
    }

    @Transactional
    fun sync(remote: SparkyTCUser): User {
        val user = userRepository.findById(remote.userID).orElse(null)
            ?: User(id = remote.userID, username = remote.username, displayName = remote.displayName, role = remote.role)
        user.username = remote.username
        user.displayName = remote.displayName
        user.role = remote.role
        user.isActive = remote.isActive
        user.syncedAt = Instant.now()
        return userRepository.save(user)
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
