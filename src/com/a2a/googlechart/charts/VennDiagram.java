package com.a2a.googlechart.charts;

public class VennDiagram extends Chart {

    // / <summary>
    // / Create a venn diagram
    // / </summary>
    // / <param name="width">width in pixels</param>
    // / <param name="height">height in pixels</param>
    public VennDiagram(int width, int height) {
        super(width, height);
    }

    @Override
    public String urlChartType() {
        return "v";
    }

    @Override
    public String getChartType() {
        return Chart.chartVennDiagram;
    }
}
