/*
 * Copyright 2018 Laszlo Balazs-Csiki and Contributors
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

package pixelitor.layers;

import org.jdesktop.swingx.painter.AbstractLayoutPainter.HorizontalAlignment;
import org.jdesktop.swingx.painter.AbstractLayoutPainter.VerticalAlignment;
import pixelitor.Composition;
import pixelitor.filters.comp.Flip;
import pixelitor.filters.comp.Rotate;
import pixelitor.filters.painters.TextAdjustmentsPanel;
import pixelitor.filters.painters.TextSettings;
import pixelitor.filters.painters.TranslatedTextPainter;
import pixelitor.gui.ImageComponents;
import pixelitor.gui.PixelitorWindow;
import pixelitor.gui.utils.OKCancelDialog;
import pixelitor.history.ContentLayerMoveEdit;
import pixelitor.history.History;
import pixelitor.history.NewLayerEdit;
import pixelitor.history.TextLayerChangeEdit;
import pixelitor.history.TextLayerRasterizeEdit;
import pixelitor.utils.ImageUtils;
import pixelitor.utils.Utils;
import pixelitor.utils.test.RandomGUITest;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;

import static org.jdesktop.swingx.painter.AbstractLayoutPainter.HorizontalAlignment.CENTER;
import static org.jdesktop.swingx.painter.AbstractLayoutPainter.HorizontalAlignment.LEFT;
import static org.jdesktop.swingx.painter.AbstractLayoutPainter.VerticalAlignment.TOP;

/**
 * A text layer
 */
public class TextLayer extends ContentLayer {
    private static final long serialVersionUID = 2L;
    private final TranslatedTextPainter painter;
    private TextSettings settings;

    public TextLayer(Composition comp) {
        this(comp, "");
    }

    public TextLayer(Composition comp, String name) {
        super(comp, name, null);

        painter = new TranslatedTextPainter();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        isAdjustment = settings.isWatermark();
    }

    public static void createNew(PixelitorWindow pw) {
        Composition comp = ImageComponents.getActiveCompOrNull();
        if (comp == null) {
            throw new IllegalStateException("no open image");
        }
        TextLayer textLayer = new TextLayer(comp);

        Layer activeLayerBefore = comp.getActiveLayer();
        MaskViewMode oldViewMode = comp.getIC().getMaskViewMode();

        // don't add it yet to history, only after the user chooses to press OK
        comp.addLayer(textLayer, false, null, true, false);

        TextAdjustmentsPanel p = new TextAdjustmentsPanel(textLayer);
        OKCancelDialog d = new OKCancelDialog(p, pw, "Create Text Layer") {
            @Override
            protected void dialogAccepted() {
                close();
                textLayer.updateLayerName();

                // now it is safe to add it to the history
                NewLayerEdit newLayerEdit = new NewLayerEdit(comp, textLayer, activeLayerBefore, "New Text Layer", oldViewMode);
                History.addEdit(newLayerEdit);
            }

            @Override
            protected void dialogCanceled() {
                close();
                comp.deleteLayer(textLayer, false, true);
            }
        };
        d.setVisible(true);
    }

    public void edit(PixelitorWindow pw) {
        if (RandomGUITest.isRunning()) {
            return; // avoid dialogs
        }

        TextSettings oldSettings = getSettings();
        TextAdjustmentsPanel p = new TextAdjustmentsPanel(this);
        OKCancelDialog d = new OKCancelDialog(p, pw, "Edit Text Layer") {
            @Override
            protected void dialogAccepted() {
                close();

                commitSettings(oldSettings);
            }

            @Override
            protected void dialogCanceled() {
                close();

                setSettings(oldSettings);
                comp.imageChanged(Composition.ImageChangeActions.FULL);
            }
        };
        d.setVisible(true);
    }

    public void commitSettings(TextSettings oldSettings) {
        updateLayerName();
        TextLayerChangeEdit edit = new TextLayerChangeEdit(
                comp,
                this,
                oldSettings
        );
        History.addEdit(edit);
    }

    @Override
    public TextLayer duplicate(boolean sameName) {
        String duplicateName = sameName ? name : Utils.createCopyName(name);
        TextLayer d = new TextLayer(comp, duplicateName);

        d.translationX = translationX;
        d.translationY = translationY;
        d.painter.setTranslation(
                painter.getTX(),
                painter.getTY());

        d.setSettings(new TextSettings(settings));

        if (hasMask()) {
            d.addMask(mask.duplicate(d));
        }

        return d;
    }

