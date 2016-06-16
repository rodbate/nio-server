package com.xunlei.armero;

import java.io.IOException;
import com.xunlei.netty.httpserver.Bootstrap;
import com.xunlei.netty.httpserver.util.HttpServerConfig;

public class ArmeroLaunch {

    public static void main(String[] args) throws IOException {
        // http://vulcan.wr.usgs.gov/Volcanoes/Colombia/Ruiz/description_eruption_lahar_1985.html
        // Armero, Colombia, destroyed by lahar on November 13, 1985.More than 23,000 people were killed in Armero when lahars (volcanic debris flows) swept down from the erupting Nevado del Ruiz
        // volcano.
        Bootstrap.main(args, new Runnable() {

            @Override
            public void run() {
            }
        }, new Runnable() {

            @Override
            public void run() {
                HttpServerConfig.releaseExternalResources();
            }
        }, new String[] {
            "classpath:/armeroApplicationContext.xml"
        });
    }
}
