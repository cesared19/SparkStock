package com.sparkstock.service.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparkstock.service.auth.AdminOnly
import com.sparkstock.service.auth.CurrentUser
import com.sparkstock.service.dto.ErrorResponse
import com.sparkstock.service.models.UserRole
import com.sparkstock.service.services.AuthService
import com.sparkstock.service.services.ConflictException
import com.sparkstock.service.services.ForbiddenException
import com.sparkstock.service.services.NotFoundException
import com.sparkstock.service.sparkytc.SparkyTCUnauthorizedException
import com.sparkstock.service.sparkytc.SparkyTCUnavailableException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.HandlerMethod
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.security.MessageDigest

fun bearerToken(request: HttpServletRequest): String? {
    val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    return header.substring(7).trim().takeIf { it.isNotEmpty() }
}

private fun writeError(objectMapper: ObjectMapper, response: HttpServletResponse, status: HttpStatus, error: String, message: String) {
    response.status = status.value()
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    objectMapper.writeValue(response.outputStream, ErrorResponse(error = error, message = message))
}

/** Every /api/v1 request needs the SparkyTC session token of a signed-in user; @AdminOnly endpoints need an admin. */
@Component
class AuthenticationInterceptor(
    private val authService: AuthService,
    private val objectMapper: ObjectMapper,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val token = bearerToken(request)
        val user = token?.let { authService.authenticate(it) }
        if (token == null || user == null) {
            writeError(objectMapper, response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Your session has expired, sign in again")
            return false
        }
        if (handler is HandlerMethod && handler.hasMethodAnnotation(AdminOnly::class.java) && user.role != UserRole.ADMIN) {
            writeError(objectMapper, response, HttpStatus.FORBIDDEN, "Forbidden", "Only admins can do this")
            return false
        }
        CurrentUser.set(user, token)
        return true
    }

    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        CurrentUser.clear()
    }
}

/** /api/integration/v1 is for SparkyTC: it sends the shared STOCK_INTEGRATION_TOKEN. Off while the token is empty. */
@Component
class IntegrationTokenInterceptor(
    private val properties: SparkStockProperties,
    private val objectMapper: ObjectMapper,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val expected = properties.integration.token
        if (expected.isBlank()) {
            writeError(objectMapper, response, HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", "The integration API is off: set STOCK_INTEGRATION_TOKEN")
            return false
        }
        val given = bearerToken(request)
        if (given == null || !MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())) {
            writeError(objectMapper, response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Send the integration token as Authorization: Bearer <token>")
            return false
        }
        return true
    }
}

@Configuration
class WebConfiguration(
    private val authenticationInterceptor: AuthenticationInterceptor,
    private val integrationTokenInterceptor: IntegrationTokenInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authenticationInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/api/v1/auth/login")
        registry.addInterceptor(integrationTokenInterceptor)
            .addPathPatterns("/api/integration/**")
    }
}

@ControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    private fun error(status: HttpStatus, error: String, message: String?) =
        ResponseEntity.status(status).body(ErrorResponse(error = error, message = message ?: error))

    @ExceptionHandler(IllegalArgumentException::class)
    fun validation(ex: IllegalArgumentException) = error(HttpStatus.BAD_REQUEST, "Validation Error", ex.message)

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun unreadable(ex: Exception) = error(HttpStatus.BAD_REQUEST, "Validation Error", "The request isn't valid JSON, or a value has the wrong type")

    @ExceptionHandler(NotFoundException::class)
    fun notFound(ex: NotFoundException) = error(HttpStatus.NOT_FOUND, "Not Found", ex.message)

    @ExceptionHandler(ConflictException::class)
    fun conflict(ex: ConflictException) = error(HttpStatus.CONFLICT, "Conflict", ex.message)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun stale(ex: OptimisticLockingFailureException) = error(HttpStatus.CONFLICT, "Conflict", "Someone else changed this device at the same time, reload and try again")

    @ExceptionHandler(com.sparkstock.service.services.NotConfiguredException::class)
    fun notConfigured(ex: Exception) = error(HttpStatus.SERVICE_UNAVAILABLE, "Not configured", ex.message)

    @ExceptionHandler(ForbiddenException::class)
    fun forbidden(ex: ForbiddenException) = error(HttpStatus.FORBIDDEN, "Forbidden", ex.message)

    @ExceptionHandler(SparkyTCUnauthorizedException::class)
    fun unauthorized(ex: SparkyTCUnauthorizedException) = error(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.message)

    @ExceptionHandler(SparkyTCUnavailableException::class)
    fun unavailable(ex: SparkyTCUnavailableException): ResponseEntity<ErrorResponse> {
        logger.warn("SparkyTC call failed: {}", ex.message, ex.cause)
        return error(HttpStatus.BAD_GATEWAY, "SparkyTC unavailable", "SparkyTC isn't answering, try again in a moment")
    }
}
