package com.oddlabs.tt.form;

import com.oddlabs.tt.gui.GUIObject;
import com.oddlabs.tt.gui.Panel;
import com.oddlabs.tt.gui.ScrollableGroup;
import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.util.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ResourceBundle;

import static com.oddlabs.tt.gui.Placement.BOTTOM_LEFT;

/**
 * Tab 4 of the MP create-game dialog. Hosts the per-slot SLOT/RACE/TEAM editing UI that a preset stamps onto the lobby.
 * The slot grid is dynamic (the host can change player count from the Advanced tab), so {@link #setRoster} swaps in a
 * freshly built {@link ScrollableGroup} and recompiles the panel layout.
 */
public final class RosterPanel extends Panel {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(RosterPanel.class.getName());

    private @Nullable ScrollableGroup roster;
    // Optional fixed content placed above the roster (e.g. bulk-apply controls like Adaptive AI /
    // Set All AI To / Free For All) - see setHeader()'s own comment for why this exists at all.
    // //added by ikill240c
    private @Nullable GUIObject header; //added by ikill240c

    public RosterPanel() {
        super(i18n("caption"));
        compileCanvas();
    }

    // Registers a fixed, already-placed-at-this-panel's-own-origin GUIObject that the roster
    // should anchor below from now on, instead of sitting at the panel's own origin itself. Added
    // so bulk-apply roster controls (Adaptive AI, Set All AI To, Free For All) could move OFF the
    // standard tab and onto this one instead - that tab's own vertical stack of controls, on top
    // of everything already on it for multiplayer, was tall enough to push this dialog's total
    // height past the visible window, which left the AT_END-anchored OK/confirm button positioned
    // outside the visible area entirely (technically present, just never reachable/visible).
    // Moving these controls to this tab's own (scrollable) roster area instead keeps the standard
    // tab's height back where it was, since this tab was already designed to hold roster-adjacent
    // content and already scrolls internally rather than growing the dialog itself.
    // //added by ikill240c
    public void setHeader(@NonNull GUIObject header) { //added by ikill240c
        this.header = header; //added by ikill240c
    }

    public void setRoster(@NonNull ScrollableGroup new_roster) {
        if (roster != null) {
            removeChild(roster);
        }
        roster = new_roster;
        addChild(new_roster);
        if (header != null) { //added by ikill240c
            new_roster.place(header, BOTTOM_LEFT, Skin.getSkin().getFormData().sectionSpacing()); //added by ikill240c
        } else { //added by ikill240c
            new_roster.place();
        } //added by ikill240c
        compileCanvas();
    }

    private static @NonNull String i18n(@NonNull String key) {
        return Utils.getBundleString(bundle, key);
    }
}

