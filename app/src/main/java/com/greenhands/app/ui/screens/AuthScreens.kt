package com.greenhands.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.auth.DemoAuth
import com.greenhands.app.auth.FieldErrors
import com.greenhands.app.profile.LocalProfilePhotoStore
import com.greenhands.app.ui.components.CompactBrand
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.DemoPasswordField
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ProfileAvatar
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.TextAction
import com.greenhands.app.ui.theme.Spacing

@Composable
fun LoginScreen(
    initialEmail: String,
    banner: String?,
    onBannerShown: () -> Unit,
    onLogin: (email: String, rememberMe: Boolean) -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit
) {
    var email by rememberSaveable(initialEmail) { mutableStateOf(initialEmail) }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(initialEmail.isNotBlank()) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var errors by remember { mutableStateOf(FieldErrors()) }
    var submitting by remember { mutableStateOf(false) }

    var localBanner by rememberSaveable { mutableStateOf(banner) }
    LaunchedEffect(banner) {
        if (banner != null) {
            localBanner = banner
            onBannerShown()
        }
    }

    BackHandler(onBack = onBack)
    ScreenScaffold(title = stringResource(R.string.auth_login_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            CompactBrand()
            Spacer(Modifier.height(Spacing.section))
            SectionHeading(
                title = stringResource(R.string.auth_login_heading),
                subtitle = stringResource(R.string.auth_login_body)
            )
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.auth_demo_notice_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.xs))
            DemoNotice(stringResource(R.string.auth_demo_notice_body))
            Spacer(Modifier.height(Spacing.md))
            if (localBanner != null) {
                DemoNotice(localBanner!!)
                Spacer(Modifier.height(Spacing.md))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_email"),
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                isError = errors.email != null,
                supportingText = { if (errors.email != null) Text(errors.email!!) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(Modifier.height(Spacing.field))
            DemoPasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.auth_password),
                visible = showPassword,
                onToggleVisibility = { showPassword = !showPassword },
                error = errors.password,
                testTag = "login_password"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    modifier = Modifier.testTag("login_remember")
                )
                Text(stringResource(R.string.auth_remember), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Spacing.md))
            PrimaryActionButton(
                text = stringResource(R.string.auth_login_action),
                onClick = {
                    submitting = true
                    val result = DemoAuth.validateLogin(email, password)
                    errors = result
                    submitting = false
                    if (!result.hasErrors) {
                        onLogin(email.trim(), rememberMe)
                    }
                },
                enabled = !submitting,
                modifier = Modifier.testTag("login_submit")
            )
            TextAction(stringResource(R.string.auth_forgot), onForgotPassword, Modifier.testTag("login_forgot"))
            TextAction(stringResource(R.string.auth_create_account), onRegister, Modifier.testTag("login_register"))
        }
    }
}

@Composable
fun RegistrationScreen(
    onRegistered: (name: String, email: String, photoPath: String?) -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var terms by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var errors by remember { mutableStateOf(FieldErrors()) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photoError by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val photoStore = remember { LocalProfilePhotoStore(context) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = photoStore.importPickerUri(uri)
        if (imported == null) {
            photoError = true
        } else {
            photoError = false
            photoPath?.let { previous -> if (previous != imported) photoStore.deleteIfExists(previous) }
            photoPath = imported
        }
    }

    ScreenScaffold(title = stringResource(R.string.auth_register_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            CompactBrand()
            Spacer(Modifier.height(Spacing.section))
            SectionHeading(
                title = stringResource(R.string.auth_register_heading),
                subtitle = stringResource(R.string.auth_register_body)
            )
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.auth_demo_notice_body))
            Spacer(Modifier.height(Spacing.md))
            ProfileAvatar(
                name = name.ifBlank { stringResource(R.string.app_name) },
                photoPath = photoPath,
                size = 88.dp
            )
            Spacer(Modifier.height(Spacing.sm))
            SecondaryActionButton(
                text = stringResource(R.string.auth_add_photo),
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.testTag("register_add_photo")
            )
            Spacer(Modifier.height(Spacing.sm))
            TextAction(
                stringResource(R.string.auth_skip_photo),
                onClick = { photoPath = null; photoError = false },
                modifier = Modifier.testTag("register_skip_photo")
            )
            if (photoError) {
                Text(
                    stringResource(R.string.auth_photo_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_name"),
                label = { Text(stringResource(R.string.auth_full_name)) },
                singleLine = true,
                isError = errors.name != null,
                supportingText = { if (errors.name != null) Text(errors.name!!) }
            )
            Spacer(Modifier.height(Spacing.field))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_email"),
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                isError = errors.email != null,
                supportingText = { if (errors.email != null) Text(errors.email!!) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(Spacing.field))
            DemoPasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.auth_password),
                visible = showPassword,
                onToggleVisibility = { showPassword = !showPassword },
                error = errors.password,
                testTag = "register_password"
            )
            Spacer(Modifier.height(Spacing.field))
            DemoPasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = stringResource(R.string.auth_confirm_password),
                visible = showConfirm,
                onToggleVisibility = { showConfirm = !showConfirm },
                error = errors.confirmPassword,
                testTag = "register_confirm"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = terms,
                    onCheckedChange = { terms = it },
                    modifier = Modifier.testTag("register_terms")
                )
                Text(
                    stringResource(R.string.auth_terms),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Spacing.md)
                )
            }
            if (errors.terms != null) {
                Text(
                    errors.terms!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(Spacing.md))
            PrimaryActionButton(
                text = stringResource(R.string.auth_create_action),
                onClick = {
                    val result = DemoAuth.validateRegistration(name, email, password, confirm, terms)
                    errors = result
                    if (!result.hasErrors) {
                        onRegistered(name.trim(), email.trim(), photoPath)
                    }
                },
                modifier = Modifier.testTag("register_submit")
            )
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var sent by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.auth_forgot_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            CompactBrand()
            Spacer(Modifier.height(Spacing.section))
            SectionHeading(
                title = stringResource(R.string.auth_forgot_heading),
                subtitle = stringResource(R.string.auth_forgot_body)
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_email"),
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                isError = error != null,
                supportingText = { if (error != null) Text(error!!) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(Spacing.md))
            PrimaryActionButton(
                text = stringResource(R.string.auth_forgot_action),
                onClick = {
                    val result = DemoAuth.validateEmailOnly(email)
                    error = result.email
                    sent = result.email == null
                },
                modifier = Modifier.testTag("forgot_submit")
            )
            if (sent) {
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(stringResource(R.string.auth_forgot_sent, email.trim()))
            }
            Spacer(Modifier.height(Spacing.sm))
            TextAction(stringResource(R.string.auth_back_login), onBack, Modifier.testTag("forgot_back_login"))
        }
    }
}
