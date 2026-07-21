package com.chronicle.app.presentation.auth

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit, viewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val authState by viewModel.authState.collectAsState()
    val primaryColor = Color(0xFF6C63FF)
    val successColor = Color(0xFF4CAF82) // Green for success

    // NEW A+ UX: Handle the Success State with a Toast and a brief pause
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            val message = (authState as AuthState.Success).message
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            // Pause for 600 milliseconds so the user sees the checkmark!
            delay(600)
            onAuthSuccess()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF3F0FF))))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Memora", fontSize = 48.sp, fontWeight = FontWeight.Black, color = primaryColor)
            Text(text = "Your private thoughts, secured.", color = Color.Gray, modifier = Modifier.padding(bottom = 32.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, leadingIcon = { Icon(Icons.Default.Email, null) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true
            )

            if (authState is AuthState.Error) {
                Text(text = (authState as AuthState.Error).message, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(32.dp))

            // UPGRADED BUTTON: Animates between Loading, Success Checkmark, and Text
            Button(
                onClick = { viewModel.authenticate(email, password, isLoginMode) },
                modifier = Modifier.fillMaxWidth().height(56.dp).animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (authState is AuthState.Success) successColor else primaryColor
                )
            ) {
                when (authState) {
                    is AuthState.Loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    is AuthState.Success -> Icon(Icons.Default.Check, contentDescription = "Success", tint = Color.White, modifier = Modifier.size(28.dp))
                    else -> Text(if (isLoginMode) "Sign In" else "Create Account", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { isLoginMode = !isLoginMode; viewModel.resetState() }) {
                Text(text = if (isLoginMode) "New here? Create an account" else "Already have an account? Sign In", color = primaryColor)
            }
        }
    }
}