package com.a2a.googlechart;

public class ShapeMarker {

    String type;
    String color;
    int datasetIndex;
    float dataPoint;
    int size;

    public static String Arrow = "Arrow";
    public static String Cross = "Cross";
    public static String Diamond = "Diamond";
    public static String Circle = "Circle";
    public static String Square = "Square";
    public static String VerticalLineToDataPoint = "VerticalLineToDataPoint";
    public static String VerticalLine = "VerticalLine";
    public static String HorizontalLine = "HorizontalLine";
    public static String XShape = "XShape";

    // / <summary>
    // / Create a shape marker for points on line charts and scatter plots
    // / </summary>
    // / <param name="markerType"></param>
    // / <param name="hexColor">RRGGBB format hexadecimal number</param>
    // / <param name="datasetIndex">the index of the line on which to draw the marker. This is 0 for the first data set, 1 for the second and so on</param>
    // / <param name="dataPoint">a floating point value that specifies on which data point the marker will be drawn. This is 1 for the first data set, 2 for the second and so on. Specify a fraction to
    // interpolate a marker between two points.</param>
    // / <param name="size">the size of the marker in pixels</param>
    public ShapeMarker(String markerType, String color, int datasetIndex, float dataPoint, int size) {
        this.type = markerType;
        this.color = color;
        this.datasetIndex = datasetIndex;
        this.dataPoint = dataPoint;
        this.size = size;
    }

    protected String getTypeUrlChar() {
        String retour = null;

        if (this.type.equals(ShapeMarker.Arrow)) {
            retour = "a";
        } else if (this.type.equals(ShapeMarker.Cross)) {
            retour = "c";
        } else if (this.type.equals(ShapeMarker.Diamond)) {
            retour = "d";
        } else if (this.type.equals(ShapeMarker.Circle)) {
            retour = "o";
        } else if (this.type.equals(ShapeMarker.Square)) {
            retour = "s";
        } else if (this.type.equals(ShapeMarker.VerticalLineToDataPoint)) {
            retour = "v";
        } else if (this.type.equals(ShapeMarker.VerticalLine)) {
            retour = "V";
        } else if (this.type.equals(ShapeMarker.HorizontalLine)) {
            retour = "h";
        } else if (this.type.equals(ShapeMarker.XShape)) {
            retour = "x";
        }
        return retour;
    }

    public String getUrlString() {
        String s = new String("");
        s += getTypeUrlChar() + ",";
        s += color + ",";
        s += datasetIndex + ",";
        s += dataPoint + ",";
        s += String.valueOf(size);
        return s;
    }
}
