/*
 * Copyright 2020 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor;

import pixelitor.gui.AutoZoom;
import pixelitor.gui.FramesUI;
import pixelitor.gui.ImageArea;
import pixelitor.gui.PixelitorWindow;
import pixelitor.gui.View;
import pixelitor.gui.utils.Dialogs;
import pixelitor.history.History;
import pixelitor.io.IOThread;
import pixelitor.io.OpenSave;
import pixelitor.layers.Drawable;
import pixelitor.layers.ImageLayer;
import pixelitor.layers.Layer;
import pixelitor.layers.MaskViewMode;
import pixelitor.layers.TextLayer;
import pixelitor.menus.MenuAction;
import pixelitor.menus.file.RecentFilesMenu;
import pixelitor.menus.view.ZoomLevel;
import pixelitor.menus.view.ZoomMenu;
import pixelitor.selection.Selection;
import pixelitor.selection.SelectionActions;
import pixelitor.tools.Tools;
import pixelitor.tools.pen.Path;
import pixelitor.utils.Messages;
import pixelitor.utils.Rnd;
import pixelitor.utils.ViewActivationListener;

import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.swing.JOptionPane.CANCEL_OPTION;
import static javax.swing.JOptionPane.CLOSED_OPTION;
import static javax.swing.JOptionPane.NO_OPTION;
import static javax.swing.JOptionPane.YES_OPTION;
import static pixelitor.gui.ImageArea.Mode.FRAMES;

/**
 * Static methods related to the list of opened images
 */
public class OpenImages {
    private static final List<View> views = new ArrayList<>();
    private static View activeView;
    private static final List<ViewActivationListener> activationListeners
            = new ArrayList<>();

    public static final MenuAction CLOSE_ALL_ACTION = new MenuAction("Close All") {
        @Override
        public void onClick() {
            warnAndCloseAll();
        }
    };

    public static final MenuAction CLOSE_ACTIVE_ACTION = new MenuAction("Close") {
        @Override
        public void onClick() {
            warnAndCloseActive();
        }
    };

    public static final MenuAction CLOSE_UNMODIFIED_ACTION = new MenuAction("Close Unmodified") {
        @Override
        public void onClick() {
            warnAndCloseUnmodified();
        }
    };

    private OpenImages() {
    }

    public static List<Composition> getUnsavedComps() {
        return views.stream()
                .map(View::getComp)
                .filter(Composition::isDirty)
                .collect(toList());
    }

    public static List<View> getViews() {
        return views;
    }

    public static View getActiveView() {
        return activeView;
    }

    public static Composition getActiveComp() {
        if (activeView != null) {
            return activeView.getComp();
        }

        // there is no open image
        return null;
    }

    public static Optional<Composition> getActiveCompOpt() {
        return Optional.ofNullable(getActiveComp());
    }

    public static boolean checkActiveComp(Predicate<Composition> check, boolean ifNoImage) {
        if (activeView != null) {
            return check.test(activeView.getComp());
        }

        return ifNoImage;
    }

    public static <T> T fromActiveComp(Function<Composition, T> function) {
        if (activeView != null) {
            return function.apply(activeView.getComp());
        }

        // there is no open image
        return null;
    }

    public static Path getActivePath() {
        if (activeView != null) {
            return activeView.getComp().getActivePath();
        }

        // there is no open image
        return null;
    }

    // to be used in assertions
    public static boolean activePathIs(Path path) {
        if (activeView != null) {
            Path activePath = activeView.getComp().getActivePath();
            if (activePath == path) {
                return true;
            } else {
                throw new AssertionError(format("active path = %s, path = %s",
                        activePath, path));
            }
        }

        // no open image
        if (path == null) {
            return true;
        } else {
            throw new AssertionError("there is no open image");
        }
    }

    public static void setActivePath(Path path) {
        if (activeView != null) {
            activeView.getComp().setActivePath(path);
        }
    }

    public static Selection getActiveSelection() {
        if (activeView != null) {
            return activeView.getComp().getSelection();
        }

        // there is no open image
        return null;
    }

    public static Optional<Composition> findCompositionByName(String name) {
        return views.stream()
                .map(View::getComp)
                .filter(c -> c.getName().equals(name))
                .findFirst();
    }

    public static Layer getActiveLayer() {
        if (activeView != null) {
            return activeView.getComp().getActiveLayer();
        }

        return null;
    }

    public static Drawable getActiveDrawable() {
        if (activeView != null) {
            var comp = activeView.getComp();
            return comp.getActiveDrawable();
        }

        return null;
    }

    public static Drawable getActiveDrawableOrThrow() {
        if (activeView != null) {
            return activeView.getComp().getActiveDrawableOrThrow();
        }

        throw new IllegalStateException("no active image");
    }

    public static int getNumOpenImages() {
        return views.size();
    }

    public static BufferedImage getActiveCompositeImage() {
        return fromActiveComp(Composition::getCompositeImage);
    }

