package com.a2a.googlechart.charts;

public class PieChart extends Chart {

    private String pieChartType;
    private String[] pieChartLabels;

    public static String ThreeD = "ThreeD";
    public static String TwoD = "TwoD";

    public PieChart(int width, int height) {
        super(width, height);
        this.pieChartType = TwoD;
    }

    public PieChart(int width, int height, String pieChartType) {
        super(width, height);
        this.pieChartType = pieChartType;
    }

    @Override
    protected String urlChartType() {
        String retour = "p";

        if (this.pieChartType.equals(PieChart.ThreeD)) {
            retour = "p3";
        }

        return retour;
    }

    @Override
    protected void collectUrlElements() {
        super.collectUrlElements();
        if (pieChartLabels != null) {
            String s = "chl=";

            for (int i = 0; i < pieChartLabels.length; i++) {
                String label = pieChartLabels[i];
                s += label;
                if (i < pieChartLabels.length) {
                    s += "|";
                }
            }

            this.urlElements.add(s);
        }
    }

    // / <summary>
    // / Set labels for the Pie Chart slices
    // / </summary>
    // / <param name="labels">Strings that will be used as label text</param>
    public void setPieChartLabels(String[] labels) {
        this.pieChartLabels = labels;
    }

    @Override
    protected String getChartType() {
        return Chart.chartPieChart;
    }
}
