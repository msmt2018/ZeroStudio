package android.zero.studio.termux.resources

import androidx.annotation.StringRes

fun @receiver:StringRes Int.getString(): String {
    return Res.application!!.getString(this)
}
