package com.example.mindhaven.ui.theme.screens.userRegistration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindhaven.ui.theme.*
import com.example.mindhaven.viewmodel.AuthFlowUi
import com.example.mindhaven.viewmodel.AuthFlowViewModel

@Composable
private fun FormFrame(title: String, ui: AuthFlowUi, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LavenderBlush,PaleLavender,LavenderBlue))).verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment=Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(title, fontSize=30.sp, color=MediumPurple)
            ui.message?.let { Text(it,color=TwilightLavender) }
            if(ui.busy) { CircularProgressIndicator(color=MediumPurple);Text("Connecting… this can take up to a minute.",color=TwilightLavender) }
            content()
        }
    }
}
@Composable private fun Input(value: String, onValue: (String)->Unit, label: String, secret: Boolean=false, digits: Boolean=false) {
    OutlinedTextField(value=value,onValueChange=onValue,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),visualTransformation=if(secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,keyboardOptions=KeyboardOptions(keyboardType=if(digits) KeyboardType.Number else KeyboardType.Text))
}
@Composable private fun Action(label:String,enabled:Boolean=true,run:()->Unit) { Button(onClick=run,enabled=enabled,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=MediumPurple)){Text(label)} }

@Composable fun VerifyEmailScreen(vm: AuthFlowViewModel) {
    val ui by vm.ui.collectAsState();var code by remember { mutableStateOf("") }
    FormFrame("Verify email",ui) {
        Text("Enter the six-digit code sent if your address is eligible.",color=TwilightLavender)
        Input(code,{ code=it.filter(Char::isDigit).take(6) },"Verification code",digits=true)
        Action("Verify",!ui.busy){vm.verify(code)}
        TextButton(onClick={vm.resend()},enabled=!ui.busy && ui.cooldown==0){Text(if(ui.cooldown>0) "Resend in ${ui.cooldown}s" else "Resend code")}
    }
}
@Composable fun ForgotPasswordScreen(vm:AuthFlowViewModel) {
    val ui by vm.ui.collectAsState();var address by remember { mutableStateOf(vm.email) }
    FormFrame("Forgot password",ui) { Input(address,{address=it},"Email");Action("Send reset code",!ui.busy){vm.forgot(address)} }
}
@Composable fun ResetPasswordScreen(vm:AuthFlowViewModel) {
    val ui by vm.ui.collectAsState();var code by remember { mutableStateOf("") };var password by remember { mutableStateOf("") }
    FormFrame("Reset password",ui) { Input(code,{code=it.filter(Char::isDigit).take(6)},"Reset code",digits=true);Input(password,{password=it},"New password",secret=true);Text("At least 12 characters; at most 72 UTF-8 bytes.",color=TwilightLavender);Action("Reset password",!ui.busy){vm.reset(code,password)} }
}
@Composable fun ChangePasswordScreen(vm:AuthFlowViewModel) {
    val ui by vm.ui.collectAsState();var current by remember { mutableStateOf("") };var password by remember { mutableStateOf("") }
    FormFrame("Change password",ui) { Text("Changing your password signs you out on all devices.",color=TwilightLavender);Input(current,{current=it},"Current password",secret=true);Input(password,{password=it},"New password",secret=true);Text("At least 12 characters; at most 72 UTF-8 bytes.",color=TwilightLavender);Action("Change password",!ui.busy){vm.change(current,password)} }
}
@Composable fun DeleteAccountScreen(vm:AuthFlowViewModel) {
    val ui by vm.ui.collectAsState();var password by remember { mutableStateOf("") };var confirm by remember { mutableStateOf(false) }
    FormFrame("Delete account",ui) { Text("This permanently deletes your account and all associated data. This cannot be undone.",color=Color.Red);Input(password,{password=it},"Password",secret=true);Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=confirm,onCheckedChange={confirm=it});Text("I understand this is permanent")};Action("Delete account",confirm && !ui.busy){vm.delete(password)} }
}

@Composable fun LoginFlowScreen(vm: AuthFlowViewModel, onForgot: () -> Unit, onRegister: () -> Unit) {
    val ui by vm.ui.collectAsState(); var address by remember { mutableStateOf(vm.email) }; var password by remember { mutableStateOf("") }
    FormFrame("Welcome back",ui) {
        Input(address,{address=it},"Email"); Input(password,{password=it},"Password",secret=true)
        Action("Login",!ui.busy){vm.login(address,password)}
        TextButton(onClick=onForgot){Text("Forgot password?")}
        TextButton(onClick=onRegister){Text("Create an account")}
    }
}
