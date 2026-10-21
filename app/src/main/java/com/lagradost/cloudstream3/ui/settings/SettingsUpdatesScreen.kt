package com.lagradost.cloudstream3.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AutoDownloadMode
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.MainActivityScreen
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.BackupUtils.restorePrompt
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.InAppUpdater.installPreReleaseIfNeeded
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream4.AppSettings
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.safefile.SafeFile
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import com.mihon.presentation.settings.collectAsState
import kotlinx.collections.immutable.persistentListOf

object SettingsUpdatesScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.category_updates)

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()

        // TODO Refactor entirely to use a different file path selector ect like QuickNovel
        val selectFileSelector =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                // It lies, it can be null if file manager quits.
                if (uri == null) return@rememberLauncherForActivityResult
                val context = CloudStreamApp.context ?: return@rememberLauncherForActivityResult

                try {
                    val settings = AppSettings(context)
                    // RW perms for the path
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                    context.contentResolver.takePersistableUriPermission(uri, flags)

                    val filePath = SafeFile.fromUri(context, uri)?.filePath()
                    println("Selected URI path: $uri - Full path: $filePath")

                    // store the actual URI instead of the path due to permissions.
                    // filePath should only be used for cosmetic purposes.
                    val visual = filePath ?: uri.toString()
                    PreferenceManager.getDefaultSharedPreferences(context).edit {
                        putString(context.getString(R.string.backup_path_key), uri.toString())
                        putString(context.getString(R.string.backup_dir_key), visual)
                    }
                    settings.backup.path.set(uri.toString())
                    settings.backup.visualPath.set(visual)
                } catch (t: Throwable) {
                    logError(t)
                }
            }

        val context = LocalContext.current
        val currentActivity = LocalActivity.current ?: (context as? Activity) ?: activity
        val defaultBackupVisual = remember(context) {
            BackupUtils.getDefaultBackupDir(context)?.filePath() ?: ""
        }
        val visualBackupPath by settings.backup.visualPath.collectAsState()
        val displayBackupPath = visualBackupPath.ifBlank { defaultBackupVisual }

        var showDialog by remember { mutableStateOf(false) }

        if (showDialog) {
            com.lagradost.cloudstream3.ui.settings.logcat.LogcatDialog {
                showDialog = false
            }
        }

        val githubViewModel = MainActivityScreen.githubViewModel()

        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_app_updates),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.check_for_update),
                        subtitle = BuildConfig.VERSION_NAME,
                        icon = painterResource(R.drawable.mobile_arrow_down_24px),
                        onClick = {
                            githubViewModel?.onAction(GithubAction.SearchForUpdate)
                            /*ioSafe {
                                if (activity?.runAutoUpdate(false) == false) {
                                    activity?.runOnUiThread {
                                        showToast(
                                            R.string.no_update_found,
                                            Toast.LENGTH_SHORT
                                        )
                                    }
                                }
                            }*/
                        }
                    ),

                    Preference.PreferenceItem.ListPreference(
                        title = stringResource(R.string.apk_installer_settings),
                        subtitle = stringResource(R.string.apk_installer_settings_des),
                        icon = painterResource(R.drawable.mobile_wrench_24px),
                        entries = integerArrayResource(R.array.apk_installer_values).zip(
                            stringArrayResource(R.array.apk_installer_pref)
                        ).toMap(),
                        preference = settings.updates.apkInstaller
                    ),

                    Preference.PreferenceItem.SwitchPreference(
                        title = stringResource(R.string.updates_settings),
                        subtitle = stringResource(R.string.updates_settings_des),
                        icon = painterResource(R.drawable.notifications_active_24px),
                        preference = settings.updates.showAppUpdates
                    )
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_backup),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.backup_settings),
                        icon = painterResource(R.drawable.save_as_24px),
                        onClick = {
                            val act = currentActivity ?: (context as? Activity) ?: activity
                            BackupUtils.backup(act)
                        }
                    ),
                    Preference.PreferenceItem.ListPreference(
                        title = stringResource(R.string.backup_frequency),
                        icon = painterResource(R.drawable.save_clock_24px),
                        entries = integerArrayResource(R.array.periodic_work_values).zip(
                            stringArrayResource(R.array.periodic_work_names)
                        ).toMap(),
                        preference = settings.backup.frequency
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.backup_path_title),
                        icon = painterResource(R.drawable.folder_24px),
                        subtitle = displayBackupPath,
                        onClick = {
                            // This is not a ListPreference because the old selection system is
                            // broken af. This needs to be refactored to QuickNovels download path
                            // system.
                            selectFileSelector.launch(Uri.EMPTY)
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.restore_settings),
                        icon = painterResource(R.drawable.restore_page_24px),
                        onClick = {
                            val act = currentActivity ?: (context as? Activity) ?: activity
                            act?.restorePrompt()
                        }
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_extensions),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        title = stringResource(R.string.automatic_plugin_updates),
                        icon = painterResource(R.drawable.extension_24px),
                        preference = settings.plugins.autoUpdate,
                    ),

                    Preference.PreferenceItem.ListPreference(
                        title = stringResource(R.string.automatic_plugin_download),
                        subtitle = "%s\n" + stringResource(R.string.automatic_plugin_download_summary),
                        icon = painterResource(R.drawable.extention_renew2),
                        entries = AutoDownloadMode.entries.map { it.value }.sorted()
                            .zip(stringArrayResource((R.array.auto_download_plugin))).toMap(),
                        preference = settings.plugins.autoDownload
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.update_plugins),
                        subtitle = stringResource(R.string.update_plugins_manually),
                        icon = painterResource(R.drawable.extention_download),
                        onClick = {
                            ioSafe {
                                val act = currentActivity ?: (context as? Activity) ?: activity
                                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_manuallyReloadAndUpdatePlugins(
                                    act ?: return@ioSafe
                                )
                            }
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.test_extensions),
                        subtitle = stringResource(R.string.test_extensions_summary),
                        icon = painterResource(R.drawable.baseline_network_ping_24),
                        onClick = {
                            val act = currentActivity ?: (context as? Activity) ?: activity
                            act?.navigate(R.id.action_navigation_global_to_navigation_test_providers)
                        })
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_actions),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.show_log_cat),
                        icon = painterResource(R.drawable.article_24px),
                        onClick = {
                            showDialog = true
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.redo_setup_process),
                        icon = painterResource(R.drawable.construction_24px),
                        onClick = {
                            val act = currentActivity ?: (context as? Activity) ?: activity
                            act?.navigate(R.id.action_navigation_global_to_navigation_setup_language)
                        }
                    ),
                )
            )
        )
    }
}