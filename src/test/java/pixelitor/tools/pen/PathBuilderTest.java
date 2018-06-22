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

package pixelitor.tools.pen;

import org.junit.Before;
import org.junit.Test;
import pixelitor.TestHelper;
import pixelitor.gui.ImageComponent;
import pixelitor.tools.PMouseEvent;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

import static org.mockito.Mockito.mock;
import static pixelitor.assertions.PixelitorAssertions.assertThat;
import static pixelitor.tools.pen.PathBuilder.State.DRAGGING_THE_CONTROL_OF_LAST;
import static pixelitor.tools.pen.PathBuilder.State.INITIAL;
import static pixelitor.tools.pen.PathBuilder.State.MOVING_TO_NEXT_CURVE_POINT;

public class PathBuilderTest {
    ImageComponent ic;

    @Before
    public void setup() {
        ic = TestHelper.createICWithoutComp();
    }

    @Test
    public void testBuilding3PointClosedPath() {
        Graphics2D g = mock(Graphics2D.class);
        Path path = new Path();
        PathBuilder pb = new PathBuilder(path);
        pb.assertStateIs(INITIAL);

        // start the curve
        pb.mousePressed(createPMouseEvent(10, 10));
        pb.assertStateIs(DRAGGING_THE_CONTROL_OF_LAST);
        assertThat(path).numPointsIs(1);
        AnchorPoint firstAnchorPoint = path.getPoint(0);
        assertThat(firstAnchorPoint)
                .locIs(10, 10)
                .imLocIs(10, 10);
        pb.paint(g);

        // drag towards right
        pb.mouseDragged(createPMouseEvent(20, 10));
        pb.assertStateIs(DRAGGING_THE_CONTROL_OF_LAST);
        assertThat(firstAnchorPoint.ctrlOut)
                .locIs(20, 10)
                .imLocIs(10, 10);

        pb.paint(g);
        pb.mouseDragged(createPMouseEvent(30, 10));
        pb.assertStateIs(DRAGGING_THE_CONTROL_OF_LAST);
        pb.paint(g);
        pb.mouseDragged(createPMouseEvent(40, 10));
        pb.assertStateIs(DRAGGING_THE_CONTROL_OF_LAST);
        assertThat(path).numPointsIs(1);
        pb.paint(g);

        // release to set the final value of the control point
        pb.mouseReleased(createPMouseEvent(50, 10));
        pb.assertStateIs(MOVING_TO_NEXT_CURVE_POINT);
        assertThat(path).numPointsIs(1);
        pb.paint(g);
        assertThat(firstAnchorPoint.ctrlOut)
                .locIs(50, 10)
                .imLocIs(50, 10);

        // move down towards the second path point
        pb.mouseMoved(createMouseEvent(50, 20), ic);
        pb.assertStateIs(MOVING_TO_NEXT_CURVE_POINT);
        pb.paint(g);
        pb.mouseMoved(createMouseEvent(50, 30), ic);
        pb.assertStateIs(MOVING_TO_NEXT_CURVE_POINT);
        pb.paint(g);
        pb.mouseMoved(createMouseEvent(50, 40), ic);
        pb.assertStateIs(MOVING_TO_NEXT_CURVE_POINT);
        assertThat(path).numPointsIs(1);
        pb.paint(g);

        // press to fix the second path point
        pb.mousePressed(createPMouseEvent(50, 50));
        pb.assertStateIs(DRAGGING_THE_CONTROL_OF_LAST);
        assertThat(path).numPointsIs(2);
        pb.paint(g);
        AnchorPoint secondPoint = path.getPoint(1);
        assertThat(secondPoint)
                .locIs(50, 50)
                .imLocIs(50, 50);

        // drag towards right to drag out the control points
        pb.mouseDragged(createPMouseEvent(60, 50));
        pb.assertStateIs(DRAGGING_THE_CONTROL_OF_LAST);
        pb.paint(g);
        pb.mouseDragged(createPMouseEvent(70, 50));
        pb.assertStateIs(DRAGGING_THE_CONTROL_OF_LAST);
        pb.paint(g);
        pb.mouseDragged(createPMouseEvent(80, 50));
        pb.assertStateIs(DRAGGING_THE_CONTROL_OF_LAST);
        assertThat(path).numPointsIs(2);
        pb.paint(g);

        // release to fix the control points
        pb.mouseReleased(createPMouseEvent(90, 50));
        pb.assertStateIs(MOVING_TO_NEXT_CURVE_POINT);
        assertThat(path).numPointsIs(2);
        pb.paint(g);
        assertThat(secondPoint.ctrlOut)
                .locIs(90, 50)
                .imLocIs(90, 50);

        // move up towards the next path point
        pb.mouseMoved(createMouseEvent(90, 40), ic);
        pb.paint(g);
        pb.mouseMoved(createMouseEvent(90, 30), ic);
        pb.paint(g);
        pb.mouseMoved(createMouseEvent(90, 20), ic);
        assertThat(path).numPointsIs(2);
        pb.paint(g);

        // press to finalize the third path point at 90, 10
        pb.mousePressed(createPMouseEvent(90, 10));
        assertThat(path).numPointsIs(3);
        AnchorPoint thirdPoint = path.getPoint(2);
        assertThat(thirdPoint)
                .locIs(90, 10)
                .imLocIs(90, 10);

        // drag to the right to finalize the control points of the third point
        pb.mouseDragged(createPMouseEvent(100, 10));
        pb.mouseDragged(createPMouseEvent(110, 10));
        pb.mouseDragged(createPMouseEvent(120, 10));
        pb.mouseReleased(createPMouseEvent(120, 10)); // finalized
        assertThat(path).numPointsIs(3);
        assertThat(thirdPoint.ctrlOut)
                .locIs(120, 10)
                .imLocIs(120, 10);

        // move towards the starting point (10, 10)
        // in order to close
        assertThat(path)
                .isNotClosed()
                .firstIsNotActive();
        pb.mouseMoved(createMouseEvent(100, 10), ic);
        pb.mouseMoved(createMouseEvent(50, 10), ic);
        assertThat(path)
                .isNotClosed()
                .firstIsNotActive();
        pb.mouseMoved(createMouseEvent(12, 10), ic);
        // we are close, the first should become active
        assertThat(path)
                .isNotClosed()
                .firstIsActive();
        // now close it
        pb.mousePressed(createPMouseEvent(11, 10));
        assertThat(path)
                .isClosed()
                .firstIsNotActive()
                .numPointsIs(4); // first point added twice
    }

    private PMouseEvent createPMouseEvent(int x, int y) {
        MouseEvent me = createMouseEvent(x, y);
        PMouseEvent pe = new PMouseEvent(me, ic);
        return pe;
    }

    private MouseEvent createMouseEvent(int x, int y) {
        return new MouseEvent(ic, 0, 0, 0, x, y, 1, false);
    }
}