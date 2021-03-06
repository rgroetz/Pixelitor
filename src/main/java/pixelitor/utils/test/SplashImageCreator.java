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

package pixelitor.utils.test;

import org.jdesktop.swingx.painter.AbstractLayoutPainter;
import org.jdesktop.swingx.painter.effects.ShadowPathEffect;
import pixelitor.Build;
import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.Composition.LayerAdder;
import pixelitor.NewImage;
import pixelitor.automate.SingleDirChooser;
import pixelitor.colors.FgBgColors;
import pixelitor.colors.FillType;
import pixelitor.filters.ColorWheel;
import pixelitor.filters.ValueNoise;
import pixelitor.filters.jhlabsproxies.JHDropShadow;
import pixelitor.filters.painters.AreaEffects;
import pixelitor.filters.painters.TextSettings;
import pixelitor.io.Dirs;
import pixelitor.io.OutputFormat;
import pixelitor.io.SaveSettings;
import pixelitor.layers.BlendingMode;
import pixelitor.layers.Drawable;
import pixelitor.layers.ImageLayer;
import pixelitor.layers.TextLayer;
import pixelitor.tools.gradient.Gradient;
import pixelitor.tools.gradient.GradientType;
import pixelitor.tools.util.ImDrag;
import pixelitor.utils.Messages;
import pixelitor.utils.ProgressHandler;
import pixelitor.utils.Rnd;
import pixelitor.utils.Utils;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.font.TextAttribute;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.awt.Color.WHITE;
import static java.awt.MultipleGradientPaint.CycleMethod.REFLECT;
import static java.lang.String.format;
import static pixelitor.ChangeReason.FILTER_WITHOUT_DIALOG;
import static pixelitor.Composition.LayerAdder.Position.TOP;
import static pixelitor.tools.gradient.GradientColorType.BLACK_TO_WHITE;
import static pixelitor.tools.gradient.GradientColorType.FG_TO_BG;

/**
 * Static methods for creating the splash images
 */
public class SplashImageCreator {
    private static final String SPLASH_SMALL_FONT = "DejaVu Sans Light";
    private static final String SPLASH_MAIN_FONT = "Azonix";
    private static final int SPLASH_WIDTH = 400;
    private static final int SPLASH_HEIGHT = 247;

    private SplashImageCreator() {
    }

