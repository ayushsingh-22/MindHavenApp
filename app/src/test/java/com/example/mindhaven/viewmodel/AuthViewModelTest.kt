package com.example.mindhaven.viewmodel

import com.example.mindhaven.data.session.AuthActions
import com.example.mindhaven.data.session.AuthResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private class Fake : AuthActions {
        var result: AuthResult = AuthResult.Success()
        var calls=0; var name=""; var pause: CompletableDeferred<Unit>? = null
        private suspend fun next(): AuthResult { calls++; pause?.await(); return result }
        override suspend fun register(email:String,password:String,name:String):AuthResult { this.name=name;return next() }
        override suspend fun verifyEmail(email:String,otp:String)=next()
        override suspend fun resendVerification(email:String)=next()
        override suspend fun login(email:String,password:String)=next()
        override suspend fun forgotPassword(email:String)=next()
        override suspend fun resetPassword(email:String,otp:String,password:String)=next()
        override suspend fun changePassword(currentPassword:String,newPassword:String)=next()
        override suspend fun logout()=next()
        override suspend fun logoutAll()=next()
        override suspend fun deleteAccount(password:String)=next()
    }
    private lateinit var fake: Fake
    private lateinit var vm: AuthFlowViewModel
    private val valid = "abcdefghijkl"
    @Before fun setup(){ Dispatchers.setMain(dispatcher);fake=Fake();vm=AuthFlowViewModel(fake) }
    @After fun cleanup(){ Dispatchers.resetMain() }
    @Test fun registrationAndCooldown() = runTest(dispatcher) {
        vm.register("Name","test",valid);runCurrent();assertEquals("Name",fake.name);assertEquals("verifyEmail",vm.ui.value.destination);assertEquals(60,vm.ui.value.cooldown)
        vm.resend();runCurrent();assertEquals(1,fake.calls);advanceTimeBy(60000);runCurrent();assertEquals(0,vm.ui.value.cooldown);vm.resend();runCurrent();assertEquals(2,fake.calls)
    }
    @Test fun registrationAndResendFailures() = runTest(dispatcher) {
        fake.result=AuthResult.Invalid;vm.register("Name","test",valid);runCurrent();assertTrue(vm.ui.value.message!!.contains("Unable"));assertNull(vm.ui.value.destination)
        vm.setEmail("test");fake.result=AuthResult.ServiceBusy(3);vm.resend();runCurrent();assertTrue(vm.ui.value.message!!.contains("busy"));assertEquals(0,vm.ui.value.cooldown)
        fake.result=AuthResult.Offline;vm.resend();runCurrent();assertTrue(vm.ui.value.message!!.contains("connect"))
    }
    @Test fun logoutFailureReportsLocalOnly() = runTest(dispatcher) {
        fake.result=AuthResult.Offline;vm.logout();runCurrent();assertEquals("emailLogin",vm.ui.value.destination);assertTrue(vm.ui.value.message!!.contains("could not confirm"))
    }
    @Test fun loginStates() = runTest(dispatcher) {
        fake.result=AuthResult.Invalid;vm.login("test",valid);runCurrent();assertEquals("Invalid email or password.",vm.ui.value.message)
        fake.result=AuthResult.NeedsVerification;vm.login("test",valid);runCurrent();assertEquals("verifyEmail",vm.ui.value.destination)
        vm.clearNavigation();fake.result=AuthResult.Success();vm.login("test",valid);runCurrent();assertNull(vm.ui.value.message)
        fake.result=AuthResult.ServiceBusy(3);vm.login("test",valid);runCurrent();assertTrue(vm.ui.value.message!!.contains("busy"))
        fake.result=AuthResult.Offline;vm.login("test",valid);runCurrent();assertTrue(vm.ui.value.message!!.contains("connect"))
    }
    @Test fun verificationSuccessAndFailure() = runTest(dispatcher) {
        vm.setEmail("test");fake.result=AuthResult.Invalid;vm.verify("123456");runCurrent();assertTrue(vm.ui.value.message!!.contains("Invalid"))
        fake.result=AuthResult.Success();vm.verify("123456");runCurrent();assertEquals("emailLogin",vm.ui.value.destination)
    }
    @Test fun resetFlowSuccessAndFailure() = runTest(dispatcher) {
        fake.result=AuthResult.Invalid;vm.forgot("test");runCurrent();assertTrue(vm.ui.value.message!!.contains("Unable"))
        fake.result=AuthResult.Success();vm.forgot("test");runCurrent();assertEquals("resetPassword",vm.ui.value.destination)
        vm.clearNavigation();fake.result=AuthResult.Invalid;vm.reset("123456",valid);runCurrent();assertTrue(vm.ui.value.message!!.contains("Invalid"))
        fake.result=AuthResult.Success();vm.reset("123456",valid);runCurrent();assertEquals("emailLogin",vm.ui.value.destination)
    }
    @Test fun changePasswordAndDelete() = runTest(dispatcher) {
        fake.result=AuthResult.Invalid;vm.change("old",valid);runCurrent();assertEquals("Current password is incorrect.",vm.ui.value.message)
        fake.result=AuthResult.Success();vm.change("old",valid);runCurrent();assertEquals("emailLogin",vm.ui.value.destination)
        vm.clearNavigation();fake.result=AuthResult.Invalid;vm.delete("old");runCurrent();assertTrue(vm.ui.value.message!!.contains("Unable"))
        fake.result=AuthResult.Success();vm.delete("old");runCurrent();assertEquals("emailLogin",vm.ui.value.destination)
    }
    @Test fun logoutAllAndLogout() = runTest(dispatcher) {
        fake.result=AuthResult.Invalid;vm.logoutAll();runCurrent();assertTrue(vm.ui.value.message!!.contains("Unable"))
        fake.result=AuthResult.Success();vm.logoutAll();runCurrent();assertEquals("emailLogin",vm.ui.value.destination)
        vm.clearNavigation();vm.logout();runCurrent();assertEquals("emailLogin",vm.ui.value.destination)
    }
    @Test fun passwordAndCodeValidation() = runTest(dispatcher) {
        vm.register("Name","test","short");runCurrent();assertEquals(0,fake.calls)
        vm.verify("123");runCurrent();assertEquals(0,fake.calls)
        vm.register("Name","test","😀".repeat(20));runCurrent();assertEquals(0,fake.calls)
    }
    @Test fun connectingIsVisibleDuringCall() = runTest(dispatcher) {
        fake.pause=CompletableDeferred();vm.login("test",valid);runCurrent();assertTrue(vm.ui.value.busy);fake.pause!!.complete(Unit);runCurrent();assertFalse(vm.ui.value.busy)
    }
}
