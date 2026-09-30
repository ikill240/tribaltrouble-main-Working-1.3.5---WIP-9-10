package com.oddlabs.tt.gui;

import com.oddlabs.tt.guievent.MouseButtonListener;
import com.oddlabs.tt.render.GUIRenderer;
import org.jspecify.annotations.NonNull;

public final class PanelGroup extends GUIObject {
    private final Group focus_group = new Group();
    private final @NonNull PanelBox box;
    private final @NonNull Panel @NonNull [] panels;

    private int selected;

    public PanelGroup(@NonNull Panel... panels) {
        this(0, panels);
    }

    public PanelGroup(int selected, @NonNull Panel @NonNull... panels) {
        assert selected < panels.length && panels.length > 0 : "Invalid index selected.";
        this.panels = panels;

        int tab_height = panels[0].getTab().getHeight();
        int width = 0;
        int height = 0;
        for (Panel panel : panels) {
            if (width < panel.getWidth()) {
                width = panel.getWidth();
            }
            if (height < panel.getHeight()) {
                height = panel.getHeight();
            }
        }
        // Was just the widest panel's own content width - fine as long as the tab row itself (laid
        // out left to right below, one tab's width added per panel) fits within that. It doesn't
        // once enough tabs are chained: this project's terrain menu has grown to 6 tabs for
        // multiplayer (mode & presets, standard, advanced, custom, economy & AI, roster), and their
        // combined tab-row width can exceed any single panel's own content width. When that
        // happens, panels/tabs past that point get positioned beyond the group's own width - not
        // rendered as part of it, not clickable, simply gone, which reads as "missing settings and
        // tabs" even though the panels themselves are fully built. Widened to the tab row's own
        // total width too, whichever is larger, so every tab actually fits inside the group instead
        // of silently running off the edge as more get added. //added by ikill240c
        int tab_row_width = Skin.getSkin().getPanelData().leftTabOffset(); //added by ikill240c
        for (Panel panel : panels) { //added by ikill240c
            tab_row_width += panel.getTab().getWidth(); //added by ikill240c
        } //added by ikill240c
        if (width < tab_row_width) { //added by ikill240c
            width = tab_row_width; //added by ikill240c
        } //added by ikill240c
        int total_height = height + tab_height;
        setDim(width, total_height);
        int x = Skin.getSkin().getPanelData().leftTabOffset();
        int y = height;
        for (int i = 0; i < panels.length; i++) {
            panels[i].setPos((width - panels[i].getWidth()) / 2,
                    Skin.getSkin().getPanelData().bottomTabOffset() + (height - panels[i].getHeight()) / 2);
            panels[i].getTab().setPos(x, y);
            x += panels[i].getTab().getWidth();
            panels[i].getTab().addMouseButtonListener(new TabListener(i));
        }
        box = new PanelBox(width,
                total_height - panels[0].getTab().getHeight() + Skin.getSkin().getPanelData().bottomTabOffset());

        focus_group.setDim(width, total_height);
        focus_group.setPos(0, 0);
        addChild(focus_group);
        setCanFocus(true);
        selectPanel(selected);
    }

    @Override
    public void setFocus(@NonNull FocusDirection direction) {
        focus_group.setGroupFocus(direction);
    }

    public void cyclePanel(@NonNull FocusDirection dir) {
        int intDir = (dir == FocusDirection.BACKWARD) ? -1 : 1;
        int next = (selected + intDir + panels.length) % panels.length;
        selectPanel(next);
    }

    private void selectPanel(int index) {
        focus_group.clearChildren();
        for (int i = 0; i < panels.length; i++) {
            if (i != index) {
                focus_group.addChild(panels[i].getTab());
                panels[i].getTab().select(false);
            }
        }
        focus_group.addChild(box);
        focus_group.addChild(panels[index].getTab());
        panels[index].getTab().select(true);
        focus_group.addChild(panels[index]);
        selected = index;
        panels[index].setFocus();
    }

    private final class PanelBox extends GUIObject {
        public PanelBox(int width, int height) {
            setDim(width, height);
            setPos(0, 0);
        }

        @Override
        protected void renderGeometry(@NonNull GUIRenderer renderer) {
            Box panelBox = Skin.getSkin().getPanelData().box();
            panelBox.render(renderer, 0f, 0f, getWidth(), getHeight(), panels[selected].getTab().getRenderState());
        }
    }

    private final class TabListener implements MouseButtonListener {
        private final int index;

        public TabListener(int index) {
            this.index = index;
        }

        @Override
        public void mousePressed(@NonNull MouseButton button, int x, int y) {
            selectPanel(index);
        }

        @Override
        public void mouseReleased(@NonNull MouseButton button, int x, int y) {
        }

        @Override
        public void mouseHeld(@NonNull MouseButton button, int x, int y) {
        }

        @Override
        public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
        }
    }
}
