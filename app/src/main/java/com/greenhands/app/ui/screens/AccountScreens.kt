package com.greenhands.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.auth.DemoAuth
import com.greenhands.app.profile.LocalProfilePhotoStore
import com.greenhands.app.session.SessionState
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.EmptyStateText
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ProfileAvatar
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.components.TextAction
import com.greenhands.app.ui.theme.Spacing
import com.greenhands.app.ui.theme.ThemeMode

@Composable
fun AccountHomeScreen(
    session: SessionState,
    onProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onLogout: () -> Unit
) {
    ScreenScaffold(title = stringResource(R.string.account_title)) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("account_home")) {
            ProfileAvatar(session.userName.ifBlank { stringResource(R.string.app_name) }, session.photoPath, size = 56.dp)
            Spacer(Modifier.height(Spacing.md))
            Text(
                session.userName.ifBlank { stringResource(R.string.dashboard_title_fallback) },
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                session.userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            StatusChip(stringResource(R.string.account_demo_status))
            Spacer(Modifier.height(Spacing.section))
            Text(stringResource(R.string.account_section_profile), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.sm))
            InfoCard {
                AccountNavRow(stringResource(R.string.account_profile), "account_profile", onProfile)
                AccountNavRow(stringResource(R.string.account_edit_profile), "account_edit_profile", onEditProfile)
            }
            Spacer(Modifier.height(Spacing.section))
            Text(stringResource(R.string.account_section_app), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.sm))
            InfoCard {
                AccountNavRow(stringResource(R.string.account_settings), "account_settings", onSettings)
                AccountNavRow(stringResource(R.string.account_help), "account_help", onHelp)
                AccountNavRow(stringResource(R.string.account_about), "account_about", onAbout)
                AccountNavRow(stringResource(R.string.account_privacy), "account_privacy", onPrivacy)
            }
            Spacer(Modifier.height(Spacing.section))
            SecondaryActionButton(
                text = stringResource(R.string.account_logout),
                onClick = onLogout,
                modifier = Modifier.testTag("account_logout")
            )
        }
    }
}

@Composable
private fun AccountNavRow(label: String, testTag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_open),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProfileScreen(
    session: SessionState,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    ScreenScaffold(title = stringResource(R.string.account_profile), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("profile_screen")) {
            ProfileAvatar(session.userName.ifBlank { stringResource(R.string.app_name) }, session.photoPath, size = 88.dp)
            Spacer(Modifier.height(Spacing.md))
            Text(
                session.userName.ifBlank { stringResource(R.string.dashboard_title_fallback) },
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                session.userEmail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            StatusChip(stringResource(R.string.profile_type))
            Spacer(Modifier.height(Spacing.section))
            DemoNotice(stringResource(R.string.profile_notice))
            Spacer(Modifier.height(Spacing.section))
            PrimaryActionButton(stringResource(R.string.account_edit_profile), onEdit, Modifier.testTag("profile_edit"))
        }
    }
}

@Composable
fun EditProfileScreen(
    session: SessionState,
    onSave: (name: String, email: String, photoPath: String?, removePhoto: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(session.userName) }
    var email by rememberSaveable { mutableStateOf(session.userEmail) }
    var errors by remember { mutableStateOf(listOf("", "")) }
    var draftPhotoPath by rememberSaveable { mutableStateOf(session.photoPath) }
    var removePhoto by rememberSaveable { mutableStateOf(false) }
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
            removePhoto = false
            if (draftPhotoPath != null && draftPhotoPath != session.photoPath && draftPhotoPath != imported) {
                photoStore.deleteIfExists(draftPhotoPath)
            }
            draftPhotoPath = imported
        }
    }

    ScreenScaffold(title = stringResource(R.string.account_edit_profile), onBack = onCancel) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding)) {
            ProfileAvatar(
                name = name.ifBlank { stringResource(R.string.app_name) },
                photoPath = if (removePhoto) null else draftPhotoPath,
                size = 96.dp,
                modifier = Modifier.testTag("edit_photo_preview")
            )
            Spacer(Modifier.height(Spacing.md))
            if (draftPhotoPath != null && !removePhoto) {
                PrimaryActionButton(
                    text = stringResource(R.string.edit_photo_change),
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("edit_photo_change")
                )
                Spacer(Modifier.height(Spacing.related))
                SecondaryActionButton(
                    text = stringResource(R.string.edit_photo_remove),
                    onClick = { removePhoto = true; photoError = false },
                    modifier = Modifier.testTag("edit_photo_remove")
                )
            } else {
                PrimaryActionButton(
                    text = stringResource(R.string.edit_photo_add),
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("edit_photo_add")
                )
            }
            if (photoError) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.auth_photo_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(Spacing.section))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_name"),
                label = { Text(stringResource(R.string.auth_full_name)) },
                singleLine = true,
                isError = errors[0].isNotBlank(),
                supportingText = { if (errors[0].isNotBlank()) Text(errors[0]) }
            )
            Spacer(Modifier.height(Spacing.field))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_email"),
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                isError = errors[1].isNotBlank(),
                supportingText = { if (errors[1].isNotBlank()) Text(errors[1]) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(Spacing.md))
            PrimaryActionButton(
                text = stringResource(R.string.edit_save),
                onClick = {
                    val result = DemoAuth.validateProfile(name, email)
                    errors = listOf(result.name.orEmpty(), result.email.orEmpty())
                    if (!result.hasErrors) {
                        onSave(name.trim(), email.trim(), if (removePhoto) null else draftPhotoPath, removePhoto)
                    }
                },
                modifier = Modifier.testTag("edit_save")
            )
            Spacer(Modifier.height(Spacing.related))
            SecondaryActionButton(
                text = stringResource(R.string.action_cancel),
                onClick = {
                    if (draftPhotoPath != session.photoPath) {
                        photoStore.deleteIfExists(draftPhotoPath)
                    }
                    onCancel()
                },
                modifier = Modifier.testTag("edit_cancel")
            )
        }
    }
}

