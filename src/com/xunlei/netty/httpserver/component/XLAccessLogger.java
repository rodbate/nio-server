/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2009, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package com.xunlei.netty.httpserver.component;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import ch.qos.logback.access.joran.JoranConfigurator;
import ch.qos.logback.access.spi.AccessEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import ch.qos.logback.core.spi.FilterAttachable;
import ch.qos.logback.core.spi.FilterAttachableImpl;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.status.WarnStatus;
import ch.qos.logback.core.util.StatusPrinter;
import com.xunlei.util.concurrent.ConcurrentUtil;

/**
 * This class is an implementation of tomcat's Valve interface, by extending ValveBase.
 * <p>
 * For more information on using LogbackValve please refer to the online documentation on <a href="http://logback.qos.ch/access.html#tomcat">logback-acces and tomcat</a>.
 * 
 * @author Ceki G&uuml;lc&uuml;
 * @author S&eacute;bastien Pennec
 */
public class XLAccessLogger implements Context, AppenderAttachable<AccessEvent>, FilterAttachable<AccessEvent> {

    public final static String DEFAULT_CONFIG_FILE = "logback-access.xml";

    private long birthTime = System.currentTimeMillis();
    Object configurationLock = new Object();

    // Attributes from ContextBase:
    private String name;
    StatusManager sm = new BasicStatusManager();
    // TODO propertyMap should be observable so that we can be notified
    // when it changes so that a new instance of propertyMap can be
    // serialized. For the time being, we ignore this shortcoming.
    Map<String, String> propertyMap = new HashMap<String, String>();
    Map<String, Object> objectMap = new HashMap<String, Object>();
    private FilterAttachableImpl<AccessEvent> fai = new FilterAttachableImpl<AccessEvent>();

    AppenderAttachableImpl<AccessEvent> aai = new AppenderAttachableImpl<AccessEvent>();
    String filename;
    boolean quiet;
    boolean started;
    boolean alreadySetLogbackStatusManager = false;

    public XLAccessLogger() {
        start();
        putObject(CoreConstants.EVALUATOR_MAP, new HashMap<Object, Object>());
    }

    public XLAccessLogger(String filename) {
        try {
            this.filename = getClass().getClassLoader().getResource(filename).getFile();
            getStatusManager().add(new InfoStatus("loading accessContext file [" + filename + "]", this));
        } catch (Throwable e) {
            getStatusManager().add(new WarnStatus("[" + filename + "] does not exist", this));
            throw new RuntimeException();
        }

        start();
        putObject(CoreConstants.EVALUATOR_MAP, new HashMap<Object, Object>());
    }

    private boolean logEnable = true;
    private boolean logSuccess = false;
    private InnerLog logimpl;
    private static final InnerLog NOP_LOG = new InnerLog();
    private final InnerLog defaultLog = new DefaultLog();

    public void setLogEanble(boolean enable) {
        this.logEnable = enable;
        if (logEnable && logSuccess) {
            this.logimpl = defaultLog;
        } else {
            this.logimpl = NOP_LOG;
        }
    }

