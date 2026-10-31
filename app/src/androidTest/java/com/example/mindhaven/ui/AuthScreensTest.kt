package com.example.mindhaven.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.mindhaven.data.session.AuthActions
import com.example.mindhaven.data.session.AuthResult
import com.example.mindhaven.ui.theme.screens.userRegistration.LoginFlowScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.VerifyEmailScreen
import com.example.mindhaven.ui.theme.screens.userRegistration.ChangePasswordScreen
import com.example.mindhaven.viewmodel.AuthFlowViewModel
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule val compose = createComposeRule()
    private class Fake:AuthActions {
        override suspend fun register(email:String,password:String,name:String):AuthResult = AuthResult.Success()
        override suspend fun verifyEmail(email:String,otp:String):AuthResult = AuthResult.Invalid
        override suspend fun resendVerification(email:String):AuthResult = AuthResult.Success()
        override suspend fun login(email:String,password:String):AuthResult = AuthResult.Invalid
        override suspend fun forgotPassword(email:String):AuthResult = AuthResult.Success()
        override suspend fun resetPassword(email:String,otp:String,password:String):AuthResult = AuthResult.Success()
        override suspend fun changePassword(currentPassword:String,newPassword:String):AuthResult = AuthResult.Invalid
        override suspend fun logout():AuthResult = AuthResult.Success()
        override suspend fun logoutAll():AuthResult = AuthResult.Success()
        override suspend fun deleteAccount(password:String):AuthResult = AuthResult.Success()
    }
    @Test fun loginShowsGenericFailure() {
        val vm=AuthFlowViewModel(Fake());compose.setContent { LoginFlowScreen(vm,{}, {}) }
        compose.onNodeWithText("Email").performTextInput("test")
        compose.onNodeWithText("Password").performTextInput("password")
        compose.onNodeWithText("Login").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Invalid email or password.").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun verifyShowsGenericFailure() {
        val vm=AuthFlowViewModel(Fake());vm.setEmail("test");compose.setContent { VerifyEmailScreen(vm) }
        compose.onNodeWithText("Verification code").performTextInput("123456")
        compose.onNodeWithText("Verify").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Invalid or expired code.").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun changePasswordShowsIncorrectCurrentPassword() {
        val vm=AuthFlowViewModel(Fake());compose.setContent { ChangePasswordScreen(vm) }
        compose.onNodeWithText("Current password").performTextInput("old")
        compose.onNodeWithText("New password").performTextInput("abcdefghijkl")
        compose.onNode(hasText("Change password") and hasClickAction()).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Current password is incorrect.").fetchSemanticsNodes().isNotEmpty() }
    }
}