@Composable
fun SettingsScreen(
    session: SessionState,
    onThemeChange: (ThemeMode) -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var showReset by rememberSaveable { mutableStateOf(false) }
    val selectedTheme = when (session.themeMode) {
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("settings_screen")) {
            SectionHeading(stringResource(R.string.settings_appearance))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.settings_selected_theme, selectedTheme),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Column(Modifier.selectableGroup().fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = session.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = Spacing.sm)
                            .testTag("theme_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = session.themeMode == mode,
                            onClick = null
                        )
                        Text(label, modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.section))
            SectionHeading(stringResource(R.string.settings_units))
            Spacer(Modifier.height(Spacing.sm))
            InfoCard {
                Text(stringResource(R.string.settings_celsius), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_celsius_body), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.section))
            SettingSwitch(
                title = stringResource(R.string.settings_demo),
                subtitle = if (session.demoModeEnabled) {
                    stringResource(R.string.settings_demo_on)
                } else {
                    stringResource(R.string.settings_demo_off)
                },
                checked = session.demoModeEnabled,
                onCheckedChange = onDemoModeChange
            )
            SettingSwitch(
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_body),
                checked = session.notificationsEnabled,
                onCheckedChange = onNotificationsChange
            )
            Spacer(Modifier.height(Spacing.section))
            SecondaryActionButton(
                text = stringResource(R.string.settings_reset),
                onClick = { showReset = true },
                modifier = Modifier.testTag("settings_reset")
            )
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showReset = false
                    }
                ) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val sections = listOf(
        stringResource(R.string.help_overview_title) to stringResource(R.string.help_overview_body),
        stringResource(R.string.help_dash_title) to stringResource(R.string.help_dash_body),
        stringResource(R.string.help_heat_title) to stringResource(R.string.help_heat_body),
        stringResource(R.string.help_demo_title) to stringResource(R.string.help_demo_body),
        stringResource(R.string.help_safety_title) to stringResource(R.string.help_safety_body)
    )
    ScreenScaffold(title = stringResource(R.string.help_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding)) {
            sections.forEach { (title, body) ->
                InfoCard {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(Spacing.related))
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onPrivacy: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("about_screen")) {
            SectionHeading(stringResource(R.string.about_heading), stringResource(R.string.about_subtitle))
            Spacer(Modifier.height(Spacing.md))
            EmptyStateText(stringResource(R.string.about_body), modifier = Modifier.testTag("about_body"))
            Spacer(Modifier.height(Spacing.section))
            InfoCard {
                Text(stringResource(R.string.module_sensor_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.module_sensor_body), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(stringResource(R.string.module_heat_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.module_heat_body), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(stringResource(R.string.module_harvest_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.module_harvest_body), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(stringResource(R.string.module_decision_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.module_decision_body), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard {
                Text(stringResource(R.string.about_research_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.about_research_body), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.about_prototype_status))
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.about_version),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("about_version")
            )
            Spacer(Modifier.height(Spacing.related))
            TextAction(
                stringResource(R.string.about_privacy_link),
                onClick = onPrivacy,
                modifier = Modifier.testTag("about_privacy")
            )
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.privacy_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding)) {
            InfoCard {
                Text(stringResource(R.string.privacy_demo_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.privacy_demo_body))
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(stringResource(R.string.privacy_sensor_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.privacy_sensor_body))
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(stringResource(R.string.privacy_camera_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.privacy_camera_body))
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(stringResource(R.string.privacy_upload_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.privacy_upload_body))
            }
        }
    }
}

@Composable
fun LogoutConfirmScreen(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    ScreenScaffold(title = stringResource(R.string.logout_title), onBack = onCancel) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("logout_confirm")) {
            SectionHeading(
                title = stringResource(R.string.logout_heading),
                subtitle = stringResource(R.string.logout_body)
            )
            Spacer(Modifier.height(Spacing.section))
            PrimaryActionButton(stringResource(R.string.logout_action), onConfirm, Modifier.testTag("logout_confirm_button"))
            Spacer(Modifier.height(Spacing.related))
            SecondaryActionButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                modifier = Modifier.testTag("logout_cancel")
            )
        }
    }
}
