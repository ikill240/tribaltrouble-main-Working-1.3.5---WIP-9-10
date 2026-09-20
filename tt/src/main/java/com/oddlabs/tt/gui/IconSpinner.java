package com.oddlabs.tt.gui;

import com.oddlabs.tt.guievent.MouseButtonListener;
import com.oddlabs.tt.input.GameAction;
import com.oddlabs.tt.render.GUIRenderer;
import com.oddlabs.tt.render.Renderer;
import com.oddlabs.tt.viewer.WorldViewer;
import com.oddlabs.tt.util.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.ResourceBundle;

/** A spinner control with an associated icon. */
public abstract class IconSpinner extends GUIObject {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(IconSpinner.class.getName());
    // See IncreaseListener.mousePressed()'s comment for why this isn't Integer.MAX_VALUE.
    // //added by ikill240c
    private static final int MAX_DEPLOY_AMOUNT = 1_000_000; //added by ikill240c

    private @NonNull String i18n(@NonNull String key, @NonNull Object @NonNull... args) {
        return Utils.getBundleString(bundle, key, args);
    }

    private final @NonNull ModeIconQuads icon_quad;
    private final @NonNull String tool_tip;
    private final @Nullable List<@NonNull IconQuad> tool_tip_icons;
    private final @NonNull IconQuad infinite_icon;
    private final @NonNull TextField label;
    private final @NonNull IconButton button_plus;
    private final @NonNull IconButton button_minus;
    private final @NonNull WorldViewer viewer;
    private @Nullable IconDisabler icon_disabler = null;

    private int text_count = 0;

    public IconSpinner(@NonNull WorldViewer viewer, @NonNull ModeIconQuads icon_quad, @NonNull String tool_tip,
            @Nullable List<@NonNull IconQuad> tool_tip_icons,
            @NonNull GameAction action, @NonNull GameAction dec_action) {
        this.icon_quad = icon_quad;
        this.infinite_icon = GUIIcons.getIcons().getInfinite();
        this.tool_tip = tool_tip;
        this.tool_tip_icons = tool_tip_icons;
        this.viewer = viewer;
        setCanFocus(true);
        setDim(icon_quad.quad(ModeIconQuads.Mode.NORMAL).getWidth(), icon_quad.quad(
                ModeIconQuads.Mode.NORMAL).getHeight());

        button_plus = new IconSpinnerButton(Skin.getSkin().getPlusButton(), action,
                () -> i18n("increase", Renderer.getLocalInput().getInputManager().getBindingString(action)),
                this);
        button_plus.setPos(0, 0);
        button_plus.addMouseButtonListener(new IncreaseListener());
        addChild(button_plus);

        button_minus = new IconSpinnerButton(Skin.getSkin().getMinusButton(), dec_action,
                () -> i18n("decrease", Renderer.getLocalInput().getInputManager().getBindingString(dec_action)),
                this);
        button_minus.setPos(button_plus.getWidth(), 0);
        button_minus.addMouseButtonListener(new DecreaseListener());
        addChild(button_minus);

        // Was icon_quad.quad(...).getWidth() - the icon's own width, comfortably fitting 1-2 digit
        // counts but clipping anything from 100 upward (and this game's production caps can now
        // reach into the hundreds). font.getWidth("999") sizes the label to fit any 3-digit count
        // without clipping; centered on the icon (via the x-offset below) since Origin.AT_MIDDLE
        // only centers the text WITHIN whatever box setDim() gives it, not relative to the icon
        // itself. //added by ikill240c
        int icon_width = icon_quad.quad(ModeIconQuads.Mode.NORMAL).getWidth(); //added by ikill240c
        int label_width = Math.max(icon_width, Skin.getSkin().getHeadlineFont().getWidth("999")); //added by ikill240c
        label = new Label("", Skin.getSkin().getHeadlineFont(), label_width, //added by ikill240c
                Origin.AT_MIDDLE);
        label.setPos((icon_width - label_width) / 2, (getHeight() - label.getHeight()) / 2); //added by ikill240c
        addChild(label);
    }

    @Override
    public final void setFocus() {
        viewer.getGUIRoot().getDelegate().setFocus();
    }

    public final void setIconDisabler(IconDisabler icon_disabler) {
        this.icon_disabler = icon_disabler;
    }

    public final void doUpdate() {
        setCount();
        if (icon_disabler != null) {
            boolean no_supply = computeCount() == 0 && getOrderSize() == 0 && icon_disabler.isDisabled();
            setDisabled(no_supply && getDisplayCount() == 0);
            if (!isDisabled()) {
                button_plus.setDisabled(no_supply);
            }
        }
    }

    public abstract int computeCount();

    protected abstract void increase(int amount);

    protected abstract void decrease(int amount);

    protected abstract void release();

    protected abstract int getOrderSize();

    public abstract boolean renderInfinite();

    protected abstract float getProgress();

    protected int getDisplayCount() {
        return computeCount();
    }

