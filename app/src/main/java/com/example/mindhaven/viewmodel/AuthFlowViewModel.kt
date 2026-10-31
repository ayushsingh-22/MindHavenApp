package com.example.mindhaven.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindhaven.data.session.AuthActions
import com.example.mindhaven.data.session.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthFlowUi(val busy: Boolean = false, val message: String? = null, val destination: String? = null, val cooldown: Int = 0)

@HiltViewModel
class AuthFlowViewModel @Inject constructor(private val auth: AuthActions) : ViewModel() {
    private val mutable = MutableStateFlow(AuthFlowUi())
    val ui: StateFlow<AuthFlowUi> = mutable
    var email: String = ""
        private set
    private var cooldownJob: Job? = null
    fun clearNavigation() { mutable.value = mutable.value.copy(destination = null) }
    fun setEmail(value: String) { email = value.trim() }
    fun resetMessage() { mutable.value = mutable.value.copy(message = null) }
    fun showMessage(message: String) { fail(message) }
    private fun passwordOk(value: String) = value.codePointCount(0,value.length) >= 12 && value.toByteArray(Charsets.UTF_8).size <= 72
    private fun error(result: AuthResult, invalid: String) = when(result) {
        is AuthResult.ServiceBusy -> "Service busy. Please try again."
        AuthResult.Offline -> "Unable to connect. Please check your connection and try again."
        else -> invalid
    }
    private fun execute(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        viewModelScope.launch { mutable.value = mutable.value.copy(busy=true,message=null); try { action() } finally { mutable.value = mutable.value.copy(busy=false) } }
    }
    private fun navigate(route: String, message: String? = null) { mutable.value = mutable.value.copy(destination=route,message=message) }
    private fun fail(message: String) { mutable.value = mutable.value.copy(message=message) }
    private fun cooldown() {
        cooldownJob?.cancel(); mutable.value = mutable.value.copy(cooldown=60)
        cooldownJob=viewModelScope.launch { while(mutable.value.cooldown > 0) { delay(1000); mutable.value=mutable.value.copy(cooldown=mutable.value.cooldown-1) } }
    }
    fun register(name: String, address: String, password: String) {
        if(name.isBlank() || address.isBlank() || !passwordOk(password)) { fail("Enter your name and email. Passwords need at least 12 characters and at most 72 UTF-8 bytes."); return }
        setEmail(address); execute { when(val r=auth.register(email,password,name.trim())) { is AuthResult.Success -> { cooldown(); navigate("verifyEmail","If eligible, a verification code will be sent.") }; else -> fail(error(r,"Unable to process the request.")) } }
    }
    fun login(address: String, password: String) {
        if(address.isBlank() || password.isEmpty()) { fail("Enter your email and password.");return };setEmail(address)
        execute { when(val r=auth.login(email,password)) { is AuthResult.Success -> Unit; AuthResult.NeedsVerification -> navigate("verifyEmail","Please verify your email."); else -> fail(error(r,"Invalid email or password.")) } }
    }
    fun verify(otp: String) { if(!Regex("^[0-9]{6}$").matches(otp)) { fail("Enter the six-digit code.");return };execute { when(val r=auth.verifyEmail(email,otp)) { is AuthResult.Success -> navigate("emailLogin","Email verified. Please sign in."); else -> fail(error(r,"Invalid or expired code.")) } } }
    fun resend() { if(mutable.value.cooldown > 0)return; execute { when(val r=auth.resendVerification(email)) { is AuthResult.Success -> { cooldown();fail("If eligible, a verification code will be sent.") };else -> fail(error(r,"Unable to process the request.")) } } }
    fun forgot(address: String) { if(address.isBlank()) { fail("Enter your email.");return };setEmail(address);execute { when(val r=auth.forgotPassword(email)) { is AuthResult.Success -> navigate("resetPassword","If eligible, a reset code will be sent.");else -> fail(error(r,"Unable to process the request.")) } } }
    fun reset(otp: String, password: String) { if(!Regex("^[0-9]{6}$").matches(otp) || !passwordOk(password)) { fail("Enter the six-digit code and a password of at least 12 characters and at most 72 UTF-8 bytes.");return };execute { when(val r=auth.resetPassword(email,otp,password)) { is AuthResult.Success -> navigate("emailLogin","Password reset. Please sign in.");else -> fail(error(r,"Invalid or expired code.")) } } }
    fun change(current: String, password: String) { if(current.isEmpty() || !passwordOk(password)) { fail("Enter your current password and a new password of at least 12 characters and at most 72 UTF-8 bytes.");return };execute { when(val r=auth.changePassword(current,password)) { is AuthResult.Success -> navigate("emailLogin","Password changed. Please sign in again.");else -> fail(error(r,"Current password is incorrect.")) } } }
    fun logoutAll() { execute { when(val r=auth.logoutAll()) { is AuthResult.Success -> navigate("emailLogin","Signed out on all devices.");else -> fail(error(r,"Unable to sign out. Please try again.")) } } }
    fun logout() { execute { val result = auth.logout(); navigate("emailLogin", if(result is AuthResult.Success) "Signed out." else "Signed out on this device; the server could not confirm logout.") } }
    fun delete(password: String) { if(password.isEmpty()) { fail("Enter your password.");return };execute { when(val r=auth.deleteAccount(password)) { is AuthResult.Success -> navigate("emailLogin","Your account has been deleted.");else -> fail(error(r,"Unable to delete the account. Check your password and try again.")) } } }
}
