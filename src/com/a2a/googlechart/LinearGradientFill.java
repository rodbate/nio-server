package com.a2a.googlechart;

import java.util.ArrayList;
import com.a2a.googlechart.charts.Chart;

public class LinearGradientFill {

    private String fillTarget;
    private int angle;
    private ArrayList colorOffsetPairs = new ArrayList();

    // / <summary>
    // / Create a linear gradient
    // / </summary>
    // / <param name="fillTarget">area to be filled</param>
    // / <param name="angle">specifies the angle of the gradient between 0 (horizontal) and 90 (vertical)</param>
    public LinearGradientFill(String fillTarget, int angle) {
        this.fillTarget = fillTarget;
        this.angle = angle;
    }

    // / <summary>
    // / Add a color/offset pair to the linear gradient
    // / </summary>
    // / <param name="color">RRGGBB format hexadecimal number</param>
    // / <param name="offset">specify at what point the color is pure where: 0 specifies the right-most chart position and 1 the left-most</param>
    public void AddColorOffsetPair(String color, double offset) {
        this.colorOffsetPairs.add(new ColorOffsetPair(color, offset));
    }

    public String getTypeUrlChar() {
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
        s += "lg,";
        s += String.valueOf(angle) + ",";

        for (int i = 0; i < colorOffsetPairs.size(); i++) {
            ColorOffsetPair colorOffsetPair = (ColorOffsetPair) colorOffsetPairs.get(i);
            s += colorOffsetPair.color + ",";
            s += colorOffsetPair.offset + ",";
        }

        return Chart.trimEnd(s, ',');
    }

    private class ColorOffsetPair {

        // / <summary>
        // / RRGGBB format hexadecimal number
        // / </summary>
        public String color;

        // / <summary>
        // / specify at what point the color is pure where: 0 specifies the right-most
        // / chart position and 1 the left-most.
        // / </summary>
        public double offset;

        // / <summary>
        // /
        // / </summary>
        // / <param name="color">RRGGBB format hexadecimal number</param>
        // / <param name="offset">specify at what point the color is pure where: 0 specifies the right-most chart position and 1 the left-most</param>
        public ColorOffsetPair(String color, double offset) {
            this.color = color;
            this.offset = offset;
        }
    }
}
