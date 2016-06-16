package com.a2a.googlechart;

public class RangeMarker {

    public static String Horizontal = "Horizontal";
    public static String Vertical = "Vertical";

    private String type;
    private String color;
    private double startPoint;
    private double endPoint;

    // / <summary>
    // / Create a range marker for line charts and scatter plots
    // / </summary>
    // / <param name="rangeMarkerType"></param>
    // / <param name="color">an RRGGBB format hexadecimal number</param>
    // / <param name="startPoint">Must be between 0.0 and 1.0. 0.0 is axis start, 1.0 is axis end.</param>
    // / <param name="endPoint">Must be between 0.0 and 1.0. 0.0 is axis start, 1.0 is axis end.</param>
    public RangeMarker(String rangeMarkerType, String color, double startPoint, double endPoint) {
        this.type = rangeMarkerType;
        this.color = color;
        this.startPoint = startPoint;
        this.endPoint = endPoint;
    }

    public String getTypeUrlChar() {
        String retour = null;

        if (this.type.equals(RangeMarker.Horizontal)) {
            retour = "r";
        } else if (this.type.equals(RangeMarker.Vertical)) {
            retour = "R";
        }

        return retour;
    }

    public String getUrlString() {
        String s = new String("");
        s += getTypeUrlChar() + ",";
        s += color + ",";
        // this value is ignored - but has to be a number
        s += "0" + ",";
        s += String.valueOf(startPoint) + ",";
        s += String.valueOf(endPoint);
        return s;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getStartPoint() {
        return startPoint;
    }

    public void setStartPoint(double startPoint) {
        this.startPoint = startPoint;
    }

    public double getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(double endPoint) {
        this.endPoint = endPoint;
    }
}