    // TODO if a text layer has a mask, then this will apply the
    // mask to the layer, resulting in an image layer without a mask.
    // This probably should be considered a bug, and instead the mask
    // should be kept, and the rasterized pixels should not be affected
    // by the mask.
    public ImageLayer replaceWithRasterized() {
        BufferedImage rasterizedImage = createRasterizedImage();

        ImageLayer newImageLayer = new ImageLayer(comp, rasterizedImage, getName(), null);

        TextLayerRasterizeEdit edit = new TextLayerRasterizeEdit(comp, this, newImageLayer);
        History.addEdit(edit);

        comp.addLayer(newImageLayer, false, null, false, false);
        comp.deleteLayer(this, false, true);

        return newImageLayer;
    }

    public BufferedImage createRasterizedImage() {
        BufferedImage img = ImageUtils.createSysCompatibleImage(canvas.getWidth(), canvas.getHeight());
        Graphics2D g = img.createGraphics();
        applyLayer(g, true, img);
        g.dispose();
        return img;
    }

    @Override
    public void paintLayerOnGraphics(Graphics2D g, boolean firstVisibleLayer) {
        painter.setFillPaint(settings.getColor());
        painter.paint(g, null, comp.getCanvasWidth(), comp.getCanvasHeight());
    }

    @Override
    public BufferedImage applyLayer(Graphics2D g, boolean firstVisibleLayer, BufferedImage imageSoFar) {
        if (settings == null) {
            // the layer was just created, nothing to paint yet
            return imageSoFar;
        }

        // the text will be painted normally
        return super.applyLayer(g, firstVisibleLayer, imageSoFar);
    }

    @Override
    public BufferedImage actOnImageFromLayerBellow(BufferedImage src) {
        assert settings.isWatermark(); // should be called only in this case
        return settings.watermarkImage(src, painter);
    }

    @Override
    public void moveWhileDragging(double x, double y) {
        super.moveWhileDragging(x, y);
        painter.setTranslation(getTX(), getTY());
    }

    @Override
    ContentLayerMoveEdit createMovementEdit(int oldTX, int oldTY) {
        return new ContentLayerMoveEdit(this, null, oldTX, oldTY);
    }

    @Override
    public void setTranslation(int x, int y) {
        super.setTranslation(x, y);
        painter.setTranslation(x, y);
    }

    public void setSettings(TextSettings settings) {
        this.settings = settings;

        isAdjustment = settings.isWatermark();
        settings.configurePainter(painter);
    }

    public TextSettings getSettings() {
        return settings;
    }

    public void updateLayerName() {
        if (settings != null) {
            setName(settings.getText(), false);
        }
    }

    @Override
    public void enlargeCanvas(int north, int east, int south, int west) {
        VerticalAlignment verticalAlignment = painter.getVerticalAlignment();
        HorizontalAlignment horizontalAlignment = painter.getHorizontalAlignment();
        int newTX = translationX;
        int newTY = translationY;

        if (horizontalAlignment == LEFT) {
            newTX += west;
        } else if (horizontalAlignment == CENTER) {
            newTX += (west - east) / 2;
        } else { // RIGHT
            newTX -= east;
        }

        if (verticalAlignment == TOP) {
            newTY += north;
        } else if (verticalAlignment == VerticalAlignment.CENTER) {
            newTY += (north - south) / 2;
        } else { // BOTTOM
            newTY -= south;
        }

        setTranslation(newTX, newTY);
    }

    @Override
    public void flip(Flip.Direction direction) {
        // TODO
    }

    @Override
    public void rotate(Rotate.SpecialAngle angle) {
        // TODO
    }

    @Override
    public void resize(int targetWidth, int targetHeight, boolean progressiveBilinear) {
        // TODO
    }

    @Override
    public void crop(Rectangle2D cropRect) {
        // the text will not be cropped, but the translations have to be adjusted

        // as the cropping is the exact opposite of "enlarge canvas",
        // calculate the corresponding margins...
        int northMargin = (int) cropRect.getY();
        int westMargin = (int) cropRect.getX();
        int southMargin = (int) (canvas.getHeight() - cropRect.getHeight() - cropRect.getY());
        int eastMargin = (int) (canvas.getWidth() - cropRect.getWidth() - cropRect.getX());

        // ...and do a negative enlargement
        enlargeCanvas(-northMargin, -eastMargin, -southMargin, -westMargin);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{text=" + (settings == null ? "null settings" : settings.getText())
                + ", super=" + super.toString() + '}';
    }
}