    private void setCount() {
        int count = getDisplayCount();
        if (count != text_count) {
            text_count = count;
            label.clear();
            if (text_count != 0) {
                label.append(renderInfinite() ? "∞" : Integer.toString(text_count));
            }
        }
    }

    @Override
    public void appendToolTip(@NonNull ToolTipBox tool_tip_box) {
        tool_tip_box.append(tool_tip);
        tool_tip_box.append(tool_tip_icons);
    }

    public final void shortcutPressed(boolean decrement, boolean batch) {
        if (!isDisabled()) {
            MouseButton mouse_button = batch ? MouseButton.RIGHT : MouseButton.LEFT;

            (decrement ? button_minus : button_plus).mousePressedAll(mouse_button, 0, 0);
        }
    }

    public final void shortcutReleased(boolean decrement, boolean batch) {
        if (!isDisabled()) {
            release();
        }
    }

    @Override
    protected final void renderGeometry(@NonNull GUIRenderer renderer) {
        int x = (getWidth() - icon_quad.quad(ModeIconQuads.Mode.NORMAL).getWidth()) / 2;
        int y = (getHeight() - icon_quad.quad(ModeIconQuads.Mode.NORMAL).getHeight()) / 2;

        ModeIconQuads.Mode skinMode = isDisabled() ? ModeIconQuads.Mode.DISABLED : isHovered() ? ModeIconQuads.Mode.ACTIVE : ModeIconQuads.Mode.NORMAL;

        renderer.drawIcon(icon_quad.quad(skinMode), x, y);

        if (computeCount() > 0) {
            var watchQuad = GUIIcons.getIcons().getWatch(getProgress());
            renderer.drawIcon(watchQuad, getWidth() - watchQuad.getWidth(), getHeight() - watchQuad.getHeight());
        }

        if (renderInfinite()) {
            renderer.drawIcon(infinite_icon, 0, 0);
        }
    }

    @Override
    protected final void mouseReleased(@NonNull MouseButton button, int x, int y) {
    }

    @Override
    protected final void mousePressed(@NonNull MouseButton button, int x, int y) {
    }

    @Override
    protected final void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
    }

    @Override
    protected final void mouseHeld(@NonNull MouseButton button, int x, int y) {
    }

    private final class IncreaseListener implements MouseButtonListener {
        @Override
        public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
        }

        @Override
        public void mouseHeld(@NonNull MouseButton button, int x, int y) {
            mousePressed(button, x, y);
        }

        @Override
        public void mousePressed(@NonNull MouseButton button, int x, int y) {
            // Shift+click deploys/gathers as many as are actually available in one go, rather than
            // the usual +1 (left click) / +10 (right click) increments - increase(int) already
            // clamps its argument down to whatever's really available (units in stock, supply
            // remaining, ship capacity, etc. depending on which DeploySpinner subclass this is).
            // Deliberately NOT Integer.MAX_VALUE: DeploySpinner's ship branch computes
            // "order_size + amount > num_units" before clamping, and order_size (already positive)
            // plus Integer.MAX_VALUE overflows to a negative int in Java's wraparound arithmetic -
            // making that comparison false, skipping the clamp entirely, and leaving amount at
            // Integer.MAX_VALUE for the subsequent order_size += amount, corrupting it. A bounded
            // sentinel far larger than any realistic unit/supply count in this game avoids that
            // failure mode while still always resolving to the true maximum in practice.
            // //added by ikill240c
            if (Renderer.getLocalInput().isShiftDownCurrently()) { //added by ikill240c
                increase(MAX_DEPLOY_AMOUNT); //added by ikill240c
            } else { //added by ikill240c
                increase(button == MouseButton.RIGHT ? 10 : 1);
            } //added by ikill240c
        }

        @Override
        public void mouseReleased(@NonNull MouseButton button, int x, int y) {
            release();
        }
    }

    private final class DecreaseListener implements MouseButtonListener {
        @Override
        public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
        }

        @Override
        public void mouseHeld(@NonNull MouseButton button, int x, int y) {
            mousePressed(button, x, y);
        }

        @Override
        public void mousePressed(@NonNull MouseButton button, int x, int y) {
            // Matches the increase side's shift behavior, but as a fixed amount (100) rather than
            // "however much is available" - decrease has no equivalent "maximum" to resolve to
            // (the floor is always 0), so a large fixed step is the natural counterpart here.
            // 100 is a plain literal (not MAX_DEPLOY_AMOUNT) since decrease()'s clamping doesn't
            // have the same overflow-prone "add before clamp" shape the ship branch of increase()
            // does, but there's no reason to risk it by reusing a million-sized constant for a
            // decrement anyway. //added by ikill240c
            if (Renderer.getLocalInput().isShiftDownCurrently()) { //added by ikill240c
                decrease(100); //added by ikill240c
            } else { //added by ikill240c
                decrease(button == MouseButton.RIGHT ? 10 : 1);
            } //added by ikill240c
        }

        @Override
        public void mouseReleased(@NonNull MouseButton button, int x, int y) {
            release();
        }
    }
}