    public static void imageClosed(View view) {
        views.remove(view);
        if (views.isEmpty()) {
            onAllImagesClosed();
        }
        activateAViewIfNoneIs();
    }

    private static void activateAViewIfNoneIs() {
        if (!views.isEmpty()) {
            boolean activeFound = false;
            for (View view : views) {
                if (view == activeView) {
                    activeFound = true;
                    break;
                }
            }

            if (!activeFound) {
                setActiveView(views.get(0), true);
            }
        }
    }

    public static void setActiveView(View view, boolean activate) {
        if (view == activeView) {
            return;
        }

        if (activate) {
            if (view == null) {
                throw new IllegalStateException("Can't activate null view");
            }
            if(!view.isMock()) {
                ImageArea.activateView(view);
            }
        }
        activeView = view;
    }

    /**
     * Changes the cursor for all images
     */
    public static void setCursorForAll(Cursor cursor) {
        for (View view : views) {
            view.setCursor(cursor);
        }
    }

    public static void addActivationListener(ViewActivationListener listener) {
        activationListeners.add(listener);
    }

    public static void removeActivationListener(ViewActivationListener listener) {
        activationListeners.remove(listener);
    }

    private static void onAllImagesClosed() {
        setActiveView(null, false);
        activationListeners.forEach(ViewActivationListener::allViewsClosed);
        History.onAllImagesClosed();
        SelectionActions.setEnabled(false, null);

        PixelitorWindow.getInstance().updateTitle(null);
        FramesUI.resetCascadeIndex();
    }

    /**
     * Another view became active
     */
    public static void viewActivated(View view) {
        if (view == activeView) {
            return;
        }

        View oldView = activeView;

        var comp = view.getComp();
        setActiveView(view, false);
        SelectionActions.setEnabled(comp.hasSelection(), comp);
        view.activateUI(true);

        for (ViewActivationListener listener : activationListeners) {
            listener.viewActivated(oldView, view);
        }

        Layer layer = comp.getActiveLayer();
        Layers.activeLayerChanged(layer, true);
        Tools.setupMaskEditing(layer.isMaskEditing());

        ZoomMenu.zoomChanged(view.getZoomLevel());

        Canvas.activeCanvasImSizeChanged(comp.getCanvas());
        PixelitorWindow.getInstance().updateTitle(comp);
    }

    public static void repaintActive() {
        if (activeView != null) {
            activeView.repaint();
        }
    }

    public static void repaintAll() {
        for (View view : views) {
            view.repaint();
        }
    }

    public static void repaintVisible() {
        if (ImageArea.currentModeIs(FRAMES)) {
            repaintAll();
        } else {
            activeView.repaint();
        }
    }

    public static void fitActiveTo(AutoZoom autoZoom) {
        if (activeView != null) {
            activeView.zoomToFit(autoZoom);
        }
    }

    public static void reloadActiveFromFileAsync() {
        // save a reference to the active view, because this will take
        // a while and another view might become active in the meantime
        View view = activeView;

        var comp = view.getComp();
        File file = comp.getFile();
        if (file == null) {
            String msg = format(
                    "<html>The image <b>%s</b> can't be reloaded because it wasn't yet saved.",
                    comp.getName());
            Messages.showError("No file", msg);
            return;
        }

        String path = file.getAbsolutePath();
        if (!file.exists()) {
            String msg = format(
                    "<html>The image <b>%s</b> can't be reloaded because the file" +
                            "<br><b>%s</b>" +
                            "<br>doesn't exist anymore.",
                    comp.getName(), path);
            Messages.showError("File not found", msg);
            return;
        }

        // prevents starting a new reload on the EDT while an asynchronous
        // reload is already scheduled or running on the IO thread
        if (IOThread.isProcessing(path)) {
            return;
        }
        IOThread.markReadProcessing(path);

        OpenSave.loadCompAsync(file)
                .thenAcceptAsync(view::replaceJustReloadedComp,
                        EventQueue::invokeLater)
                .whenComplete((v, e) -> IOThread.readingFinishedFor(path))
                .exceptionally(Messages::showExceptionOnEDT);
    }

    public static void duplicateActive() {
        assert activeView != null;

        Composition activeComp = activeView.getComp();
        Composition newComp = activeComp.createCopy(false, true);

        addAsNewComp(newComp);
    }

    public static void onActiveView(Consumer<View> action) {
        if (activeView != null) {
            action.accept(activeView);
        }
    }

    public static void forEachView(Consumer<View> action) {
        for (View view : views) {
            action.accept(view);
        }
    }

    public static void onActiveComp(Consumer<Composition> action) {
        if (activeView != null) {
            var comp = activeView.getComp();
            action.accept(comp);
        }
    }

    public static void onActiveSelection(Consumer<Selection> action) {
        if (activeView != null) {
            activeView.getComp().onSelection(action);
        }
    }

    public static void onActiveLayer(Consumer<Layer> action) {
        if (activeView != null) {
            Layer activeLayer = activeView.getComp().getActiveLayer();
            action.accept(activeLayer);
        }
    }