    public void start() {
        try {
            try {
                AccessEvent.crackTest();
            } catch (Throwable e) {
                String info = "logback-access is no crack for nettyHttpServer,make sure logaccess_crack.jar's class path order is prior to ori_logaccess.jar";
                getStatusManager().add(new ErrorStatus(info, this));
                return;
            }

            if (filename == null) {
                try {
                    filename = getClass().getClassLoader().getResource(DEFAULT_CONFIG_FILE).getFile();
                    getStatusManager().add(new InfoStatus("filename property not set. Assuming [" + filename + "]", this));
                } catch (Throwable e) {
                    getStatusManager().add(new WarnStatus("[" + DEFAULT_CONFIG_FILE + "] does not exist", this));
                    return;
                }
            }
            File configFile = new File(filename);
            if (configFile.exists()) {
                try {
                    JoranConfigurator jc = new JoranConfigurator();
                    jc.setContext(this);
                    jc.doConfigure(filename);
                    logSuccess = true;
                } catch (JoranException e) {
                    // TODO can we do better than printing a stack trace on syserr?
                    // e.printStackTrace();
                    getStatusManager().add(new ErrorStatus("configure logback-access error", this, e));
                }
            } else {
                getStatusManager().add(new WarnStatus("[" + filename + "] does not exist", this));
            }

        } catch (Throwable e) {
            getStatusManager().add(new ErrorStatus("configure logback-access error", this, e));
        } finally {
            if (!quiet) {
                StatusPrinter.print(getStatusManager());
            }
            setLogEanble(true);
            started = true;
        }
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    private class DefaultLog extends InnerLog {

        @Override
        public void log(XLHttpRequest request, XLHttpResponse response) {
            // try {
            // if (!alreadySetLogbackStatusManager) {
            // alreadySetLogbackStatusManager = true;
            // org.apache.catalina.Context tomcatContext = request.getContext();
            // if (tomcatContext != null) {
            // ServletContext sc = tomcatContext.getServletContext();
            // if (sc != null) {
            // sc.setAttribute(AccessConstants.LOGBACK_STATUS_MANAGER_KEY,
            // getStatusManager());
            // }
            // }
            // }
            AccessEvent accessEvent = new AccessEvent(request, response);
            if (getFilterChainDecision(accessEvent) == FilterReply.DENY) {
                return;
            }
            aai.appendLoopOnAppenders(accessEvent);
            // } finally {
            // request.removeAttribute(AccessConstants.LOGBACK_STATUS_MANAGER_KEY);
            // }
        }

        @Override
        public void log(AccessEvent accessEvent) {
            if (getFilterChainDecision(accessEvent) == FilterReply.DENY) {
                return;
            }
            aai.appendLoopOnAppenders(accessEvent);
        }

    }

    private static class InnerLog {

        public void log(AccessEvent accessEvent) {
        }

        public void log(XLHttpRequest request, XLHttpResponse response) {
        }
    }

    public void log(XLHttpRequest request, XLHttpResponse response) {
        logimpl.log(request, response);
    }

    public void log(AccessEvent accessEvent) {
        logimpl.log(accessEvent);
    }

    public void stop() {
        started = false;
    }

    @Override
    public void addAppender(Appender<AccessEvent> newAppender) {
        aai.addAppender(newAppender);
    }

    @Override
    public Iterator<Appender<AccessEvent>> iteratorForAppenders() {
        return aai.iteratorForAppenders();
    }

    @Override
    public Appender<AccessEvent> getAppender(String name) {
        return aai.getAppender(name);
    }

    @Override
    public boolean isAttached(Appender<AccessEvent> appender) {
        return aai.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
        aai.detachAndStopAllAppenders();

    }

    @Override
    public boolean detachAppender(Appender<AccessEvent> appender) {
        return aai.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String name) {
        return aai.detachAppender(name);
    }

    public String getInfo() {
        return "Logback's implementation of ValveBase";
    }

    // Methods from ContextBase:
    @Override
    public StatusManager getStatusManager() {
        return sm;
    }

    public Map<String, String> getPropertyMap() {
        return propertyMap;
    }

    @Override
    public void putProperty(String key, String val) {
        this.propertyMap.put(key, val);
    }

    @Override
    public String getProperty(String key) {
        return this.propertyMap.get(key);
    }

    @Override
    public Map<String, String> getCopyOfPropertyMap() {
        return new HashMap<String, String>(this.propertyMap);
    }

    @Override
    public Object getObject(String key) {
        return objectMap.get(key);
    }

    @Override
    public void putObject(String key, Object value) {
        objectMap.put(key, value);
    }

    @Override
    public void addFilter(Filter<AccessEvent> newFilter) {
        fai.addFilter(newFilter);
    }

    @Override
    public void clearAllFilters() {
        fai.clearAllFilters();
    }

    @Override
    public List<Filter<AccessEvent>> getCopyOfAttachedFiltersList() {
        return fai.getCopyOfAttachedFiltersList();
    }

    @Override
    public FilterReply getFilterChainDecision(AccessEvent event) {
        return fai.getFilterChainDecision(event);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        if (this.name != null) {
            throw new IllegalStateException("LogbackValve has been already given a name");
        }
        this.name = name;
    }

    @Override
    public Object getConfigurationLock() {
        return configurationLock;
    }

    @Override
    public long getBirthTime() {
        return birthTime;
    }

    ExecutorService executorService = ConcurrentUtil.getLogExecutor();// 用统一的，来进行统一管理

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }
}
