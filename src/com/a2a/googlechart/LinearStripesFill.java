package com.a2a.googlechart;

import java.util.ArrayList;
import com.a2a.googlechart.charts.Chart;

public class LinearStripesFill {

    private String fillTarget;
    private int angle;
    private ArrayList colorWidthPairs = new ArrayList();

    // / <summary>
    // / Create a linear stripes fill.
    // / </summary>
    // / <param name="fillTarget">The area that will be filled.</param>
    // / <param name="angle">specifies the angle of the gradient between 0 (vertical) and 90 (horizontal)</param>
    public LinearStripesFill(String fillTarget, int angle) {
        this.fillTarget = fillTarget;
        this.angle = angle;
    }

    // / <summary>
    // / A color/width pair describes a linear stripe.
    // / </summary>
    // / <param name="color">RRGGBB format hexadecimal number</param>
    // / <param name="width">must be between 0 and 1 where 1 is the full width of the chart</param>
    public void AddColorWidthPair(String color, double width) {
        this.colorWidthPairs.add(new ColorWidthPair(color, width));
    }

    protected String getTypeUrlChar() {
        String retour = null;

        if (fillTarget.equals(SolidFill.ChartArea)) {
            retour = "c";
        } else if (fillTarget.equals(SolidFill.Background)) {
            retour = "bg";
        }

        return retour;
    }

    public String getUrlString() {
        String s = new String("");
        s += getTypeUrlChar() + ",";
        s += "ls,";
        s += String.valueOf(angle) + ",";

        for (int i = 0; i < colorWidthPairs.size(); i++) {
            ColorWidthPair colorWidthPair = (ColorWidthPair) colorWidthPairs.get(i);
            s += colorWidthPair.color + ",";
            s += colorWidthPair.width + ",";
        }

        return Chart.trimEnd(s, ',');
    }

    private class ColorWidthPair {

        public String color;
        public double width;

        // / <summary>
        // / Describes a linear stripe. Stripes are repeated until the chart is filled.
        // / </summary>
        // / <param name="color">RGGBB format hexadecimal number</param>
        // / <param name="width">must be between 0 and 1 where 1 is the full width of the chart</param>
        public ColorWidthPair(String color, double width) {
            this.color = color;
            this.width = width;
        }
    }
}