    public static void saveManySplashImages() {
        assert EventQueue.isDispatchThread() : "not EDT thread";

        boolean okPressed = SingleDirChooser.selectOutputDir(OutputFormat.PNG);
        if (!okPressed) {
            return;
        }
        int numImages = 64;
        String msg = format("Save %d Splash Images: ", numImages);
        var progressHandler = Messages.startProgress(msg, numImages);

        CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);
        for (int i = 0; i < numImages; i++) {
            int seqNo = i;
            cf = cf.thenCompose(v -> makeSplashAsync(progressHandler, seqNo));
        }
        cf.thenRunAsync(() -> cleanupAfterManySplashes(numImages, progressHandler),
                EventQueue::invokeLater);
    }

    private static CompletableFuture<Void> makeSplashAsync(ProgressHandler ph, int seqNo) {
        return CompletableFuture
            .supplyAsync(() -> makeOneSplashImage(ph, seqNo), EventQueue::invokeLater)
            .thenCompose(SplashImageCreator::saveAndCloseOneSplash);
    }

    private static Composition makeOneSplashImage(ProgressHandler ph, int seqNo) {
        ph.updateProgress(seqNo);

        var comp = createSplashComp();
        comp.getView().paintImmediately();

        OutputFormat format = OutputFormat.getLastUsed();
        String fileName = format("splash%04d.%s", seqNo, format);
        File f = new File(Dirs.getLastSave(), fileName);
        comp.setFile(f);

        return comp;
    }

    public static Composition createSplashComp() {
        assert EventQueue.isDispatchThread() : "not EDT thread";

        FgBgColors.setFGColor(WHITE);
//        FgBgColors.setBGColor(new Color(6, 83, 81));
        FgBgColors.setBGColor(Rnd.createRandomColor().darker().darker().darker());

        var comp = NewImage.addNewImage(FillType.WHITE, SPLASH_WIDTH, SPLASH_HEIGHT, "Splash");
        ImageLayer layer = (ImageLayer) comp.getLayer(0);

        for (int i = 0; i < 3; i++) {
            GradientType gradientType = Rnd.chooseFrom(GradientType.values());
            CycleMethod cycleMethod = REFLECT;

            Gradient gradient = new Gradient(
                    ImDrag.createRandom(SPLASH_WIDTH, SPLASH_HEIGHT, SPLASH_HEIGHT / 2),
                    gradientType, cycleMethod, FG_TO_BG,
                    false, BlendingMode.MULTIPLY, 1.0f);
            gradient.drawOn(layer);
        }

        addTextLayers(comp);

        return comp;
    }

    public static Composition createOldSplashImage() {
        assert EventQueue.isDispatchThread() : "not EDT thread";

        ValueNoise.reseed();
        var comp = NewImage.addNewImage(FillType.WHITE, SPLASH_WIDTH, SPLASH_HEIGHT, "Splash");
        ImageLayer layer = (ImageLayer) comp.getLayer(0);

        layer.setName("Color Wheel", true);
        new ColorWheel().startOn(layer, FILTER_WITHOUT_DIALOG);

        layer = addNewLayer(comp, "Value Noise");
        ValueNoise valueNoise = new ValueNoise();
        valueNoise.setDetails(7);
        valueNoise.startOn(layer, FILTER_WITHOUT_DIALOG);
        layer.setOpacity(0.3f, true, true, true);
        layer.setBlendingMode(BlendingMode.SCREEN, true, true, true);

        layer = addNewLayer(comp, "Gradient");
        addRadialBWGradientToActiveDrawable(layer, true);
        layer.setOpacity(0.4f, true, true, true);
        layer.setBlendingMode(BlendingMode.LUMINOSITY, true, true, true);

        addTextLayers(comp);

        return comp;
    }

    private static void addTextLayers(Composition comp) {
        FgBgColors.setFGColor(WHITE);
        Font font = createSplashFont(SPLASH_MAIN_FONT, Font.PLAIN, 48);
        addTextLayer(comp, "Pixelitor", WHITE,
                font, -17, BlendingMode.NORMAL, 1.0f, true);

        font = createSplashFont(SPLASH_SMALL_FONT, Font.PLAIN, 22);
        addTextLayer(comp, "Loading...",
                WHITE, font, -70, BlendingMode.NORMAL, 1.0f, true);

        font = createSplashFont(SPLASH_SMALL_FONT, Font.PLAIN, 20);
        addTextLayer(comp, "version " + Build.VERSION_NUMBER,
                WHITE, font, 50, BlendingMode.NORMAL, 1.0f, true);
    }

    private static Font createSplashFont(String name, int style, int size) {
        Font font = new Font(name, style, size);

        // check that the font exists
        assert font.getName().equals(name) : font.getName();

        Map<TextAttribute, Object> attr = new HashMap<>();
        attr.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        attr.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        font = font.deriveFont(attr);

        return font;
    }

    private static void addDropShadow(ImageLayer layer) {
        JHDropShadow dropShadow = new JHDropShadow();
        dropShadow.setDistance(5);
        dropShadow.setSoftness(5);
        dropShadow.setOpacity(0.7f);
        dropShadow.startOn(layer, FILTER_WITHOUT_DIALOG);
    }

    private static ImageLayer addNewLayer(Composition comp, String name) {
        ImageLayer imageLayer = comp.addNewEmptyLayer(name, false);
        imageLayer.setName(name, true);
        return imageLayer;
    }

    private static TextLayer addNewTextLayer(Composition comp, String name) {
        TextLayer textLayer = new TextLayer(comp, name);
        new LayerAdder(comp).atPosition(TOP).add(textLayer);
        return textLayer;
    }

    private static TextLayer addTextLayer(Composition comp, String text,
                                          Color textColor, Font font,
                                          int translationY, BlendingMode blendingMode,
                                          float opacity, boolean dropShadow) {
        TextLayer layer = addNewTextLayer(comp, text);

        AreaEffects effects = null;
        if (dropShadow) {
            effects = new AreaEffects();
            ShadowPathEffect dropShadowEffect = new ShadowPathEffect(0.6f);
            dropShadowEffect.setEffectWidth(3);
            dropShadowEffect.setOffset(Utils.offsetFromPolar(4, 0.7));
            effects.setDropShadow(dropShadowEffect);
        }

        TextSettings settings = new TextSettings(text, font, textColor, effects,
                AbstractLayoutPainter.HorizontalAlignment.CENTER,
                AbstractLayoutPainter.VerticalAlignment.CENTER, false, 0);

        layer.setSettings(settings);

        layer.startMovement();
        layer.moveWhileDragging(0, translationY);
        layer.endMovement();

        layer.setOpacity(opacity, true, true, true);
        layer.setBlendingMode(blendingMode, true, true, true);

        return layer;
    }

    public static void addRadialBWGradientToActiveDrawable(Drawable dr, boolean radial) {
        Canvas canvas = dr.getComp().getCanvas();
        int canvasWidth = canvas.getImWidth();
        int canvasHeight = canvas.getImHeight();

        int startX = canvasWidth / 2;
        int startY = canvasHeight / 2;

        int endX = 0;
        int endY = 0;
        if (canvasWidth > canvasHeight) {
            endX = startX;
        } else {
            endY = startY;
        }

        GradientType gradientType;

        if (radial) {
            gradientType = GradientType.RADIAL;
        } else {
            gradientType = GradientType.SPIRAL_CW;
        }

        Gradient gradient = new Gradient(
                new ImDrag(startX, startY, endX, endY),
                gradientType, REFLECT, BLACK_TO_WHITE,
                false,
                BlendingMode.NORMAL, 1.0f);
        gradient.drawOn(dr);
    }

    private static CompletableFuture<Void> saveAndCloseOneSplash(Composition comp) {
        SaveSettings saveSettings = new SaveSettings(
                OutputFormat.getLastUsed(), comp.getFile());
        return comp.saveAsync(saveSettings, false)
                .thenAcceptAsync(v -> comp.getView().close(), EventQueue::invokeLater);
    }

    private static void cleanupAfterManySplashes(int numCreatedImages, ProgressHandler progressHandler) {
        progressHandler.stopProgress();
        Messages.showInStatusBar(format(
                "Finished saving %d splash images to %s",
                numCreatedImages, Dirs.getLastSave()));
    }
}
