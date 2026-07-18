/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  DebuggerConnection 偏好设置页 (子项目 1)。
 *  把 5 种连接方式的可配置项暴露在设置 -> Debugger。
 *  UI 是 minimal 的,详细表单会在子项目 2~5 各自补全。
 */

package com.itsaky.androidide.preferences

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import com.itsaky.androidide.R
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionPreferences
import com.itsaky.androidide.debugger.connection.ShizukuConfig
import com.itsaky.androidide.fragments.debugger.DeviceConnectionBottomSheet
import com.itsaky.androidide.fragments.shizuku.ShizukuManagerFragment
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
    addPreference(DeviceConnectionManagerEntry())
    addPreference(DebuggerConnectionTypeChoice())
    addPreference(DebuggerAutoRetrySwitch())
    addPreference(AidlSocketOptionsGroup())
    addPreference(ShizukuOptionsGroup())
    addPreference(RootOptionsGroup())
    addPreference(InnetSocksOptionsGroup())
    addPreference(InnetAdbOptionsGroup())
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
    // ALL 列表初始化存在竞态 (见 ConnectionType.fromId 注释),
    // filterNotNull 防御 it 为 null 导致的 NPE
    return ConnectionType.ALL
        .filterNotNull()
        .map { type ->
          PreferenceChoices.Entry(
              label = type.displayName,
              _isChecked = type == current,
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
    addPreference(ShizukuManagerEntry())
    addPreference(ShizukuSubPathChoice())
  }
}

/**
 * 「打开 Shizuku 管理器」入口: 点击后在 IDE 内打开
 * [ShizukuManagerFragment], 显示状态 / 启动 / 授权 / 无线配对。
 */
@Parcelize
private class ShizukuManagerEntry(
    override val key: String = "idepref_debugger_shizuku_manager",
    override val title: Int = R.string.idepref_debugger_shizuku_manager_title,
    override val summary: Int? = R.string.idepref_debugger_shizuku_manager_summary,
) : SimplePreference() {

  override fun onPreferenceClick(preference: Preference): Boolean {
    val ctx = preference.context
    val activity = ctx as? FragmentActivity ?: return false
    // 用 PreferencesActivity 布局里的 fragmentContainer, 保留 toolbar + 返回键导航
    activity.supportFragmentManager
        .beginTransaction()
        .setCustomAnimations(
            android.R.anim.fade_in,
            android.R.anim.fade_out,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        .replace(R.id.fragmentContainer, ShizukuManagerFragment())
        .addToBackStack("shizuku_manager")
        .commit()
    return true
  }
}

/**
 * 「设备连接管理」入口: 点击后弹出 [DeviceConnectionBottomSheet],
 * 以 Shizuku 和 Root 两种 ADB 连接方式为核心, 检测状态 / 请求授权 / 切换活跃通道。
 *
 * 跟 [ShizukuManagerEntry] 的区别:
 *   - ShizukuManagerEntry 打开的是全屏 Fragment, 只管 Shizuku
 *   - DeviceConnectionManagerEntry 弹出 BottomSheet, 同时管 Shizuku + Root, 支持切换
 */
@Parcelize
private class DeviceConnectionManagerEntry(
    override val key: String = "idepref_debugger_device_connection",
    override val title: Int = R.string.idepref_debugger_device_connection_title,
    override val summary: Int? = R.string.idepref_debugger_device_connection_summary,
) : SimplePreference() {

  override fun onPreferenceClick(preference: Preference): Boolean {
    val ctx = preference.context
    val activity = ctx as? FragmentActivity ?: return false
    DeviceConnectionBottomSheet().show(
        activity.supportFragmentManager,
        "device_connection_bottom_sheet",
    )
    return true
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
              _isChecked = p == current,
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
private class InnetSocksOptionsGroup(
    override val key: String = "idepref_debugger_innet_socks",
    override val title: Int = R.string.idepref_debugger_innet_socks_title,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
  init {
    addPreference(InnetSocksHostEdit())
    addPreference(InnetSocksPortEdit())
    addPreference(InnetSocksUserEdit())
    addPreference(InnetSocksPasswordEdit())
    addPreference(InnetSocksConnectTimeoutEdit())
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
private class InnetSocksUserEdit(
    override val key: String = DebugConnectionPreferences.INNET_SOCKS_USER,
    override val title: Int = R.string.idepref_debugger_innet_socks_user_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetSocksUser = (newValue as? String)
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetSocksUser ?: "")
  }
}

@Parcelize
private class InnetSocksPasswordEdit(
    override val key: String = DebugConnectionPreferences.INNET_SOCKS_PASSWORD,
    override val title: Int = R.string.idepref_debugger_innet_socks_password_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetSocksPassword = (newValue as? String)
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetSocksPassword ?: "")
  }
}

@Parcelize
private class InnetSocksConnectTimeoutEdit(
    override val key: String = DebugConnectionPreferences.INNET_SOCKS_CONNECT_TIMEOUT_MS,
    override val title: Int = R.string.idepref_debugger_innet_socks_connect_timeout_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetSocksConnectTimeoutMs = (newValue as? String)?.toLongOrNull() ?: 10_000L
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetSocksConnectTimeoutMs.toString())
  }
}

@Parcelize
private class InnetAdbOptionsGroup(
    override val key: String = "idepref_debugger_innet_adb",
    override val title: Int = R.string.idepref_debugger_innet_adb_title,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
  init {
    addPreference(InnetAdbHostEdit())
    addPreference(InnetAdbPortEdit())
    addPreference(InnetAdbSerialEdit())
    addPreference(InnetAdbConnectTimeoutEdit())
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
private class InnetAdbSerialEdit(
    override val key: String = DebugConnectionPreferences.INNET_ADB_SERIAL,
    override val title: Int = R.string.idepref_debugger_innet_adb_serial_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetAdbSerial = (newValue as? String)
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetAdbSerial ?: "")
  }
}

@Parcelize
private class InnetAdbConnectTimeoutEdit(
    override val key: String = DebugConnectionPreferences.INNET_ADB_CONNECT_TIMEOUT_MS,
    override val title: Int = R.string.idepref_debugger_innet_adb_connect_timeout_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    DebugConnectionPreferences.innetAdbConnectTimeoutMs = (newValue as? String)?.toLongOrNull() ?: 5_000L
    return true
  }
  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.innetAdbConnectTimeoutMs.toString())
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
