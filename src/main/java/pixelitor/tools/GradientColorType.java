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
package pixelitor.tools;

import pixelitor.colors.FgBgColors;

import java.awt.Color;

import static java.awt.Color.BLACK;
import static java.awt.Color.WHITE;

/**
 * The color option in the gradient tool
 */
public enum GradientColorType {
    FG_TO_BG("Foreground to Background") {
        @Override
        protected Color getFirstColor() {
            return FgBgColors.getFG();
        }

        @Override
        protected Color getSecondColor() {
            return FgBgColors.getBG();
        }
    }, FG_TO_TRANSPARENT("Foreground to Transparent") {
        private final Color transparentColor = new Color(0, 0, 0, 0);

        @Override
        protected Color getFirstColor() {
            return FgBgColors.getFG();
        }

        @Override
        protected Color getSecondColor() {
            return transparentColor;
        }
    }, BLACK_TO_WHITE("Black to White") {
        @Override
        protected Color getFirstColor() {
            return BLACK;
        }

        @Override
        protected Color getSecondColor() {
            return WHITE;
        }
    };

    private final String guiName;

    GradientColorType(String guiName) {
        this.guiName = guiName;
    }

    protected abstract Color getFirstColor();

    protected abstract Color getSecondColor();

    public Color getStartColor(boolean invert) {
        if (invert) {
            return getSecondColor();
        }
        return getFirstColor();
    }

    public Color getEndColor(boolean invert) {
        if (invert) {
            return getFirstColor();
        }
        return getSecondColor();
    }

    @Override
    public String toString() {
        return guiName;
    }
}
