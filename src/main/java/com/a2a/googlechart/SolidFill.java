package com.a2a.googlechart;

public class SolidFill {

    public static String Background = "Background";
    public static String ChartArea = "ChartArea";

    private String fillTarget;
    private String color;

    public SolidFill(String fillTarget, String color) {
        this.fillTarget = fillTarget;
        this.color = color;
    }

    private String getTypeUrlChar() {
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
        s += "s,";
        s += this.color;
        return s;
    }

    public String getFillTarget() {
        return fillTarget;
    }

    public void setFillTarget(String fillTarget) {
        this.fillTarget = fillTarget;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

}
