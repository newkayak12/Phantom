package com.intellij.settingsSync.core.communicator

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.core.SettingsSyncEventListener
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.core.SettingsSyncSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RemoteCommunicatorHolder : SettingsSyncEventListener {
  private val logger = logger<RemoteCommunicatorHolder>()
  const val DEFAULT_PROVIDER_CODE = "jba"
  const val DEFAULT_USER_ID = "jba"

  init {
    SettingsSyncEvents.getInstance().addListener(this)
  }

  // pair userId:remoteCommunicator
  @Volatile
  private var currentCommunicator: SettingsSyncRemoteCommunicator? = null

  fun getRemoteCommunicator(): SettingsSyncRemoteCommunicator? = currentCommunicator ?: run {
    currentCommunicator = createRemoteCommunicator()
    return@run currentCommunicator
  }

  fun getAuthService() = getCurrentProvider()?.authService

  fun isAvailable() = currentCommunicator != null

  fun invalidateCommunicator() {
    currentCommunicator?.let {
      logger.info("Invalidating remote communicator")
      if (it is Disposable) {
        Disposer.dispose(it)
      }
    }
    currentCommunicator = null
  }

  fun getCurrentUserData(): SettingsSyncUserData? {
    val userId = SettingsSyncLocalSettings.getInstance().userId ?: run {
      logger.warn("No current userId. Returning null user data")
      return null
    }
    val provider: SettingsSyncCommunicatorProvider = getCurrentProvider() ?: run {
      logger.warn("No active provider. Returning null user data")
      return null
    }
    return provider.authService.getUserData(userId)
  }

  override fun loginStateChanged() {
    invalidateCommunicator()
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    if (!syncEnabled) {
      invalidateCommunicator()
    }
  }

  fun isPendingAction(): Boolean {
    if (!SettingsSyncSettings.getInstance().syncEnabled)
      return false
    val userId = SettingsSyncLocalSettings.getInstance().userId ?: return false
    return getCurrentProvider()?.authService?.getPendingUserAction(userId) != null
  }

  fun createRemoteCommunicator(provider: SettingsSyncCommunicatorProvider,
                               userId: String,
                               parentDisposable: Disposable? = null): SettingsSyncRemoteCommunicator? {
    val communicator: SettingsSyncRemoteCommunicator = provider.createCommunicator(userId) ?: return null
    if (parentDisposable != null && communicator is Disposable) {
      Disposer.register(parentDisposable, communicator)
    }
    return communicator
  }

  private fun createRemoteCommunicator(): SettingsSyncRemoteCommunicator? {
    val provider: SettingsSyncCommunicatorProvider = getCurrentProvider() ?: run {
      logger.warn("Attempting to create remote communicator without active provider")
      resetLoginData()
      return null
    }
    val userId = SettingsSyncLocalSettings.getInstance().userId ?: run {
      logger.warn("Empty current userId. Communicator will not be created.")
      resetLoginData()
      return null
    }

    val currentUserData: SettingsSyncUserData = provider.authService.getUserData(userId) ?: run {
      logger.warn("Cannot find user data for user '$userId' in provider '${provider.providerCode}. Resetting the data'")
      resetLoginData()
      return null
    }
    val communicator: SettingsSyncRemoteCommunicator = provider.createCommunicator(currentUserData.id) ?: run {
      logger.warn("Provider '${provider.providerCode}' returned empty communicator")
      return null
    }
    return communicator
  }

  private fun resetLoginData() {
    logger.warn("Backup & Sync will be disabled.")
    SettingsSyncSettings.getInstance().syncEnabled = false
    SettingsSyncLocalSettings.getInstance().providerCode = null
    SettingsSyncLocalSettings.getInstance().userId = null
  }

  fun getAvailableProviders(): List<SettingsSyncCommunicatorProvider> {
    val extensionList = arrayListOf<SettingsSyncCommunicatorProvider>()
    extensionList.addAll(getAvailableSyncProviders())
    // phantom-idea: no JetBrains account placeholder (it downloaded the JetBrains "Backup and Sync" and licensing plugins from the
    // JetBrains Marketplace, and its code is gone); only installed providers (Phantom's Git repository provider) are listed
    return extensionList.sortedBy { it.providerCode } // always show providers in the same order
  }

  fun getExternalProviders(): List<SettingsSyncCommunicatorProvider> {
    return getAvailableProviders().filter { it.providerCode != DEFAULT_PROVIDER_CODE }
  }

  fun getDefaultProvider(): SettingsSyncCommunicatorProvider? {
    return getProvider(DEFAULT_PROVIDER_CODE)
  }

  fun getProvider(providerCode: String): SettingsSyncCommunicatorProvider? {
    return getAvailableProviders().find { it.providerCode == providerCode }
  }

  fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    return getAvailableProviders().flatMap { it.authService.getAvailableUserAccounts() }
  }

  private fun getCurrentProvider(): SettingsSyncCommunicatorProvider? {
    val providerCode = SettingsSyncLocalSettings.getInstance().providerCode ?: return null
    return getProvider(providerCode)
  }
}
