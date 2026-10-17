// Copyright 2026 Phantom IDEA contributors. Use of this source code is governed by the Apache 2.0 license.
package dev.phantom.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JLabel

/**
 * Keeps project types that moved out of the bundle into phantom-plugins visible in File → New → Project. While the plugin
 * is not installed, the entry explains where the project type went and installs the plugin; once the plugin is
 * installed, its own wizard takes the place and this entry disappears.
 */
@ApiStatus.Internal
abstract class PhantomPluginNewProjectWizard(
  private val pluginId: String,
  private val pluginName: String,
  override val name: String,
  override val icon: Icon,
  private val what: String,
) : GeneratorNewProjectWizard {
  override val id: String = "phantom.promo.$pluginId"
  override val description: String = what

  override fun isEnabled(): Boolean = isMissing()

  private fun isMissing(): Boolean = PluginManagerCore.getPlugin(PluginId.getId(pluginId))?.isEnabled != true

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context).nextStep(::InstallStep)

  private inner class InstallStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {
    override fun setupUI(builder: Panel) {
      val status = JLabel()
      with(builder) {
        row {
          text("$what It comes from the <b>$pluginName</b> plugin, which is not installed.")
        }
        row {
          button("Install $pluginName") {
            installAndEnable(null, setOf(PluginId.getId(pluginId)), showDialog = false, modalityState = ModalityState.any()) {
              status.text = "Installed. Close this dialog and open New Project again to create the project."
            }
          }.validationOnApply { if (isMissing()) error("Install $pluginName to create this project.") else null }
        }
        row { cell(status).applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND } }
      }
    }
  }
}

internal class SpringBootPluginNewProjectWizard : PhantomPluginNewProjectWizard(
  pluginId = "dev.phantom.spring.boot",
  pluginName = "Phantom Spring Boot",
  name = "Spring Boot",
  icon = IconLoader.getIcon("/icons/phantom/springBoot.svg", SpringBootPluginNewProjectWizard::class.java.classLoader),
  what = "Creates a Spring Boot project with Spring Initializr.",
)
