package com.oddlabs.tt.delegate;

import com.oddlabs.tt.camera.GameCamera;
import com.oddlabs.tt.camera.MapCamera;
import com.oddlabs.tt.form.InGameChatForm;
import com.oddlabs.tt.gui.ActionButtonPanel;
import com.oddlabs.tt.gui.CursorType;
import com.oddlabs.tt.gui.Label;
import com.oddlabs.tt.gui.MouseButton;
import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.input.GameAction;
import com.oddlabs.tt.input.InputEvent;
import com.oddlabs.tt.input.InputPhase;
import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Action;
import com.oddlabs.tt.model.Army;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.MountUnitContainer; //added by ikill240c
import com.oddlabs.tt.model.LandBuilding;
import com.oddlabs.tt.model.BuildingTemplate;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.model.UnitTemplate;
import com.oddlabs.tt.player.Formation; //added by ikill240c
import com.oddlabs.tt.model.behaviour.IdleController;
import com.oddlabs.tt.render.CompassRenderer;
import com.oddlabs.tt.render.GUIRenderer;
import com.oddlabs.tt.render.Renderer;
import com.oddlabs.tt.util.Utils;
import com.oddlabs.tt.viewer.Notification;
import com.oddlabs.tt.viewer.WorldViewer;
import com.oddlabs.util.Color;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public final class SelectionDelegate extends ControllableCameraDelegate {
    private static final Vector4fc SELECTION_COLOR = Color.argb4v(0xFF_4C_FF_00);
    private static final GameAction[] ARMY_CREATES = new GameAction[]{GameAction.ARMY_CREATE_0, GameAction.ARMY_CREATE_1, GameAction.ARMY_CREATE_2, GameAction.ARMY_CREATE_3, GameAction.ARMY_CREATE_4, GameAction.ARMY_CREATE_5, GameAction.ARMY_CREATE_6, GameAction.ARMY_CREATE_7, GameAction.ARMY_CREATE_8, GameAction.ARMY_CREATE_9,
    };
    private static final GameAction[] ARMY_SELECTS = new GameAction[]{GameAction.ARMY_SELECT_0, GameAction.ARMY_SELECT_1, GameAction.ARMY_SELECT_2, GameAction.ARMY_SELECT_3, GameAction.ARMY_SELECT_4, GameAction.ARMY_SELECT_5, GameAction.ARMY_SELECT_6, GameAction.ARMY_SELECT_7, GameAction.ARMY_SELECT_8, GameAction.ARMY_SELECT_9
    };
    // Parallel arrays: FORMATION_ACTIONS[i] is the keybind that switches to FORMATIONS[i]. //added by ikill240c
    private static final GameAction[] FORMATION_ACTIONS = new GameAction[]{GameAction.FORMATION_SQUARE,
            GameAction.FORMATION_DIAMOND, GameAction.FORMATION_CIRCLE, GameAction.FORMATION_TIGHT,
            GameAction.FORMATION_LOOSE, GameAction.FORMATION_BY_TYPE, GameAction.FORMATION_DEFAULT,
            GameAction.FORMATION_STAR}; //added by ikill240c
    private static final Formation[] FORMATIONS = new Formation[]{Formation.SQUARE, Formation.DIAMOND,
            Formation.CIRCLE, Formation.TIGHT, Formation.LOOSE, Formation.BY_TYPE, Formation.DEFAULT,
            Formation.STAR}; //added by ikill240c
    private final @NonNull InGameChatForm chat_form;
    private final @NonNull Label observer_label;
    private final @NonNull GameCamera game_camera;

    private boolean close_chat_override = false;
    private boolean chat_visible;
    private boolean selection = false;
    private int selection_x1;
    private int selection_y1;
    private int selection_x2;
    private int selection_y2;
    private boolean pick_done = false;
    private boolean map_mode = false;
    private boolean observer = false;
    private int last_idle_peon_name = -1;

    public SelectionDelegate(@NonNull WorldViewer viewer, @NonNull GameCamera camera) {
        super(viewer, camera);
        String observer_mode = Utils.getBundleString(ResourceBundle.getBundle(SelectionDelegate.class.getName()),
                "observer_mode");
        this.observer_label = new Label(observer_mode, Skin.getSkin().getHeadlineFont());
        this.game_camera = (GameCamera) getCamera();
        displayChangedNotify(getGUIRoot().getWidth(), getGUIRoot().getHeight());
        addChild(getViewer().getPanel());
        chat_form = new InGameChatForm(getViewer().getGUIRoot().getInfoPrinter(), getViewer());
        chat_form.addCloseListener(() -> {
            if (Renderer.getLocalInput().getInputManager().isActive(GameAction.GLOBAL_CHAT)) {
                close_chat_override = true;
            }
            chat_visible = false;
        });
        chat_visible = false;
        ((GameCamera) getCamera()).setOwner(this);
    }

    public @NonNull InGameChatForm getChatForm() {
        return chat_form;
    }

    private @NonNull ActionButtonPanel getActionButtonPanel() {
        return getViewer().getPanel();
    }

    public void setObserverMode() {
        observer = true;
        getViewer().getSelection().clearSelection();
        if (!map_mode)
            addChild(observer_label);
    }

    @Override
    public void handleInput(@NonNull InputEvent event) {
        // Prevent base GUIObject from handling UI_ACTIVATE (Space/Return as Click)
        // because we handle Space for Map Mode and Return for Chat.
        event.consumeAction(GameAction.UI_ACTIVATE);

        // Intercept Esc for armory submenu navigation before super reaches InGameDelegate
        if ((event.getPhase() == InputPhase.PRESSED || event.getPhase() == InputPhase.REPEAT)
                && !map_mode && !observer
                && (event.hasAction(GameAction.GLOBAL_MENU) || event.hasAction(GameAction.UI_CANCEL))) {
            if (getActionButtonPanel().tryCloseSubmenu(event)) {
                return;
            }
        }

        super.handleInput(event);
        if (event.isConsumed()) return;

        if (event.getPhase() == InputPhase.PRESSED) {
            if (event.hasActions()) {
                if (event.consumeAction(GameAction.CAMERA_MAP_MODE)) {
                    if (!map_mode) {
                        selection = false;
                        getViewer().getPicker().pickRotate((GameCamera) getCamera());
                        map_mode = true;
                        if (observer)
                            observer_label.remove();
                        else
                            getActionButtonPanel().remove();
                        getCamera().disable();
                        setCamera(new MapCamera(this, game_camera));
                        getCamera().enable();
                    }
                    event.consume();
                    return;
                }

                if (event.consumeAction(GameAction.NOTIFICATION_JUMP)) {
                    if (!observer) {
                        Notification n = getViewer().getNotificationManager().getLatestNotification();
                        if (n != null) {
                            if (getCamera() instanceof GameCamera)
                                getGUIRoot().pushDelegate(new JumpDelegate(getViewer(), (GameCamera) getCamera(),
                                        n.getX(), n.getY()));
                            else if (getCamera() instanceof MapCamera)
                                ((MapCamera) getCamera()).mapGoto(n.getX(), n.getY(), true);
                        }
                    }
                    event.consume();
                    return;
                }

                // Army Shortcuts
                for (int i = 0; i <= 9; i++) {
                    if (event.consumeAction(ARMY_SELECTS[i])) {
                        if (!map_mode && !observer) {
                            boolean selected = getViewer().getSelection().enableShortcutArmy(i);
                            if (selected && event.getClicks() > 1) {
                                var set = getViewer().getSelection().getCurrentSelection().getSet();
                                if (!set.isEmpty()) {
                                    var s = set.iterator().next();
                                    getGUIRoot().pushDelegate(new JumpDelegate(getViewer(), (GameCamera) getCamera(),
                                            s.getPositionX(), s.getPositionY()));
                                }
                            }
                        }
                        event.consume();
                        return;
                    }
                    if (event.consumeAction(ARMY_CREATES[i])) {
                        if (!map_mode && !observer) {
                            getViewer().getSelection().setShortcutArmy(i);
                        }
                        event.consume();
                        return;
                    }
                }

                // Formations - Alt+1 through Alt+8. //added by ikill240c
                for (int i = 0; i < FORMATIONS.length; i++) {
                    if (event.consumeAction(FORMATION_ACTIONS[i])) {
                        if (!map_mode && !observer) {
                            // Was Player.setFormation(Formation) with no selection - a single,
                            // global setting applied to every future order for every unit
                            // regardless of what was actually selected. Now applies only to the
                            // units in the CURRENT SELECTION at the moment this shortcut is
                            // pressed (filtered to Abilities.TARGET, matching how Picker.
                            // pickTarget() itself selects which units in an Army an order actually
                            // applies to), per an explicit request that formations be a property
                            // of the selected units, not a universal player-wide mode.
                            // //added by ikill240c
                            Selectable<?>[] selection = getViewer().getSelection().getCurrentSelection() //added by ikill240c
                                    .filter(Abilities.TARGET); //added by ikill240c
                            getViewer().getLocalPlayer().setFormation(selection, FORMATIONS[i]); //added by ikill240c
                            getGUIRoot().getInfoPrinter().print(Utils.getBundleString(
                                    ResourceBundle.getBundle(SelectionDelegate.class.getName()),
                                    "formation_" + FORMATIONS[i].name().toLowerCase()));
                        }
                        event.consume();
                        return;
                    }
                }

                // Stand Ground - "Y", applies to whatever's currently selected. Goes through
                // PlayerInterface.standGround(...) (network-safe, same as every other unit
                // command here) rather than calling Unit.standGround() directly on each selected
                // unit from this input-handling code. //added by ikill240c
                if (event.consumeAction(GameAction.STAND_GROUND)) { //added by ikill240c
                    if (!map_mode && !observer) { //added by ikill240c
                        var set = getViewer().getSelection().getCurrentSelection().getSet(); //added by ikill240c
                        getViewer().getPeerHub().getPlayerInterface() //added by ikill240c
                                .standGround(set.toArray(new Selectable<?>[0])); //added by ikill240c
                    } //added by ikill240c
                    event.consume(); //added by ikill240c
                    return; //added by ikill240c
                } //added by ikill240c

                if (event.consumeAction(GameAction.GLOBAL_CHAT)) {
                    if (!chat_visible)
                        chat_form.setReceivers(true);
                    event.consume();
                    return;
                }
                if (event.consumeAction(GameAction.GLOBAL_CHAT_TEAM)) {
                    if (!chat_visible)
                        chat_form.setReceivers(false);
                    event.consume();
                    return;
                }
                if (event.consumeAction(GameAction.UNIT_BEACON)) {
                    if (!map_mode && !observer) {
                        getGUIRoot().pushDelegate(new BeaconDelegate(getViewer(), (GameCamera) getCamera()));
                    }
                    event.consume();
                    return;
                }

                if (event.consumeAction(GameAction.UNIT_NEXT_IDLE)) {
                    nextIdlePeon();
                    event.consume();
                    return;
                }

                if (event.consumeAction(GameAction.GAME_SPEED_UP)) {
                    changeGamespeed(1);
                    event.consume();
                    return;
                }

                if (event.consumeAction(GameAction.GAME_SPEED_DOWN)) {
                    changeGamespeed(-1);
                    event.consume();
                    return;
                }

                if (event.hasAction(GameAction.CAMERA_FIRST_PERSON) || event.hasAction(GameAction.CAMERA_ZOOM_MODE)) {
                    if (map_mode) {
                        event.consume(); // Consume in map mode
                        return;
                    }
                    // Otherwise bubble (to super)
                }
            }

            if (map_mode || observer) {
                // Bubble
            } else {
                getActionButtonPanel().handleInput(event);
                if (event.isConsumed()) {
                    return;
                }
            }
        } else if (event.getPhase() == InputPhase.REPEAT) {
            if (event.hasActions()) {
                if (event.consumeAction(GameAction.GAME_SPEED_UP)) {
                    changeGamespeed(1);
                    event.consume();
                    return;
                }
                if (event.consumeAction(GameAction.GAME_SPEED_DOWN)) {
                    changeGamespeed(-1);
                    event.consume();
                    return;
                }
            }

            if (!map_mode && !observer) {
                getActionButtonPanel().handleInput(event);
                if (event.isConsumed()) {
                    return;
                }
            }
        } else if (event.getPhase() == InputPhase.RELEASED) {
            if (event.consumeAction(GameAction.GLOBAL_CHAT) || event.consumeAction(GameAction.GLOBAL_CHAT_TEAM)) {
                if (!close_chat_override) {
                    if (!chat_visible) {
                        addChild(chat_form);
                        chat_form.setPos(GameCamera.SCROLL_BUFFER, GameCamera.SCROLL_BUFFER);
                        chat_form.setFocus();
                        chat_visible = true;
                    }
                } else {
                    close_chat_override = false;
                }
                event.consume();
                return;
            }

            if (!map_mode && !observer) {
                getActionButtonPanel().handleInput(event);
                if (event.isConsumed()) {
                    return;
                }
            }
        }
        super.handleInput(event);
    }

    private void changeGamespeed(int delta) {
        getViewer().getPeerHub().getPlayerInterface().changePreferredGamespeed(delta);
    }

    private void nextIdlePeon() {
        var set = getViewer().getLocalPlayer().getUnits().getSet();

        boolean has_idle_peon = false;
        int lowest_name = Integer.MAX_VALUE;
        Selectable<?> lowest_peon = null;

        boolean has_greater_name = false;
        int lowest_greater_name = Integer.MAX_VALUE;
        Selectable<?> lowest_greater_peon = null;
        for (var s : set) {
            if (s.getOwner() != getViewer().getLocalPlayer())
                continue;
            Abilities abilities = s.getAbilities();
            if ((abilities.hasAbilities(Abilities.BUILD)) && (s.getPrimaryController() instanceof IdleController)) {
                int name = getViewer().getDistributableTable().getName(s);
                if (name < lowest_name) {
                    has_idle_peon = true;
                    lowest_name = name;
                    lowest_peon = s;
                }
                if (name > last_idle_peon_name && name < lowest_greater_name) {
                    has_greater_name = true;
                    lowest_greater_name = name;
                    lowest_greater_peon = s;
                }
            }
        }

        Selectable<?> target = null;
        if (has_greater_name) {
            last_idle_peon_name = lowest_greater_name;
            target = lowest_greater_peon;
        } else if (has_idle_peon) {
            last_idle_peon_name = lowest_name;
            target = lowest_peon;
        }

        if (target != null && getCamera() instanceof GameCamera) {
            getViewer().getSelection().clearSelection();
            getViewer().getSelection().getCurrentSelection().add(target);
            getGUIRoot().pushDelegate(new JumpDelegate(getViewer(), (GameCamera) getCamera(), target.getPositionX(),
                    target.getPositionY()));
        }
    }

    @Override
    protected @NonNull CursorType getCursorType() {
        return map_mode ? CursorType.TARGET : CursorType.NORMAL;
    }

    public void exitMapMode() {
        map_mode = false;
        getCamera().disable();
        // Snap GameCamera's current position to its target so there's no
        // interpolation lag when switching back from MapCamera
        game_camera.getState().snapToTarget();
        setCamera(game_camera);
        getCamera().enable();
        if (observer)
            addChild(observer_label);
        else
            addChild(getActionButtonPanel());

        if (chat_visible) {
            chat_form.remove();
            addChild(chat_form);
            chat_visible = true;
        }
    }

    private void updateSelection(@NonNull List<@NonNull Selectable<UnitTemplate>> friendly_units,
            @NonNull List<@NonNull Ship> friendly_ships, Selectable<BuildingTemplate> friendly_building,
            Selectable<?> enemy) {
        Army current_selection = getViewer().getSelection().getCurrentSelection();
        Selectable<?> first = current_selection.getSet().iterator().next();
        if (first instanceof Building || first.getOwner() != getViewer().getLocalPlayer()) {
            if (first == friendly_building || first == enemy) {
                current_selection.clear();
            }
            return;
        }
        if (first instanceof Ship) {
            toggleSelection(current_selection, friendly_ships);
            return;
        }
        toggleSelection(current_selection, friendly_units);
    }

    private void toggleSelection(@NonNull Army current_selection,
            @NonNull List<? extends @NonNull Selectable<?>> picked) {
        boolean add = false;
        for (Selectable<?> selectable : picked) {
            if (!current_selection.contains(selectable)) {
                add = true;
                break;
            }
        }
        for (Selectable<?> selectable : picked) {
            if (add) {
                if (!current_selection.contains(selectable))
                    current_selection.add(selectable);
            } else {
                current_selection.remove(selectable);
            }
        }
    }

    private void replaceSelection(@NonNull List<Selectable<UnitTemplate>> friendly_units,
            @NonNull List<Ship> friendly_ships, @Nullable Selectable<BuildingTemplate> friendly_building,
            @Nullable Selectable<?> enemy) {
        Army current_selection = getViewer().getSelection().getCurrentSelection();
        current_selection.clear();
        if (!friendly_units.isEmpty()) {
            for (Selectable<?> friendlyUnit : friendly_units) {
                current_selection.add(friendlyUnit);
            }
        } else if (!friendly_ships.isEmpty()) {
            for (Ship ship : friendly_ships) {
                current_selection.add(ship);
            }
        } else if (friendly_building != null) {
            current_selection.add(friendly_building);
        } else if (enemy != null) {
            current_selection.add(enemy);
        }
    }

    @Override
    public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
        if (button == MouseButton.LEFT && !map_mode && !observer) {
            if (selection) {
                selection = false;
                boolean shift_down = Renderer.getLocalInput().isShiftDownCurrently(); //added by ikill240c 2026-09-10 16:45
                // Shift + double click: select every visible unit of the exact same type. //added by ikill240c 2026-09-10 16:45
                boolean select_by_type = shift_down && clicks > 1; //added by ikill240c 2026-09-10 16:45
                Selectable<?>[] picked = getViewer().getPicker().pickBoxed(
                        getViewer().getGUIRoot().getDelegate().getCamera().getState(), selection_x1, selection_y1,
                        selection_x2, selection_y2, clicks, select_by_type);
                // Picker returns a single-element array specifically (not the normal
                // select-all-of-type expansion) when double-clicking a tower with a unit currently
                // mounted in it - see Picker.createSinglePick()'s comment. Ownership is checked
                // here, not in Picker, matching how the existing building-select-all branch also
                // defers ownership checks to this loop rather than Picker itself - you shouldn't
                // be able to unmount an enemy's tower this way. Issues the exact same
                // exitTower(...) command the "Exit Tower" action button already calls (see
                // ActionButtonPanel.java), so this is a network-safe shortcut, not a separate
                // action path. //added by ikill240c
                if (clicks > 1 && picked.length == 1 && picked[0] instanceof Building tower //added by ikill240c
                        && tower.getOwner() == getViewer().getLocalPlayer() //added by ikill240c
                        && tower.getUnitContainer() instanceof MountUnitContainer mount_container //added by ikill240c
                        && mount_container.getUnit() != null) { //added by ikill240c
                    getViewer().getPeerHub().getPlayerInterface().exitTower(tower); //added by ikill240c
                    return; //added by ikill240c
                } //added by ikill240c
                List<Selectable<UnitTemplate>> friendly_units = new ArrayList<>();
                List<Ship> friendly_ships = new ArrayList<>();
                Selectable<BuildingTemplate> friendly_building = null;
                Selectable<?> enemy = null;
                for (Selectable<?> selectable : picked) {
                    if (selectable != null) {
                        if (selectable.getOwner() == getViewer().getLocalPlayer()) {
                            if (selectable instanceof Ship ship) {
                                friendly_ships.add(ship);
                            } else if (selectable instanceof LandBuilding building) {
                                friendly_building = building;
                            } else if (selectable instanceof Unit unit) {
                                friendly_units.add(unit);
                            } else {
                                throw new RuntimeException();
                            }
                        } else if (selectable instanceof LandBuilding ally_building //added by ikill240c
                                && !getViewer().getLocalPlayer().isEnemy(selectable.getOwner())) { //added by ikill240c
                            // An ally-owned building (not the local player's own, but not an enemy's
                            // either) was previously funneled into the plain "enemy" catch-all below,
                            // meaning it could only ever be targeted for attack, never selected as
                            // something to control. Routes it into friendly_building instead - the
                            // same slot the local player's own building uses - so it gets added to
                            // the selection and ActionButtonPanel shows the same building controls
                            // for it. Nothing downstream (SelectionArmy, Picker.pickTarget,
                            // Player.isValid()) checks ownership beyond "not an enemy" any more (see
                            // Player.isValid()'s own comment for the matching engine-side fix) - they
                            // operate on whatever's actually in the selection, so getting an ally's
                            // building into that selection is the entire fix needed here.
                            // //added by ikill240c
                            friendly_building = ally_building; //added by ikill240c
                        } else if (selectable instanceof Ship ally_ship //added by ikill240c
                                && !getViewer().getLocalPlayer().isEnemy(selectable.getOwner())) { //added by ikill240c
                            // Same reasoning as the ally building case above, extended to units
                            // (ships and land units both) per an explicit follow-up request - was
                            // scoped to buildings only at first, but Player.isValid() (the actual
                            // engine-side gate every order-issuing method funnels through) was
                            // relaxed for any non-enemy owner regardless of type, so restricting
                            // selection to buildings only here was leaving that already-safe
                            // capability inaccessible for units specifically. //added by ikill240c
                            friendly_ships.add(ally_ship); //added by ikill240c
                        } else if (selectable instanceof Unit ally_unit //added by ikill240c
                                && !getViewer().getLocalPlayer().isEnemy(selectable.getOwner())) { //added by ikill240c
                            friendly_units.add(ally_unit); //added by ikill240c
                        } else {
                            enemy = selectable;
                        }
                    }
                }
                if (shift_down && !select_by_type //added by ikill240c 2026-09-10 16:45
                        && getViewer().getSelection().getCurrentSelection().size() > 0)
                    updateSelection(friendly_units, friendly_ships, friendly_building, enemy);
                else
                    replaceSelection(friendly_units, friendly_ships, friendly_building, enemy);
                pick_done = true;
            }
        }
    }

    @Override
    public void mouseReleased(@NonNull MouseButton button, int x, int y) {
        if (map_mode) {
            if (button == MouseButton.LEFT) {
                getViewer().getPicker().pickMapGoto(x, y, (MapCamera) getCamera());
            }
        } else if (!observer) {
            if (!pick_done)
                mouseClicked(button, x, y, 1);
            pick_done = false;
            super.mouseReleased(button, x, y);
        } else {
            super.mouseReleased(button, x, y);
        }
    }

    @Override
    public boolean canHoverBehind() {
        return true;
    }

    @Override
    public void mouseDragged(@NonNull MouseButton button, int x, int y, int relative_x, int relative_y, int absolute_x,
            int absolute_y) {
        if (!map_mode) {
            if (!observer) {
                if (button == MouseButton.LEFT) {
                    selection_x2 += relative_x;
                    selection_y2 += relative_y;
                } else {
                    super.mouseDragged(button, x, y, relative_x, relative_y, absolute_x, absolute_y);
                }
            } else {
                super.mouseDragged(button, x, y, relative_x, relative_y, absolute_x, absolute_y);
            }
        }
    }

    @Override
    public void mousePressed(@NonNull MouseButton button, int x, int y) {
        if (!map_mode) {
            if (!observer) {
                var inputManager = Renderer.getLocalInput().getInputManager();
                switch (button) {
                    case LEFT:
                        if (!inputManager.isActive(GameAction.CAMERA_MAP_MODE)) {
                            selection = true;
                        }
                        selection_x1 = x;
                        selection_y1 = y;
                        selection_x2 = x;
                        selection_y2 = y;
                        break;
                    case RIGHT: {
                        Army selection = getViewer().getSelection().getCurrentSelection();
                        // Shift + right click queues the order instead of replacing the current one. //added by ikill240c 2026-09-10 16:45
                        boolean queue_order = Renderer.getLocalInput().isShiftDownCurrently(); //added by ikill240c 2026-09-10 16:45
                        if (selection.size() > 0) {
                            if (selection.containsAbility(Abilities.SAIL)) {
                                getViewer().getPicker().pickSailingTarget(selection,
                                        getViewer().getGUIRoot().getDelegate().getCamera().getState(),
                                        getViewer().getPeerHub().getPlayerInterface(), x, y);
                            } else if (selection.containsAbility(Abilities.TARGET)) {
                                getViewer().getPicker().pickTarget(selection,
                                        getViewer().getGUIRoot().getDelegate().getCamera().getState(),
                                        getViewer().getPeerHub().getPlayerInterface(), x, y, Action.DEFAULT,
                                        queue_order); //added by ikill240c 2026-09-10 16:45
                            }
                        }
                        break;
                    }
                    default:
                        super.mousePressed(button, x, y);
                        break;
                }
            } else {
                super.mousePressed(button, x, y);
            }
        }

    }

    public boolean isSelecting() {
        return selection;
    }

    @Override
    public boolean keyboardBlocked() {
        return chat_visible && chat_form.isActive();
    }

    @Override
    public void render2D(@NonNull GUIRenderer renderer) {
        if (com.oddlabs.tt.global.Settings.getSettings().show_compass && getCamera() != null) {
            float horizAngle = getCamera().getState().getHorizAngle();
            CompassRenderer.render(renderer, Skin.getSkin().getEditFont(),
                    horizAngle, getGUIRoot().getWidth(), getGUIRoot().getHeight());
        }

        if (selection) {
            float minX = Math.min(selection_x1, selection_x2);
            float minY = Math.min(selection_y1, selection_y2);
            float maxX = Math.max(selection_x1, selection_x2);
            float maxY = Math.max(selection_y1, selection_y2);
            float w = maxX - minX;
            float h = maxY - minY;

            float thickness = com.oddlabs.tt.global.Settings.getSettings().high_contrast ? 3.0f : 1.0f;

            // Ensure thickness doesn't exceed half dimensions
            if (thickness > w / 2) thickness = w / 2;
            if (thickness > h / 2) thickness = h / 2;

            renderer.drawColoredQuad(minX, minY, w, thickness, SELECTION_COLOR);
            renderer.drawColoredQuad(minX, maxY - thickness, w, thickness, SELECTION_COLOR);
            renderer.drawColoredQuad(minX, minY + thickness, thickness, h - 2 * thickness, SELECTION_COLOR);
            renderer.drawColoredQuad(maxX - thickness, minY + thickness, thickness, h - 2 * thickness, SELECTION_COLOR);
        }
    }

    @Override
    public void displayChangedNotify(int width, int height) {
        super.displayChangedNotify(width, height);
        observer_label.setPos((width - observer_label.getWidth()) / 2, height - observer_label.getHeight());
    }
}
