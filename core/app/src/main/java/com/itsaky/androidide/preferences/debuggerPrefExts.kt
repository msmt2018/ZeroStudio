/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  DebuggerConnection 偏好设置页 (子项目 1)。
 *  把 5 种连接方式的可配置项暴露在设置 -> Debugger。
 *  UI 是 minimal 的,详细表单会在子项目 2~5 各自补全。
 */

package com.itsaky.androidide.preferences

import android.content.Context
import androidx.preference.Preference
import com.itsaky.androidide.R
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionPreferences
import com.itsaky.androidide.debugger.connection.ShizukuConfig
import kotlinx.parcelize.Parcelize

/**
 * 顶层 "Debugger" 分组;挂在 IDEPreferences 主菜单下。
 */
@Parcelize
class DebuggerPreferences(
    override val key: String = "idepref_debugger",
    override val title: Int = R.string.idepref_debugger_title,
    override val summary: Int? = R.string.idepref_debugger_summary,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceScreen() {

  init {
    addPreference(DebuggerConnectionTypeChoice())
    addPreference(DebuggerAutoRetrySwitch())
    addPreference(AidlSocketOptionsGroup())
    addPreference(ShizukuOptionsGroup())
    addPreference(RootOptionsGroup())
    addPreference(InnetVmOptionsGroup())
    addPreference(UsbLanOptionsGroup())
  }
}

/** 5 选 1 单选: 决定 DebugConnectionRegistry.create() 用哪个实现。 */
@Parcelize
private class DebuggerConnectionTypeChoice(
    override val key: String = DebugConnectionPreferences.ACTIVE_TYPE,
    override val title: Int = R.string.idepref_debugger_connection_type_title,
    override val summary: Int? = R.string.idepref_debugger_connection_type_summary,
) : SingleChoicePreference() {

  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val current = DebugConnectionPreferences.activeType
    return ConnectionType.ALL
        .map { type ->
          PreferenceChoices.Entry(
              label = type.displayName,
              `is` = type == current,
              data = type,
          )
        }
        .toTypedArray()
  }

  override fun onChoiceConfirmed(
      preference: Preference,
      entry: PreferenceChoices.Entry?,
      position: Int,
  ) {
    super.onChoiceConfirmed(preference, entry, position)
    val type = entry?.data as? ConnectionType ?: ConnectionType.AidlSocket
    DebugConnectionPreferences.activeType = type
    updatePreference(preference)
  }

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updatePreference(it) }
  }

  private fun updatePreference(preference: Preference) {
    val current = DebugConnectionPreferences.activeType
    preference.summary = preference.context.getString(
        R.string.idepref_debugger_connection_type_summary
    ) + "\n当前: " + current.displayName
  }
}

@Parcelize
private class DebuggerAutoRetrySwitch(
    override val key: String = DebugConnectionPreferences.AUTO_RETRY,
    override val title: Int = R.string.idepref_debugger_auto_retry_title,
    override val summary: Int? = R.string.idepref_debugger_auto_retry_summary,
) :
    SwitchPreference(
        setValue = DebugConnectionPreferences::autoRetry::set,
        getValue = DebugConnectionPreferences::autoRetry::get,
    )

// ---- 各方案子分组(子项目 2~5 补全) ----

@Parcelize
private class AidlSocketOptionsGroup(
    override val key: String = "idepref_debugger_aidl",
    override val title: Int = ConnectionType.AidlSocket.displayName.let { _ -> R.string.idepref_debugger_aidl_listen_port_title },
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
  init {
    addPreference(AidlListenPortEdit())
  }
}

@Parcelize
private class AidlListenPortEdit(
    override val key: String = DebugConnectionPreferences.AIDL_LISTEN_PORT,
    override val title: Int = R.string.idepref_debugger_aidl_listen_port_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    val v = (newValue as? String)?.toIntOrNull() ?: 0
    DebugConnectionPreferences.aidlListenPort = v
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.aidlListenPort.toString())
    input.hint = "0"
  }
}

@Parcelize
private class ShizukuOptionsGroup(
    override val key: String = "idepref_debugger_shizuku",
    override val title: Int = ConnectionType.Shizuku.displayName.let { _ -> R.string.idepref_debugger_shizuku_sub_path_title },
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
  init {
    addPreference(ShizukuSubPathChoice())
  }
}

