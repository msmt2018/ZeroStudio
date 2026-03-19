package com.itsaky.androidide.templates.impl.nativeTemplate.imguiActivityProject

internal fun imguiActivitySrcKt(packageName: String, activityClass: String): String = """
package ${data.packageName}

import android.app.NativeActivity
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.LinkedBlockingQueue

class MainActivity : NativeActivity() {
    private val unicodeCharacterQueue = LinkedBlockingQueue<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun showSoftInput() {
        val systemService = getSystemService(INPUT_METHOD_SERVICE)
        if (systemService != null) {
            val inputMethodManager = systemService as InputMethodManager
            inputMethodManager.showSoftInput(window.decorView, 0)
            return
        }
        throw NullPointerException("null cannot be cast to non-null type android.view.inputmethod.InputMethodManager")
    }

    fun hideSoftInput() {
        val systemService = getSystemService(INPUT_METHOD_SERVICE)
        if (systemService != null) {
            val inputMethodManager = systemService as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            return
        }
        throw NullPointerException("null cannot be cast to non-null type android.view.inputmethod.InputMethodManager")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            unicodeCharacterQueue.offer(event.getUnicodeChar(event.metaState))
        }
        return super.dispatchKeyEvent(event)
    }

    fun pollUnicodeChar(): Int {
        return unicodeCharacterQueue.poll() ?: 0
    }
}
""".trimIndent()

internal fun imguiActivitySrcJava(packageName: String, activityClass: String): String = """
package ${data.packageName};

import android.app.NativeActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import java.util.concurrent.LinkedBlockingQueue;

public final class MainActivity extends NativeActivity {
    private LinkedBlockingQueue<Integer> unicodeCharacterQueue = new LinkedBlockingQueue<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public final void showSoftInput() {
        Object systemService = getSystemService("input_method");
        if (systemService != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) systemService;
            inputMethodManager.showSoftInput(getWindow().getDecorView(), 0);
            return;
        }
        throw new NullPointerException("null cannot be cast to non-null type android.view.inputmethod.InputMethodManager");
    }

    public final void hideSoftInput() {
        Object systemService = getSystemService("input_method");
        if (systemService != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) systemService;
            inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            return;
        }
        throw new NullPointerException("null cannot be cast to non-null type android.view.inputmethod.InputMethodManager");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == 0) {
            this.unicodeCharacterQueue.offer(Integer.valueOf(event.getUnicodeChar(event.getMetaState())));
        }
        return super.dispatchKeyEvent(event);
    }

    public final int pollUnicodeChar() {
        Integer poll = this.unicodeCharacterQueue.poll();
        if (poll == null) {
            return 0;
        }
        return poll.intValue();
    }
}
""".trimIndent()