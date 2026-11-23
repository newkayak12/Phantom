package com.intellij.settingsSync.core.communicator

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
open class SettingsSyncCommunicatorBean : BaseKeyedLazyInstance<SettingsSyncCommunicatorProvider>() {
  @Attribute("implementation")
  @JvmField
  @RequiredElement
  var implementation: String = ""

  override fun getImplementationClassName(): String = implementation
}

private val PROVIDER_EP: ExtensionPointName<SettingsSyncCommunicatorBean> =
  ExtensionPointName.create("com.intellij.settingsSync.communicatorProvider")

@Suppress("UNCHECKED_CAST")
@TestOnly
@ApiStatus.Internal
fun getSyncProviderPoint(): ExtensionPoint<SettingsSyncCommunicatorBean> {
  return PROVIDER_EP.point
}

@ApiStatus.Internal
fun getAvailableSyncProviders(): List<SettingsSyncCommunicatorProvider> {
  return PROVIDER_EP.extensionList
    .filter {
      // phantom-idea: only providers Phantom bundles (or their updates); was also any plugin developed by JetBrains or by a
      // vendor JetBrains trusts, such as JetBrains' own account provider
      val plugin = it.pluginDescriptor
      plugin.isBundled || PluginManagerCore.isUpdatedBundledPlugin(plugin)
    }
    .map { bean -> bean.instance }
    .filter { it.isAvailable() }
}