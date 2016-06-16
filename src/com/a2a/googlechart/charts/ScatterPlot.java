package com.a2a.googlechart.charts;

public class ScatterPlot extends Chart {

    // / <summary>
    // / Create a scatter plot
    // / </summary>
    // / <param name="width">width in pixels</param>
    // / <param name="height">height in pixels</param>
    public ScatterPlot(int width, int height) {
        super(width, height);
    }

    @Override
    public String urlChartType() {
        return "s";
    }

    @Override
    public String getChartType() {
        return Chart.chartScatterPlot;
    }
}
