// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.WhatsNewUtil
import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.PluginManagementPolicy
// phantom-idea: removed the reloadPluginIcon import, used only by the removed Toolbox App promotion
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
// phantom-idea: removed imports used only by the removed Toolbox App promotion (Registry, AlignY, EmptySpacingConfiguration)
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JEditorPane

// phantom-idea: removed TOOLBOX_URL and the UnscaledGaps imports, used only by the removed Toolbox App promotion
@ApiStatus.Internal
class UpdateSettingsConfigurable @JvmOverloads constructor (private val checkNowEnabled: Boolean = true) :
  BoundConfigurable(IdeBundle.message("updates.settings.title"), "preferences.updates") {

  private lateinit var myLink: JComponent
  private lateinit var myLastCheckedLabel: JEditorPane

  override fun createPanel(): DialogPanel {
    val settings = UpdateSettings.getInstance()
    val manager = ExternalUpdateManager.ACTUAL
    val channelSelectionLockedMessage = UpdateStrategyCustomization.getInstance().getChannelSelectionLockedMessage()
    val appInfo = ApplicationInfo.getInstance()
    val channelModel = CollectionComboBoxModel(settings.activeChannels)

    return panel {
      row(IdeBundle.message("updates.settings.current.version") + ' ' + ApplicationNamesInfo.getInstance().fullProductName + ' ' + appInfo.fullVersion) {
        comment(appInfo.build.asString() + ' ' + NlsMessages.formatDateLong(appInfo.buildDate.time))
      }.bottomGap(BottomGap.SMALL)

      row {
        when {
          manager != null -> {
            comment(IdeBundle.message("updates.settings.external", manager.toolName))
          }
          channelSelectionLockedMessage != null -> {
            checkBox(IdeBundle.message("updates.settings.checkbox"))
              .bindSelected(settings.state::isCheckNeeded)
            comment(channelSelectionLockedMessage)
          }
          else -> {
            val checkBox = checkBox(IdeBundle.message("updates.settings.checkbox.for"))
              .bindSelected(settings.state::isCheckNeeded)
              .gap(RightGap.SMALL)
            comboBox(channelModel)
              .bindItem(getter = { settings.selectedActiveChannel },
                        setter = { settings.selectedChannelStatus = selectedChannel(it) })
              .enabledIf(checkBox.selected)
          }
        }
      }

      row {
        checkBox(IdeBundle.message("updates.plugins.settings.checkbox"))
          .bindSelected(settings.state::isPluginsCheckNeeded)
      }
      indent {
        row {
          checkBox(IdeBundle.message("updates.plugins.autoupdate.settings.checkbox"))
            .bindSelected(settings.state::isPluginsAutoUpdateEnabled)
            .comment(IdeBundle.message("updates.plugins.autoupdate.settings.comment"))
            .onApply {
              service<PluginAutoUpdateService>().onSettingsChanged()
            }
            .also {
              if (!PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed()) {
                if (settings.isPluginsAutoUpdateEnabled) {
                  settings.isPluginsAutoUpdateEnabled = false
                  service<PluginAutoUpdateService>().onSettingsChanged()
                }
                enabled(false)
                icon(AllIcons.General.Warning).applyToComponent {
                  toolTipText = IdeBundle.message("updates.plugins.autoupdate.settings.prohibited.by.policy.comment")
                  isEnabled = true
                }
              }
              else {
                val connect = ApplicationManager.getApplication().messageBus.connect(disposable!!)
                val component = it.component
                connect.subscribe(PluginAutoUpdateListener.TOPIC, object : PluginAutoUpdateListener {
                  override fun settingsChanged() {
                    component.isSelected = settings.state::isPluginsAutoUpdateEnabled.get()
                  }
                })
              }
            }
        }
      }

      row {
        if (checkNowEnabled) {
          button(IdeBundle.message("updates.settings.check.now.button")) {
            val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myLastCheckedLabel))
            val settingsCopy = UpdateSettings()
            settingsCopy.state.copyFrom(settings.state)
            settingsCopy.isCheckNeeded = true
            settingsCopy.isPluginsCheckNeeded = true
            if (channelSelectionLockedMessage == null) {
              settingsCopy.selectedChannelStatus = selectedChannel(channelModel.selected)
            }
            UpdateChecker.updateAndShowResult(project, settingsCopy)
            updateLastCheckedLabel(settings.lastTimeChecked)
          }
        }

        myLastCheckedLabel = comment("").component
        updateLastCheckedLabel(settings.lastTimeChecked)
      }.topGap(TopGap.SMALL)
        .bottomGap(BottomGap.SMALL)

      if (WhatsNewUtil.isWhatsNewAvailable()) {
        row {
          checkBox(IdeBundle.message("updates.settings.show.editor"))
            .bindSelected(settings.state::isShowWhatsNewEditor)
        }
      }

      if (settings.ignoredBuildNumbers.isNotEmpty()) {
        row {
          myLink = link(IdeBundle.message("updates.settings.ignored")) {
            val text = settings.ignoredBuildNumbers.joinToString("\n")
            val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myLink))
            val result = Messages.showMultilineInputDialog(project, null, IdeBundle.message("updates.settings.ignored.title"), text, null, null)
            if (result != null) {
              settings.ignoredBuildNumbers.clear()
              settings.ignoredBuildNumbers.addAll(result.split('\n'))
            }
          }.component
        }
      }

      UpdateSettingsUIProvider.EP_NAME.forEachExtensionSafe {
        it.init(this)
      }

      // phantom-idea: removed the "We recommend the Toolbox App" promotion (Toolbox App integration dropped)
      var wasEnabled = settings.isCheckNeeded || settings.isPluginsCheckNeeded

      onApply {
        val isEnabled = settings.isCheckNeeded || settings.isPluginsCheckNeeded
        if (isEnabled != wasEnabled) {
          UpdateCheckerService.getInstance().apply {
            if (isEnabled) queueNextCheck() else cancelChecks()
          }
          wasEnabled = isEnabled
        }
      }
    }
  }

  private fun selectedChannel(value: ChannelStatus?): ChannelStatus = value ?: ChannelStatus.RELEASE

  private fun updateLastCheckedLabel(time: Long): Unit =
    if (time > 0) {
      myLastCheckedLabel.text = IdeBundle.message("updates.settings.last.check", DateFormatUtil.formatPrettyDateTime(time))
      myLastCheckedLabel.toolTipText = DateFormatUtil.formatDate(time) + ' ' + DateFormatUtil.formatTimeWithSeconds(time)
    }
    else {
      myLastCheckedLabel.text = IdeBundle.message("updates.settings.last.check", IdeBundle.message("updates.last.check.never"))
      myLastCheckedLabel.toolTipText = null
    }
}
