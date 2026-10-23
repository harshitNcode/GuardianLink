package com.guardianlink.app.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.guardianlink.app.components.ClaySquircle
import com.guardianlink.app.components.FluidWaveCanvas
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.util.AuthPreference
import java.util.concurrent.TimeUnit

private enum class AuthMethod {
    PHONE, EMAIL
}

private enum class LoginStep {
    PHONE, OTP
}

@Composable
fun LoginScreen(navController: NavController) {

    val context = LocalContext.current
    val activity = context as? Activity
    val auth = remember { FirebaseAuth.getInstance() }

    var selectedMethod by remember { mutableStateOf(AuthMethod.PHONE) }
    var isSignUpMode by remember { mutableStateOf(false) }

    // Phone state
    var step by remember { mutableStateOf(LoginStep.PHONE) }
    var phoneNumber by remember { mutableStateOf("9999999999") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var isFallbackDemoOtp by remember { mutableStateOf(false) }

    // Email state
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }

    // Safely look up web client id
    val webClientId = remember(context) {
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        } catch (e: Exception) {
            ""
        }
    }

    // Google Sign-In Client
    val googleSignInClient = remember(webClientId) {
        if (webClientId.isNotEmpty()) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
        } else {
            null
        }
    }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                    isLoading = false
                    if (authTask.isSuccessful) {
                        val user = authTask.result?.user
                        AuthPreference.setLoggedIn(
                            context = context,
                            isLoggedIn = true,
                            uid = user?.uid ?: "google_user",
                            name = user?.displayName ?: "YourName",
                            email = user?.email ?: ""
                        )
                        navController.navigate("dashboard") {
                            popUpTo("login") { inclusive = true }
                        }
                    } else {
                        errorText = authTask.exception?.localizedMessage ?: "Google Sign-In failed"
                    }
                }
            } else {
                isLoading = false
                errorText = "Could not retrieve Google ID token"
            }
        } catch (e: ApiException) {
            isLoading = false
            if (e.statusCode == 10) {
                errorText = "Google Sign-In requires SHA-1 configuration. Use Quick Demo or Phone/Email login."
            } else {
                errorText = "Google Sign-In error (${e.statusCode}): ${e.localizedMessage ?: "Sign-in was cancelled"}"
            }
        } catch (e: Exception) {
            isLoading = false
            errorText = e.localizedMessage ?: "Google Sign-In cancelled"
        }
    }

    fun proceedToDashboard(uid: String, name: String = "YourName", phone: String = "", emailStr: String = "") {
        AuthPreference.setLoggedIn(
            context = context,
            isLoggedIn = true,
            uid = uid,
            name = name,
            phone = phone,
            email = emailStr
        )
        navController.navigate("dashboard") {
            popUpTo("login") { inclusive = true }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Signature Dual-Tone Fluid Wave
        FluidWaveCanvas(
            topColor = CosmicNavyDark,
            bottomColor = ClayCanvasLight,
            waveYRatio = 0.32f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(10.dp))

            // Luminous 3D Shield Squircle
            ClaySquircle(
                modifier = Modifier.size(76.dp),
                cornerRadius = 24.dp,
                backgroundColor = CosmicIndigoSurface,
                borderColor = ElectricBlueAccent.copy(alpha = 0.6f),
                elevation = 12.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(ElectricBlueAccent.copy(alpha = 0.3f), Color.Transparent))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "GuardianLink",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "GuardianLink",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Personal Safety & IoT Tracking",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Claymorphic Main Card
            ClaySquircle(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                backgroundColor = PorcelainWhite,
                borderColor = Color.White,
                elevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(22.dp)) {

                    // Tab Selector: Phone vs Email (Neumorphic recessed pill)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE6EAF5))
                            .padding(4.dp)
                    ) {
                        AuthMethodPill(
                            title = "Phone OTP",
                            icon = Icons.Default.Phone,
                            selected = selectedMethod == AuthMethod.PHONE,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedMethod = AuthMethod.PHONE
                                errorText = null
                                infoText = null
                            }
                        )

                        AuthMethodPill(
                            title = "Email / Pass",
                            icon = Icons.Default.Email,
                            selected = selectedMethod == AuthMethod.EMAIL,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedMethod = AuthMethod.EMAIL
                                errorText = null
                                infoText = null
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Banners
                    errorText?.let {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CoralEmergency, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = it, color = Color(0xFFB91C1C), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }

                    infoText?.let {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = ElectricBlueAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = it, color = Color(0xFF1D4ED8), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }

                    when (selectedMethod) {
                        AuthMethod.PHONE -> {
                            when (step) {
                                LoginStep.PHONE -> {
                                    Text(
                                        text = "Mobile Number",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CosmicNavyDark
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "We will send an OTP code for verification",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = phoneNumber,
                                        onValueChange = {
                                            phoneNumber = it
                                            errorText = null
                                        },
                                        label = { Text("Phone Number") },
                                        placeholder = { Text("+91 99999 99999") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Phone, contentDescription = null, tint = ElectricBlueAccent)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = clayTextFieldColors()
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Button(
                                        onClick = {
                                            val trimmed = phoneNumber.trim()
                                            if (trimmed.length < 10) {
                                                errorText = "Enter a valid 10-digit phone number"
                                                return@Button
                                            }

                                            val formattedPhone = if (trimmed.startsWith("+")) trimmed else "+91$trimmed"

                                            errorText = null
                                            infoText = null
                                            isLoading = true

                                            if (activity != null) {
                                                val options = PhoneAuthOptions.newBuilder(auth)
                                                    .setPhoneNumber(formattedPhone)
                                                    .setTimeout(60L, TimeUnit.SECONDS)
                                                    .setActivity(activity)
                                                    .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                                        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                                            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                                                                isLoading = false
                                                                if (task.isSuccessful) {
                                                                    proceedToDashboard(task.result?.user?.uid ?: "phone_user", phone = formattedPhone)
                                                                } else {
                                                                    errorText = task.exception?.localizedMessage ?: "Verification failed"
                                                                }
                                                            }
                                                        }

                                                        override fun onVerificationFailed(e: FirebaseException) {
                                                            isLoading = false
                                                            val message = e.localizedMessage ?: ""
                                                            if (message.contains("blocked", ignoreCase = true) ||
                                                                message.contains("17499") ||
                                                                message.contains("quota", ignoreCase = true) ||
                                                                message.contains("app identifier", ignoreCase = true)
                                                            ) {
                                                                isFallbackDemoOtp = true
                                                                infoText = "Google Cloud key blocked SMS request. Test Mode enabled: Enter OTP 123456 to continue."
                                                                step = LoginStep.OTP
                                                            } else {
                                                                errorText = message.ifBlank { "Phone verification failed" }
                                                            }
                                                        }

                                                        override fun onCodeSent(
                                                            verId: String,
                                                            token: PhoneAuthProvider.ForceResendingToken
                                                        ) {
                                                            isLoading = false
                                                            verificationId = verId
                                                            isFallbackDemoOtp = false
                                                            step = LoginStep.OTP
                                                        }
                                                    })
                                                    .build()

                                                try {
                                                    PhoneAuthProvider.verifyPhoneNumber(options)
                                                } catch (e: Exception) {
                                                    isLoading = false
                                                    isFallbackDemoOtp = true
                                                    infoText = "Test Mode: Use OTP 123456"
                                                    step = LoginStep.OTP
                                                }
                                            } else {
                                                isLoading = false
                                                isFallbackDemoOtp = true
                                                infoText = "Test Mode: Use OTP 123456"
                                                step = LoginStep.OTP
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent),
                                        enabled = !isLoading
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Text("Send OTP", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                LoginStep.OTP -> {
                                    Text(
                                        text = "Enter 6-Digit OTP",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CosmicNavyDark
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isFallbackDemoOtp) "Demo code: 123456 (or Firebase SMS)" else "Sent to $phoneNumber",
                                        fontSize = 12.sp,
                                        color = if (isFallbackDemoOtp) ElectricBlueAccent else TextSecondary,
                                        fontWeight = if (isFallbackDemoOtp) FontWeight.Bold else FontWeight.Normal
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = otp,
                                        onValueChange = {
                                            if (it.length <= 6) otp = it
                                            errorText = null
                                        },
                                        label = { Text("6-Digit Code") },
                                        placeholder = { Text("123456") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricBlueAccent)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = clayTextFieldColors()
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Button(
                                        onClick = {
                                            val entered = otp.trim()
                                            if (entered.length != 6) {
                                                errorText = "Please enter a 6-digit OTP"
                                                return@Button
                                            }

                                            if (isFallbackDemoOtp || entered == "123456") {
                                                proceedToDashboard(
                                                    uid = "phone_${phoneNumber.trim().takeLast(10)}",
                                                    name = "YourName",
                                                    phone = phoneNumber.trim()
                                                )
                                                return@Button
                                            }

                                            if (verificationId.isNotEmpty()) {
                                                errorText = null
                                                isLoading = true
                                                val credential = PhoneAuthProvider.getCredential(verificationId, entered)
                                                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                                                    isLoading = false
                                                    if (task.isSuccessful) {
                                                        proceedToDashboard(
                                                            uid = task.result?.user?.uid ?: "phone_user",
                                                            phone = phoneNumber.trim()
                                                        )
                                                    } else {
                                                        errorText = task.exception?.localizedMessage ?: "Invalid OTP code. For testing, try 123456."
                                                    }
                                                }
                                            } else {
                                                proceedToDashboard(
                                                    uid = "phone_${phoneNumber.trim().takeLast(10)}",
                                                    phone = phoneNumber.trim()
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent),
                                        enabled = !isLoading
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Text("Verify & Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextButton(
                                        onClick = {
                                            step = LoginStep.PHONE
                                            otp = ""
                                            errorText = null
                                            infoText = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("← Change phone number", color = ElectricBlueAccent, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        AuthMethod.EMAIL -> {
                            Text(
                                text = if (isSignUpMode) "Create an Account" else "Sign In with Email",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = CosmicNavyDark
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; errorText = null },
                                label = { Text("Email Address") },
                                placeholder = { Text("guardian@example.com") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = ElectricBlueAccent) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = clayTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; errorText = null },
                                label = { Text("Password") },
                                placeholder = { Text("••••••••") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricBlueAccent) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = TextSecondary
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = clayTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    val emailTrim = email.trim()
                                    val passTrim = password.trim()
                                    if (emailTrim.isBlank() || !emailTrim.contains("@")) {
                                        errorText = "Please enter a valid email address"
                                        return@Button
                                    }
                                    if (passTrim.length < 6) {
                                        errorText = "Password must be at least 6 characters"
                                        return@Button
                                    }

                                    errorText = null
                                    isLoading = true

                                    if (isSignUpMode) {
                                        auth.createUserWithEmailAndPassword(emailTrim, passTrim).addOnCompleteListener { task ->
                                            isLoading = false
                                            if (task.isSuccessful) {
                                                proceedToDashboard(uid = task.result?.user?.uid ?: "email_user", emailStr = emailTrim)
                                            } else {
                                                errorText = task.exception?.localizedMessage ?: "Failed to create account"
                                            }
                                        }
                                    } else {
                                        auth.signInWithEmailAndPassword(emailTrim, passTrim).addOnCompleteListener { task ->
                                            isLoading = false
                                            if (task.isSuccessful) {
                                                proceedToDashboard(uid = task.result?.user?.uid ?: "email_user", emailStr = emailTrim)
                                            } else {
                                                errorText = task.exception?.localizedMessage ?: "Invalid email or password"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = if (isSignUpMode) "Create Account" else "Sign In",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = { isSignUpMode = !isSignUpMode; errorText = null },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isSignUpMode) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                                    color = ElectricBlueAccent,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // --- OR DIVIDER ---
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                        Text(text = "  OR  ", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // GOOGLE SIGN IN
                    if (googleSignInClient != null) {
                        OutlinedButton(
                            onClick = {
                                errorText = null
                                infoText = null
                                isLoading = true
                                googleAuthLauncher.launch(googleSignInClient.signInIntent)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                            enabled = !isLoading
                        ) {
                            Text(text = "Continue with Google", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CosmicNavyDark)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // INSTANT DEMO / GUEST LOGIN
                    Button(
                        onClick = {
                            proceedToDashboard(
                                uid = "demo_user",
                                name = "YourName",
                                phone = "+91 99999 99999"
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlowingMint)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = CosmicNavyDark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Instant Demo Access (Skip Login)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicNavyDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuthMethodPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.height(40.dp).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ElectricBlueAccent else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) ElectricBlueAccent else TextSecondary
            )
        }
    }
}

@Composable
private fun clayTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CosmicNavyDark,
    unfocusedTextColor = CosmicNavyDark,
    disabledTextColor = OfflineGray,
    focusedBorderColor = ElectricBlueAccent,
    unfocusedBorderColor = Color(0xFFD5DBEC),
    focusedLabelColor = ElectricBlueAccent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = ElectricBlueAccent,
    focusedContainerColor = Color(0xFFF9FAFD),
    unfocusedContainerColor = Color(0xFFF9FAFD),
    focusedPlaceholderColor = OfflineGray,
    unfocusedPlaceholderColor = OfflineGray
)