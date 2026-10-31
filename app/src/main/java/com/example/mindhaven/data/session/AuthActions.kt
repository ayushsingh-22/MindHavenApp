package com.example.mindhaven.data.session

interface AuthActions {
    suspend fun register(email: String, password: String, name: String): AuthResult
    suspend fun verifyEmail(email: String, otp: String): AuthResult
    suspend fun resendVerification(email: String): AuthResult
    suspend fun login(email: String, password: String): AuthResult
    suspend fun forgotPassword(email: String): AuthResult
    suspend fun resetPassword(email: String, otp: String, password: String): AuthResult
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult
    suspend fun logout(): AuthResult
    suspend fun logoutAll(): AuthResult
    suspend fun deleteAccount(password: String): AuthResult
}
