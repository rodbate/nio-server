package com.a2a.googlechart.charts;

public class BarChart extends Chart {

    String orientation;
    String style;
    int barWidth;

    public static String Stacked = "Stacked";
    public static String Grouped = "Grouped";
    public static String Vertical = "Vertical";
    public static String Horizontal = "Horizontal";

    // / <summary>
    // / Create a bar chart
    // / </summary>
    // / <param name="width">Width in pixels</param>
    // / <param name="height">Height in pixels</param>
    // / <param name="orientation">The orientation of the bars.</param>
    // / <param name="style">Bar chart style when using multiple data sets</param>
    public BarChart(int width, int height, String orientation, String style) {
        super(width, height);

        this.orientation = orientation;
        this.style = style;
    }

    // / <summary>
    // / Set the width of the individual bars
    // / </summary>
    // / <param name="width">Width in pixels</param>
    public void SetBarWidth(int width) {
        this.barWidth = width;
    }

    // / <summary>
    // / Return the chart identifier used in the chart url.
    // / </summary>
    // / <returns></returns>
    @Override
    protected String urlChartType() {
        char orientationChar = this.orientation == BarChart.Horizontal ? 'h' : 'v';
        char styleChar = this.style == BarChart.Stacked ? 's' : 'g';

        return "b" + orientationChar + styleChar;
    }

    // / <summary>
    // / Collect all the elements that will make up the chart url.
    // / </summary>
    @Override
    protected void collectUrlElements() {
        super.collectUrlElements();
        if (this.barWidth != 0) {
            super.urlElements.add("chbh=" + this.barWidth);
        }
    }

    // / <summary>
    // / Return the chart type for this chart
    // / </summary>
    // / <returns></returns>
    @Override
    protected String getChartType() {
        return Chart.chartBarChart;
    }
}
