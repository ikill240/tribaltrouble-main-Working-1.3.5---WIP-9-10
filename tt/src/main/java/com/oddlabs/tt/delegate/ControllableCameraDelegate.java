package com.oddlabs.tt.delegate;

import com.oddlabs.tt.camera.GameCamera;
import com.oddlabs.tt.gui.MouseButton;
import com.oddlabs.tt.input.GameAction;
import com.oddlabs.tt.input.InputEvent;
import com.oddlabs.tt.input.InputPhase;
import com.oddlabs.tt.render.Renderer;
import com.oddlabs.tt.viewer.WorldViewer;
import org.jspecify.annotations.NonNull;

public abstract class ControllableCameraDelegate extends InGameDelegate {
    private final @NonNull GameCamera game_camera;
    private FirstPersonDelegate first_person_delegate;

    public ControllableCameraDelegate(@NonNull WorldViewer viewer, @NonNull GameCamera game_camera) {
        super(viewer, game_camera);
        this.game_camera = game_camera;
    }

    @Override
    public void handleInput(@NonNull InputEvent event) {
        super.handleInput(event);
        if (event.isConsumed()) return;

        if (event.getPhase() == InputPhase.PRESSED || event.getPhase() == InputPhase.REPEAT) {
            if (event.consumeAction(GameAction.CAMERA_FIRST_PERSON)) {
                if (isCursorInWindow()) {
                    pushFirstPersonDelegate(true);
                }
                event.consume();
                return;
            }
            if (event.consumeAction(GameAction.CAMERA_ZOOM_MODE)) {
                if (isCursorInWindow()) {
                    pushZoomDelegate();
                }
                event.consume();
                return;
            }
        }
    }

    @Override
    public void mousePressed(@NonNull MouseButton button, int x, int y) {
        if (button == MouseButton.MIDDLE) {
            pushFirstPersonDelegate(false);
        }
    }

    @Override
    public void mouseReleased(@NonNull MouseButton button, int x, int y) {
        if (button == MouseButton.MIDDLE && first_person_delegate != null) {
            first_person_delegate.mouseReleased(button, x, y);
        }
    }

    @Override
    public void mouseScrolled(int amount) {
        getCamera().mouseScrolled(amount);
    }

    @Override
    public void mouseMoved(int x, int y) {
        getCamera().mouseMoved(x, y);
    }

    @Override
    public final boolean canScroll() {
        var localInput = Renderer.getLocalInput();
        float scale = getGUIRoot().getGlobalScale();
        mouseMoved(Math.round(localInput.getMouseX() / scale), Math.round(localInput.getMouseY() / scale));
        return getGUIRoot().getModalDelegate() == null;
    }

    @Override
    public void mouseDragged(@NonNull MouseButton button, int x, int y, int relative_x, int relative_y, int absolute_x,
            int absolute_y) {
        if (button == MouseButton.MIDDLE && first_person_delegate != null) {
            first_person_delegate.mouseDragged(button, x, y, relative_x, relative_y, absolute_x, absolute_y);
        }
    }

    // Pivot and zoom modes anchor the cursor to the viewport. If the cursor is outside the window
    // when entering the mode, it can escape its usual confines and is able to drift far from the
    // playable area. Ignore the key until the cursor is inside.
    private boolean isCursorInWindow() {
        return Renderer.getLocalInput().getInputProvider().isCursorInWindow();
    }

    // Opt-in helper for subclasses that represent a transient "waiting for a click" mode
    // (TargetDelegate, PlacingDelegate) - NOT called automatically from this class's own
    // handleInput(), since SelectionDelegate (the base delegate, also a subclass of this one) is
    // the one place that actually OWNS map mode's toggle logic and must keep handling
    // CAMERA_MAP_MODE itself rather than having it intercepted here.
    //
    // CAMERA_MAP_MODE's own toggle logic lives entirely in SelectionDelegate, but
    // GUIRoot.pushDelegate() detaches the previous top delegate from the GUI tree outright (not
    // merely covers it) whenever a new one is pushed - so while a TargetDelegate/PlacingDelegate
    // is active (e.g. waiting for the player to click a move/attack/guard/patrol destination, or
    // a building placement site), SelectionDelegate isn't in the tree at all and there is no path
    // for a Space/Numpad5 press to ever reach its map-mode handler. Previously this meant the key
    // simply did nothing while such a delegate was active, with no visible feedback at all - a
    // player would naturally try clicking next, except the pending click-target mode was still
    // silently armed, so that click got interpreted as "commit to this target/placement" instead
    // of whatever the player actually intended (a plain selection click, a camera adjustment,
    // etc.), moving units or placing a building somewhere far from the intended spot. Popping
    // here at least surfaces the state change and cancels the pending action (matching how these
    // same delegates already cancel on Escape/UI_CANCEL) rather than leaving it silently armed;
    // map mode then works normally on the very next Space press once SelectionDelegate is
    // restored to the tree. //added by ikill240c
    protected final boolean handleMapModeWhileTransient(@NonNull InputEvent event) { //added by ikill240c
        if ((event.getPhase() == InputPhase.PRESSED || event.getPhase() == InputPhase.REPEAT) //added by ikill240c
                && event.consumeAction(GameAction.CAMERA_MAP_MODE)) { //added by ikill240c
            pop(); //added by ikill240c
            event.consume(); //added by ikill240c
            return true; //added by ikill240c
        } //added by ikill240c
        return false; //added by ikill240c
    }

    private void pushFirstPersonDelegate(boolean key_pressed) {
        first_person_delegate = new FirstPersonDelegate(getViewer(), getCamera().getState(), key_pressed);
        getGUIRoot().pushDelegate(first_person_delegate);
    }

    private void pushZoomDelegate() {
        getGUIRoot().pushDelegate(new ZoomDelegate(getViewer(), game_camera));
    }
}