    public static void onActiveImageLayer(Consumer<ImageLayer> action) {
        if (activeView != null) {
            ImageLayer activeLayer = (ImageLayer) activeView.getComp().getActiveLayer();
            action.accept(activeLayer);
        }
    }

    public static void onActiveTextLayer(Consumer<TextLayer> action) {
        if (activeView != null) {
            TextLayer activeLayer = (TextLayer) activeView.getComp().getActiveLayer();
            action.accept(activeLayer);
        }
    }

    public static <T> T fromActiveTextLayer(Function<TextLayer, T> function) {
        if (activeView != null) {
            TextLayer activeLayer = (TextLayer) activeView.getComp().getActiveLayer();
            return function.apply(activeLayer);
        }
        return null;
    }

    public static void onActiveDrawable(Consumer<Drawable> action) {
        Drawable dr = getActiveDrawable();
        if (dr != null) {
            action.accept(dr);
        }
    }

    public static Composition addJustLoadedComp(Composition comp, File file) {
        if (comp != null) { // there was no decoding problem
            addAsNewComp(comp);
            RecentFilesMenu.getInstance().addFile(file);
            Messages.showInStatusBar("<html><b>" + file.getName() + "</b> was opened.");
        }
        return comp;
    }

    public static void addAsNewComp(BufferedImage image, File file, String name) {
        var comp = Composition.fromImage(image, file, name);
        addAsNewComp(comp);
    }

    public static void addAsNewComp(Composition comp) {
        try {
            assert comp.getView() == null : "already has a view";

            View view = new View(comp);
            comp.addAllLayersToGUI();
            view.setCursor(Tools.getCurrent().getStartingCursor());
            views.add(view);
            MaskViewMode.NORMAL.activate(view, comp.getActiveLayer(), "image added");
            ImageArea.addNewView(view);
            setActiveView(view, false);
        } catch (Exception e) {
            Messages.showException(e);
        }
    }

    public static void activateRandomView() {
        View view = Rnd.chooseFrom(views);
        if (view != activeView) {
            setActiveView(view, true);
        }
    }

    public static void assertNumOpenImagesIs(int expected) {
        int numOpenImages = getNumOpenImages();
        if (numOpenImages == expected) {
            return;
        }

        throw new AssertionError(format(
                "Expected %d images, found %d (%s)",
                expected, numOpenImages, getOpenImageNamesAsString()));
    }

    public static void assertNumOpenImagesIsAtLeast(int minimum) {
        int numOpenImages = getNumOpenImages();
        if (numOpenImages >= minimum) {
            return;
        }
        throw new AssertionError(format(
                "Expected at least %d images, found %d (%s)",
                minimum, numOpenImages, getOpenImageNamesAsString()));
    }

    public static void assertZoomOfActiveIs(ZoomLevel expected) {
        if (activeView == null) {
            throw new AssertionError("no active image");
        }
        ZoomLevel actual = activeView.getZoomLevel();
        if (actual != expected) {
            throw new AssertionError("expected = " + expected +
                    ", found = " + actual);
        }
    }

    private static String getOpenImageNamesAsString() {
        return views.stream()
                .map(View::getName)
                .collect(joining(", ", "[", "]"));
    }

    private static void warnAndCloseActive() {
        warnAndClose(activeView);
    }

    public static void warnAndClose(View view) {
        try {
            var comp = view.getComp();
            if (comp.isDirty()) {
                int answer = Dialogs.showCloseWarningDialog(comp.getName());

                if (answer == YES_OPTION) { // "Save"
                    boolean fileSaved = OpenSave.save(comp, false);
                    if (fileSaved) {
                        view.close();
                    }
                } else if (answer == NO_OPTION) { // "Don't Save"
                    view.close();
                } else if (answer == CANCEL_OPTION) {
                    // do nothing
                } else if (answer == CLOSED_OPTION) { // dialog closed by pressing X
                    // do nothing
                } else {
                    throw new IllegalStateException("answer = " + answer);
                }
            } else {
                view.close();
            }
        } catch (Exception ex) {
            Messages.showException(ex);
        }
    }

    private static void warnAndCloseAll() {
        warnAndCloseAllIf(view -> true);
    }

    public static void warnAndCloseAllBut(View selected) {
        warnAndCloseAllIf(view -> view != selected);
    }

    private static void warnAndCloseUnmodified() {
        warnAndCloseAllIf(view -> !view.getComp().isDirty());
    }

    private static void warnAndCloseAllIf(Predicate<View> condition) {
        // make a copy because items will be removed from the original while iterating
        Iterable<View> tmpCopy = new ArrayList<>(views);
        for (View view : tmpCopy) {
            if (condition.test(view)) {
                warnAndClose(view);
            }
        }
    }

    public static boolean isAnyPixelGridAllowed() {
        for (View view : views) {
            if (view.allowPixelGrid()) {
                return true;
            }
        }
        return false;
    }

}