@Parcelize
private class ShizukuSubPathChoice(
    override val key: String = DebugConnectionPreferences.SHIZUKU_SUB_PATH,
    override val title: Int = R.string.idepref_debugger_shizuku_sub_path_title,
    override val summary: Int? = R.string.idepref_debugger_shizuku_sub_path_summary,
) : SingleChoicePreference() {

  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val current = DebugConnectionPreferences.shizukuSubPath
    return ShizukuConfig.SubPath.values()
        .map { p ->
          PreferenceChoices.Entry(
              label = p.name,
              `is` = p == current,
              data = p,
          )
        }
        .toTypedArray()
  }

  override fun onChoiceConfirmed(
      preference: Preference,
      entry: PreferenceChoices.Entry?,
      position: Int,
  ) {
    super.onChoiceConfirmed(preference, entry, position)
    val v = entry?.data as? ShizukuConfig.SubPath ?: ShizukuConfig.SubPath.Auto
    DebugConnectionPreferences.shizukuSubPath = v
  }
}

@Parcelize
private class RootOptionsGroup(
    override val key: String = "idepref_debugger_root",
    override val title: Int = ConnectionType.Root.displayName.let { _ -> R.string.idepref_debugger_root_su_bin_title },
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
  init {
    addPreference(RootSuBinEdit())
  }
}

@Parcelize
private class RootSuBinEdit(
    override val key: String = DebugConnectionPreferences.ROOT_SU_BIN,
    override val title: Int = R.string.idepref_debugger_root_su_bin_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.rootSuBin = (newValue as? String) ?: "/system/bin/su"
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.rootSuBin)
    input.hint = "/system/bin/su"
  }
}

@Parcelize
private class InnetVmOptionsGroup(
    override val key: String = "idepref_debugger_innet",
    override val title: Int = ConnectionType.InnetVm.displayName.let { _ -> R.string.idepref_debugger_innet_socks_host_title },
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
  init {
    addPreference(InnetSocksHostEdit())
    addPreference(InnetSocksPortEdit())
    addPreference(InnetAdbHostEdit())
    addPreference(InnetAdbPortEdit())
  }
}

@Parcelize
private class InnetSocksHostEdit(
    override val key: String = DebugConnectionPreferences.INNET_SOCKS_HOST,
    override val title: Int = R.string.idepref_debugger_innet_socks_host_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetSocksHost = (newValue as? String) ?: "127.0.0.1"
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetSocksHost)
  }
}

@Parcelize
private class InnetSocksPortEdit(
    override val key: String = DebugConnectionPreferences.INNET_SOCKS_PORT,
    override val title: Int = R.string.idepref_debugger_innet_socks_port_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetSocksPort = (newValue as? String)?.toIntOrNull() ?: 1080
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetSocksPort.toString())
  }
}

@Parcelize
private class InnetAdbHostEdit(
    override val key: String = DebugConnectionPreferences.INNET_ADB_HOST,
    override val title: Int = R.string.idepref_debugger_innet_adb_host_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetAdbHost = (newValue as? String) ?: "127.0.0.1"
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetAdbHost)
  }
}

@Parcelize
private class InnetAdbPortEdit(
    override val key: String = DebugConnectionPreferences.INNET_ADB_PORT,
    override val title: Int = R.string.idepref_debugger_innet_adb_port_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetAdbPort = (newValue as? String)?.toIntOrNull() ?: 5555
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetAdbPort.toString())
  }
}

@Parcelize
private class UsbLanOptionsGroup(
    override val key: String = "idepref_debugger_usb",
    override val title: Int = ConnectionType.UsbLan.displayName.let { _ -> R.string.idepref_debugger_usb_adb_host_title },
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
  init {
    addPreference(UsbAdbHostEdit())
    addPreference(UsbAdbPortEdit())
    addPreference(UsbAdbSerialEdit())
  }
}

@Parcelize
private class UsbAdbHostEdit(
    override val key: String = DebugConnectionPreferences.USB_ADB_HOST,
    override val title: Int = R.string.idepref_debugger_usb_adb_host_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.usbAdbHost = (newValue as? String) ?: "127.0.0.1"
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.usbAdbHost)
  }
}

@Parcelize
private class UsbAdbPortEdit(
    override val key: String = DebugConnectionPreferences.USB_ADB_PORT,
    override val title: Int = R.string.idepref_debugger_usb_adb_port_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.usbAdbPort = (newValue as? String)?.toIntOrNull() ?: 5037
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.usbAdbPort.toString())
  }
}

@Parcelize
private class UsbAdbSerialEdit(
    override val key: String = DebugConnectionPreferences.USB_ADB_SERIAL,
    override val title: Int = R.string.idepref_debugger_usb_adb_serial_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.usbAdbSerial = (newValue as? String)
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.usbAdbSerial ?: "")
  }
}
