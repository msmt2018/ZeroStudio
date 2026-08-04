package com.itsaky.androidide.preferences

import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import com.itsaky.androidide.R
import com.itsaky.androidide.debugger.connection.DebugConnectionPreferences
import com.itsaky.androidide.fragments.debugger.DeviceConnectionBottomSheet
import kotlinx.parcelize.Parcelize

/** Debugger settings trimmed to JDWP-only controls. */
@Parcelize
class DebuggerPreferences(
    override val key: String = "idepref_debugger",
    override val title: Int = R.string.idepref_debugger_title,
    override val summary: Int? = R.string.idepref_debugger_summary,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceScreen() {
  init {
    addPreference(DeviceConnectionManagerEntry())
    addPreference(DebuggerAutoRetrySwitch())
    addPreference(JdwpListenPortEdit())
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

@Parcelize
private class JdwpListenPortEdit(
    override val key: String = DebugConnectionPreferences.AIDL_LISTEN_PORT,
    override val title: Int = R.string.idepref_debugger_aidl_listen_port_title,
) : EditTextPreference() {
  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    val port = (newValue as? String)?.toIntOrNull() ?: 8700
    DebugConnectionPreferences.aidlListenPort = port
    return true
  }

  override fun onConfigureTextInput(input: com.google.android.material.textfield.TextInputLayout) {
    input.editText?.setText(DebugConnectionPreferences.aidlListenPort.toString())
    input.hint = "8700"
  }
}

@Parcelize
private class DeviceConnectionManagerEntry(
    override val key: String = "idepref_debugger_device_connection",
    override val title: Int = R.string.idepref_debugger_device_connection_title,
    override val summary: Int? = R.string.idepref_debugger_device_connection_summary,
) : SimplePreference() {

  override fun onPreferenceClick(preference: Preference): Boolean {
    val activity = preference.context as? FragmentActivity ?: return false
    DeviceConnectionBottomSheet().show(
        activity.supportFragmentManager,
        "jdwp_connection_bottom_sheet",
    )
    return true
  }
}
