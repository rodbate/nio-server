package com.a2a.googlechart;

import java.util.ArrayList;
import com.a2a.googlechart.charts.Chart;

public class ChartAxis {

    public static String Left = "Left";
    public static String Centered = "Centered";
    public static String Right = "Right";
    public static String Unset = "Unset";
    public static String Bottom = "Bottom";
    public static String Top = "Top";

    String axisType;
    ArrayList axisLabels = new ArrayList();
    int upperBound;
    int lowerBound;
    boolean rangeSet;
    String color;
    int fontSize = -1;
    String alignment = ChartAxis.Unset;

    // / <summary>
    // / Create an axis, default is range 0 - 100 evenly spaced. You can create multiple axes of the same this.
    // / </summary>
    // / <param name="axisType">Axis position</param>
    public ChartAxis(String axisType) {
        this.axisType = axisType;
    }

    // / <summary>
    // / Create an axis, default is range 0 - 100 evenly spaced. You can create multiple axes of the same this.
    // / </summary>
    // / <param name="axisType">Axis position</param>
    // / <param name="labels">These labels will be added to the axis without position information</param>
    public ChartAxis(String axisType, String[] labels) {
        this.axisType = axisType;

        if (labels != null) {
            for (int i = 0; i < labels.length; i++) {
                String label = labels[i];
                this.axisLabels.add(new ChartAxisLabel(label, -1));
            }
        }
    }

    // / <summary>
    // / Specify the axis range
    // / </summary>
    // / <param name="lowerBound">the lowest value on the axis</param>
    // / <param name="upperBound">the highest value on the axis</param>
    public void SetRange(int lowerBound, int upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.rangeSet = true;
    }

    // / <summary>
    // / Add a label to the axis
    // / </summary>
    // / <param name="axisLabel"></param>
    public void addLabel(ChartAxisLabel axisLabel) {
        axisLabels.add(axisLabel);
    }

    public String urlAxisStyle() {
        if (color == null) {
            return null;
        }
        String result = color + ",";
        if (fontSize != -1) {
            result += String.valueOf(fontSize) + ",";
        }

        if (!alignment.equals(ChartAxis.Unset)) {
            if (alignment.equals(ChartAxis.Left)) {
                result += "-1,";
            } else if (alignment.equals(ChartAxis.Centered)) {
                result += "0,";
            } else if (alignment.equals(ChartAxis.Right)) {
                result += "1,";
            }
        }

        return Chart.trimEnd(result, ',');
    }

    public String urlAxisType() {
        String retour = null;

        if (axisType.equals(ChartAxis.Bottom)) {
            retour = "x";
        } else if (axisType.equals(ChartAxis.Top)) {
            retour = "t";
        } else if (axisType.equals(ChartAxis.Left)) {
            retour = "y";
        } else if (axisType.equals(ChartAxis.Right)) {
            retour = "r";
        }

        return retour;
    }

    public String urlLabels() {
        String result = "|";
        for (int i = 0; i < axisLabels.size(); i++) {
            ChartAxisLabel label = (ChartAxisLabel) axisLabels.get(i);
            result += label.text + "|";
        }

        return result;
    }

    public String urlLabelPositions() {
        String result = new String("");
        for (int i = 0; i < axisLabels.size(); i++) {
            ChartAxisLabel axisLabel = (ChartAxisLabel) axisLabels.get(i);
            if (axisLabel.position == -1) {
                return null;
            }
            result += String.valueOf(axisLabel.position) + ",";
        }

        return Chart.trimEnd(result, ',');
    }

    public String urlRange() {
        if (rangeSet) {
            return String.valueOf(lowerBound) + "," + String.valueOf(upperBound);
        }
        return null;
    }
}

// / <summary>
// / Describes an axis label
// / </summary>
class ChartAxisLabel {

    public String text;
    public float position;

    // / <summary>
    // / Create an axis label without position information, labels will be evenly spaced on the axis
    // / </summary>
    // / <param name="text">The label text</param>
    public ChartAxisLabel(String text) {
        this.text = text;
        this.position = -1;
    }

    // / <summary>
    // / Create an axis label without label text. The axis label will be evenly spaced on the axis and the text will
    // / be it's numeric position within the axis range.
    // / </summary>
    // / <param name="position"></param>
    public ChartAxisLabel(float position) {
        this.text = null;
        this.position = position;
    }

    // / <summary>
    // / Create an axis label with label text and position.
    // / </summary>
    // / <param name="text">The label text</param>
    // / <param name="position">The label position within the axis range</param>
    public ChartAxisLabel(String text, float position) {
        this.text = text;
        this.position = position;
    }
}
