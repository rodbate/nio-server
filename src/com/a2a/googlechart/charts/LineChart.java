package com.a2a.googlechart.charts;

import java.util.ArrayList;

public class LineChart extends Chart {

    public static String SingleDataSet = "SingleDataSet";
    public static String MultiDataSet = "MultiDataSet";

    private String lineChartType = LineChart.SingleDataSet;
    private ArrayList lineStyles = new ArrayList();

    // / <summary>
    // / Create a line chart with one line per dataset. Points are evenly spaced along the x-axis.
    // / </summary>
    // / <param name="width">width in pixels</param>
    // / <param name="height">height in pixels</param>
    public LineChart(int width, int height) {
        super(width, height);
        this.lineChartType = LineChart.SingleDataSet;
    }

    // / <summary>
    // / Create a line chart with the specified type.
    // / </summary>
    // / <param name="width">width in pixels</param>
    // / <param name="height">height in pixels</param>
    // / <param name="lineChartType">specifies how the chart handles datasets</param>
    public LineChart(int width, int height, String lineChartType) {
        super(width, height);
        this.lineChartType = lineChartType;
    }

    @Override
    protected String urlChartType() {
        if (this.lineChartType.equals(LineChart.MultiDataSet)) {
            return "lxy";
        }
        return "lc";
    }

    // / <summary>
    // / Apply a style to a line. Line styles are applied to lines in order, the
    // / first line will use the first line style.
    // / </summary>
    // / <param name="lineStyle"></param>
    public void addLineStyle(LineStyle lineStyle) {
        lineStyles.add(lineStyle);
    }

    @Override
    protected void collectUrlElements() {
        super.collectUrlElements();
        if (lineStyles.size() > 0) {
            String s = "chls=";
            for (int i = 0; i < lineStyles.size(); i++) {
                LineStyle lineStyle = (LineStyle) lineStyles.get(i);

                s += String.valueOf(lineStyle.getLineThickness()) + ",";
                s += String.valueOf(lineStyle.getLengthOfSegment()) + ",";
                s += String.valueOf(lineStyle.getLengthOfBlankSegment()) + "|";
            }

            urlElements.add(trimEnd(s, '|'));
        }
    }

    @Override
    protected String getChartType() {
        return Chart.chartLineChart;
    }
}

class LineStyle {

    private float lineThickness;
    private float lengthOfSegment;
    private float lengthOfBlankSegment;

    // / <summary>
    // / Create a line style
    // / </summary>
    // / <param name="lineThickness">line thickness in pixels</param>
    // / <param name="lengthOfSegment">length of each solid line segment in pixels</param>
    // / <param name="lengthOfBlankSegment">length of each blank line segment in pixels</param>
    public LineStyle(float lineThickness, float lengthOfSegment, float lengthOfBlankSegment) {
        this.lineThickness = lineThickness;
        this.lengthOfSegment = lengthOfSegment;
        this.lengthOfBlankSegment = lengthOfBlankSegment;
    }

    public float getLineThickness() {
        return lineThickness;
    }

    public void setLineThickness(float lineThickness) {
        this.lineThickness = lineThickness;
    }

    public float getLengthOfSegment() {
        return lengthOfSegment;
    }

    public void setLengthOfSegment(float lengthOfSegment) {
        this.lengthOfSegment = lengthOfSegment;
    }

    public float getLengthOfBlankSegment() {
        return lengthOfBlankSegment;
    }

    public void setLengthOfBlankSegment(float lengthOfBlankSegment) {
        this.lengthOfBlankSegment = lengthOfBlankSegment;
    }
}
