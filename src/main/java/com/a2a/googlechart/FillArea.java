package com.a2a.googlechart;

public class FillArea {

    public static String SingleLine = "SingleLine";
    public static String MultiLine = "MultiLine";

    private String type;
    private String color;
    private int startLineIndex;
    private int endLineIndex;

    // / <summary>
    // / Create a fill area between lines for use on a line chart.
    // / </summary>
    // / <param name="color">an RRGGBB format hexadecimal number</param>
    // / <param name="startLineIndex">line indexes are determined by the order in which datasets are added. The first set is index 0, then index 1 etc</param>
    // / <param name="endLineIndex">line indexes are determined by the order in which datasets are added. The first set is index 0, then index 1 etc</param>
    public FillArea(String color, int startLineIndex, int endLineIndex) {
        this.type = FillArea.MultiLine;
        this.color = color;
        this.startLineIndex = startLineIndex;
        this.endLineIndex = endLineIndex;
    }

    // / <summary>
    // / Fill all the area under a line
    // / </summary>
    // / <param name="color">an RRGGBB format hexadecimal number</param>
    // / <param name="lineIndex">line indexes are determined by the order in which datasets are added. The first set is index 0, then index 1 etc</param>
    public FillArea(String color, int lineIndex) {
        this.type = FillArea.SingleLine;
        this.color = color;
        this.startLineIndex = lineIndex;
    }

    public String getUrlString() {
        String s = new String("");

        if (type.equals(FillArea.MultiLine)) {
            s += "b" + ",";
            s += this.color + ",";
            s += String.valueOf(this.startLineIndex) + ",";
            s += String.valueOf(this.endLineIndex) + ",";
            s += "0"; // ignored
        } else if (type.equals(FillArea.SingleLine)) {
            s += "B" + ",";
            s += this.color + ",";
            s += String.valueOf(this.startLineIndex) + ",";
            s += "0" + ","; // ignored
            s += "0"; // ignored
        }

        return s;
    }
}
