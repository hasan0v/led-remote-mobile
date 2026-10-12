package az.kickdl.app.screens;

import android.view.View;

import az.kickdl.app.MainActivity;

/** A lightweight "fragment": builds a view tree, receives lifecycle and inset callbacks. */
public abstract class Screen {
    protected final MainActivity a;
    private View view;

    protected Screen(MainActivity a) { this.a = a; }

    protected abstract View build();

    public final View view() {
        if (view == null) view = build();
        return view;
    }

    public boolean isBuilt() { return view != null; }

    public void onShow() {}

    public void onHide() {}

    public void onDestroy() {}

    public boolean onBack() { return false; }

    /** Status bar height and bottom area (nav bar + system nav) in px. */
    public void onInsets(int top, int bottom) {}

    /** Scroll to top when the active tab is re-selected. */
    public void scrollToTop() {}
}
