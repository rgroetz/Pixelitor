/*
 * Copyright 2015 Laszlo Balazs-Csiki
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

import pixelitor.Composition;
import pixelitor.ImageComponents;
import pixelitor.layers.Layer;
import pixelitor.utils.Optional;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 */
public class PixelitorEvent implements Comparable<PixelitorEvent> {
    private final String description;
    private final Date date;
    private final Composition comp;
    private final Layer layer;
    private static final Format dateFormatter = new SimpleDateFormat("HH:mm:ss:SSS");

    public PixelitorEvent(String description) {
        assert description != null;

        this.description = description;

        date = new Date();

        Optional<Composition> opt = ImageComponents.getActiveComp();
        if (opt.isPresent()) {
            comp = opt.get();
            layer = comp.getActiveLayer();
        } else {
            comp = null;
            layer = null;
        }
    }

    public String getMessage() {
        return description;
    }

    @Override
    public int compareTo(PixelitorEvent o) {
        Date thisDate = date;
        Date otherDate = o.date;

        return thisDate.compareTo(otherDate);
    }

    @Override
    public String toString() {
        return description + " on \"" + comp.getName() + '/' + layer.getName() + "\" at " + dateFormatter.format(date);
    }
}
