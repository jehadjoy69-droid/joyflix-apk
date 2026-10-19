package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.navigation.NavOptions
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class SettingsProviders : BasePreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_providers)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_providers, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        getPref(R.string.display_sub_key)?.setOnPreferenceClickListener {
            activity?.getApiDubstatusSettings()?.let { current ->
                val dublist = DubStatus.entries
                val names = dublist.map { it.name }

                val currentList = ArrayList<Int>()
                for (i in current) {
                    currentList.add(dublist.indexOf(i))
                }

                activity?.showMultiDialog(
                    names,
                    currentList,
                    getString(R.string.display_subbed_dubbed_settings),
                    {}
                ) { selectedList ->
                    APIRepository.dubStatusActive = selectedList.map { dublist[it] }.toHashSet()
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.display_sub_key),
                            selectedList.map { names[it] }.toMutableSet()
                        )
                    }
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.test_providers_key)?.setOnPreferenceClickListener {
            // Somehow animations do not work without this.
            val options = NavOptions.Builder()
                .setEnterAnim(R.anim.enter_anim)
                .setExitAnim(R.anim.exit_anim)
                .setPopEnterAnim(R.anim.pop_enter)
                .setPopExitAnim(R.anim.pop_exit)
                .build()

            this@SettingsProviders.findNavController()
                .navigate(R.id.navigation_test_providers, null, options)
            true
        }

        getPref(R.string.enable_nsfw_on_providers_key)?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: false
            MainAPI.settingsForProvider.enableAdult = enabled
            true
        }

        getPref(R.string.prefer_media_type_key)?.setOnPreferenceClickListener {
            val isAdult = com.lagradost.cloudstream3.NsfwHelper.isAdultEnabled
            val validTypes = enumValues<TvType>().filter { isAdult || it != TvType.NSFW }.sorted()
            val names = validTypes.map { it.name }
            val default = validTypes.map { it.ordinal }
            val defaultSet = default.map { it.toString() }.toSet()
            val currentList = try {
                settingsManager.getStringSet(getString(R.string.prefer_media_type_key), defaultSet)
                    ?.mapNotNull {
                        it.toIntOrNull()
                    }?.filter { isAdult || it != TvType.NSFW.ordinal }
            } catch (e: Throwable) {
                null
            } ?: default

            val currentIndexList = currentList.mapNotNull { ord ->
                validTypes.indexOfFirst { it.ordinal == ord }.takeIf { it >= 0 }
            }

            activity?.showMultiDialog(
                names,
                currentIndexList,
                getString(R.string.preferred_media_settings),
                {}
            ) { selectedIndexes ->
                val selectedOrdinals = selectedIndexes.map { validTypes[it].ordinal.toString() }.toMutableSet()
                settingsManager.edit {
                    putStringSet(
                        getString(R.string.prefer_media_type_key),
                        selectedOrdinals
                    )
                }
                DataStoreHelper.currentHomePage = null
                //(context ?: CloudStreamApp.context)?.let { ctx -> app.initClient(ctx) }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.
        provider_lang_key)?.setOnPreferenceClickListener {
            activity?.getApiProviderLangSettings()?.let { currentLangTags ->
                val languagesTagName = APIHolder.apis.withLock {
                    listOf(Pair(AllLanguagesName, getString(R.string.all_languages_preference))) +
                        APIHolder.apis.map { Pair(it.lang, getNameNextToFlagEmoji(it.lang) ?: it.lang) }
                            .toSet().sortedBy { it.second.substringAfter("\u00a0").lowercase() }
                }

                val currentIndexList = currentLangTags.map { langTag ->
                    languagesTagName.indexOfFirst { lang -> lang.first == langTag }
                }

                activity?.showMultiDialog(
                    languagesTagName.map { it.second },
                    currentIndexList,
                    getString(R.string.provider_lang_settings),
                    {}
                ) { selectedList ->
                    settingsManager.edit {
                        putStringSet(
                            getString(R.string.provider_lang_key),
                            selectedList.map { languagesTagName[it].first }.toSet()
                        )
                    }
                    // APIRepository.providersActive = it.context.getApiSettings()
                }
            }

            return@setOnPreferenceClickListener true
        }
    }
}
