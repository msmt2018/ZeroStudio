package android.zero.studio.termux.io;

public class KeyboardShortcut {

    public final int codePoint;
    public final int shortcutAction;

    public KeyboardShortcut(int codePoint, int shortcutAction) {
        this.codePoint = codePoint;
        this.shortcutAction = shortcutAction;
    }

}
