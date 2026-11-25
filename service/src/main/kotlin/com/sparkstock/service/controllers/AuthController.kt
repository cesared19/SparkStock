package com.sparkstock.service.controllers

import com.sparkstock.service.auth.AdminOnly
import com.sparkstock.service.auth.CurrentUser
import com.sparkstock.service.config.bearerToken
import com.sparkstock.service.dto.DataResponse
import com.sparkstock.service.dto.LoginDTO
import com.sparkstock.service.dto.UserDTO
import com.sparkstock.service.services.AuthService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class AuthController(private val authService: AuthService) {

    /** Signs in with a SparkyTC account. */
    @PostMapping("/auth/login")
    fun login(@RequestBody request: LoginDTO) = DataResponse(data = authService.login(request.username, request.password))

    @PostMapping("/auth/logout")
    fun logout(request: HttpServletRequest): DataResponse<Boolean> {
        bearerToken(request)?.let { authService.logout(it) }
        return DataResponse(data = true)
    }

    @GetMapping("/auth/me")
    fun me() = DataResponse(data = UserDTO.of(CurrentUser.get()))

    /** Every SparkyTC account (refreshed from SparkyTC), e.g. to choose who gets a laptop. */
    @AdminOnly
    @GetMapping("/users")
    fun users() = DataResponse(data = authService.syncAllUsers(CurrentUser.token()).map(UserDTO::of))
}
