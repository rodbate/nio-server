package com.xunlei.netty.httpserver.cmd.common;

import static ch.qos.logback.core.CoreConstants.LINE_SEPARATOR;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.helpers.Transform;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import com.xunlei.netty.httpserver.cmd.BaseStatCmd;
import com.xunlei.netty.httpserver.cmd.annotation.CmdAdmin;
import com.xunlei.netty.httpserver.component.XLHttpRequest;
import com.xunlei.netty.httpserver.component.XLHttpResponse;
import com.xunlei.netty.httpserver.component.XLHttpResponse.ContentType;
import com.xunlei.util.Log;

/**
 * 日志信息
 * 
 * @author ZengDong
 * @since 2010-5-23 上午12:15:48
 */
@Service
public class LoggerCmd extends BaseStatCmd {

    private static class StatusHelper {

        private String abbreviatedOrigin(Status s) {
            Object o = s.getOrigin();
            if (o == null) {
                return null;
            }
            String fqClassName = o.getClass().getName();
            int lastIndex = fqClassName.lastIndexOf(CoreConstants.DOT);
            if (lastIndex != -1) {
                return fqClassName.substring(lastIndex + 1, fqClassName.length());
            } else {
                return fqClassName;
            }
        }

        public String getStatus(XLHttpRequest request) {
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            StatusManager sm = lc.getStatusManager();

            StringBuilder output = new StringBuilder();

            output.append("<html>\r\n");
            output.append("<head>\r\n");
            printCSS(output);
            output.append("</head>\r\n");
            output.append("<body>\r\n");
            output.append("<h2>Status messages for LoggerContext named [");
            output.append(lc.getName());
            output.append("]</h2>\r\n");

            output.append("<form method=\"POST\">\r\n");
            output.append("<input type=\"submit\" name=\"submit\" value=\"clear\">");
            output.append("</form>\r\n");

            if ("CLEAR".equalsIgnoreCase(request.getParameter("submit"))) {
                sm.clear();
                sm.add(new InfoStatus("Cleared all status messages", this));
            }

            output.append("<table>");
            StringBuilder buf = new StringBuilder();
            if (sm != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                int count = 0;
                buf.append("<table>\r\n");
                printHeader(buf);
                List<Status> statusList = sm.getCopyOfStatusList();
                for (Status s : statusList) {
                    count++;
                    printStatus(buf, s, count, sdf);
                }
                buf.append("</table>\r\n");
            } else {
                output.append("Could not find status manager");
            }
            output.append(buf);
            output.append("</table>");
            output.append("</body>\r\n");
            output.append("</html>\r\n");

            return output.toString();
        }

        public String getStatusSimple() {
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            StatusManager sm = lc.getStatusManager();

            StringBuilder output = new StringBuilder("");
            output.append("Status messages for LoggerContext named [");
            output.append(lc.getName());
            output.append("]\n");

            String fmt = "%-21s%-8s%-80s %s\n";
            if (sm != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                List<Status> statusList = sm.getCopyOfStatusList();
                output.append(String.format(fmt, "Date", "Level", "Message", "Origin"));
                for (Status s : statusList) {
                    output.append(String.format(fmt, sdf.format(s.getDate()), statusLevelAsString1(s), s.getMessage(), abbreviatedOrigin(s)));
                    if (s.getThrowable() != null) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        s.getThrowable().printStackTrace(pw);
                        output.append(sw.getBuffer()).append("\n");
                    }
                }
            } else {
                output.append("Could not find status manager");
            }
            return output.toString();
        }

        private void printCSS(StringBuilder output) {
            output.append("  <STYLE TYPE=\"text/css\">\r\n");
            output.append("    .warn  { font-weight: bold; color: #FF6600;} \r\n"); // orange
            output.append("    .error { font-weight: bold; color: #CC0000;} \r\n");
            output.append("    table { margin-left: 2em; margin-right: 2em; border-left: 2px solid #AAA; }\r\n");
            output.append("    tr.even { background: #FFFFFF; }\r\n");
            output.append("    tr.odd  { background: #EAEAEA; }\r\n");
            output.append("    td { padding-right: 1ex; padding-left: 1ex; border-right: 2px solid #AAA; }\r\n");
            output.append("    td.date { text-align: right; font-family: courier, monospace; font-size: smaller; }");
            output.append(LINE_SEPARATOR);

            output.append("  td.level { text-align: right; }");
            output.append(LINE_SEPARATOR);
            output.append("    tr.header { background: #596ED5; color: #FFF; font-weight: bold; font-size: larger; }");
            output.append(CoreConstants.LINE_SEPARATOR);

            output.append("  td.exception { background: #A2AEE8; white-space: pre; font-family: courier, monospace;}");
            output.append(LINE_SEPARATOR);

            output.append("  </STYLE>\r\n");
        }

        private void printHeader(StringBuilder buf) {
            buf.append("  <tr class=\"header\">\r\n");
            buf.append("    <th>Date </th>\r\n");
            buf.append("    <th>Level</th>\r\n");
            buf.append("    <th>Origin</th>\r\n");
            buf.append("    <th>Message</th>\r\n");
            buf.append("  </tr>\r\n");
        }

        private void printStatus(StringBuilder buf, Status s, int count, SimpleDateFormat sdf) {
            String trClass;
            if (count % 2 == 0) {
                trClass = "even";
            } else {
                trClass = "odd";
            }
            buf.append("  <tr class=\"").append(trClass).append("\">\r\n");
            String dateStr = sdf.format(s.getDate());
            buf.append("    <td class=\"date\">").append(dateStr).append("</td>\r\n");
            buf.append("    <td class=\"level\">").append(statusLevelAsString(s)).append("</td>\r\n");
            buf.append("    <td>").append(abbreviatedOrigin(s)).append("</td>\r\n");
            buf.append("    <td>").append(s.getMessage()).append("</td>\r\n");
            buf.append("  </tr>\r\n");
            if (s.getThrowable() != null) {
                printThrowable(buf, s.getThrowable());
            }
        }

        private void printThrowable(StringBuilder buf, Throwable t) {
            buf.append("  <tr>\r\n");
            buf.append("    <td colspan=\"4\" class=\"exception\"><pre>");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            buf.append(Transform.escapeTags(sw.getBuffer()));
            buf.append("    </pre></td>\r\n");
            buf.append("  </tr>\r\n");

        }

        private String statusLevelAsString(Status s) {
            switch (s.getEffectiveLevel()) {
            case Status.INFO:
                return "INFO";
            case Status.WARN:
                return "<span class=\"warn\">WARN</span>";
            case Status.ERROR:
                return "<span class=\"error\">ERROR</span>";
            }
            return null;
        }

        private String statusLevelAsString1(Status s) {
            switch (s.getEffectiveLevel()) {
            case Status.INFO:
                return "INFO";
            case Status.WARN:
                return "WARN";
            case Status.ERROR:
                return "ERROR";
            }
            return null;
        }
    }

    private static final Logger log = Log.getLogger();
    private static final boolean logbackEngine = isLogback();

    private static final StatusHelper statusHelper = new StatusHelper();

    private static boolean isLogback() {
        try {
            if (log instanceof ch.qos.logback.classic.Logger) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private static String list(boolean all) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        String fmt = "%-20s%-20s%s\n";
        StringBuilder sb = new StringBuilder(String.format(fmt, "Level", "EffectiveLevel", "Name"));
        for (Logger log : loggerContext.getLoggerList()) {
            ch.qos.logback.classic.Logger l = (ch.qos.logback.classic.Logger) log;
            if (!all && l.getLevel() == null) {
                continue;
            }
            sb.append(String.format("%-20s%-20s%s\n", l.getLevel(), l.getEffectiveLevel(), l.getName()));
        }
        return sb.toString();
    }

    @CmdAdmin(reportToArmero = false)
    public Object list(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        return list(false);
    }

    @CmdAdmin(reportToArmero = false)
    public Object listAll(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        return list(true);
    }

    /**
     * 实时设置logger根的level
     */
    @CmdAdmin(reportToArmero = false)
    public Object setLevel(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        if (!logbackEngine) {
            return "Logger isnot logback:" + log.getClass();
        }

        String level = request.getParameterCompelled("level");
        String name = request.getParameter("name", Logger.ROOT_LOGGER_NAME);
        boolean force = request.getParameterBoolean("f", false);
        Logger tmp = LoggerFactory.getLogger(name);
        ch.qos.logback.classic.Logger l = (ch.qos.logback.classic.Logger) tmp;
        Level ori = l.getLevel();
        Level oriE = l.getEffectiveLevel();
        if (!force && ori == null) {
            return MessageFormat.format("Logger[{0}]'s level is null,can''t set to [{1}],please use ''?f=true''", name, level);
        }
        l.setLevel(Level.toLevel(level, Level.ERROR));
        Level now = l.getLevel();
        Level nowE = l.getEffectiveLevel();
        String fmt = "%-20s%-20s%-20s\n";
        StringBuilder sb = new StringBuilder("Logger[").append(name).append("]'s level setted:\n");
        sb.append(String.format(fmt, "", "Level", "EffectiveLevel"));
        sb.append(String.format(fmt, "ORI", ori, oriE));
        sb.append(String.format(fmt, "NOW", now, nowE));
        String r = sb.toString();
        if (now.equals(Level.OFF)) {
            System.err.print(r);
        }
        log.error(r);
        return sb.append("\n\n").append(list(false));
    }

    @CmdAdmin(reportToArmero = false)
    public Object status(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        return statusHelper.getStatusSimple();
    }

    @CmdAdmin(reportToArmero = false)
    public Object statusHtml(XLHttpRequest request, XLHttpResponse response) throws Exception {
        init(request, response);
        response.setInnerContentType(ContentType.html);
        return statusHelper.getStatus(request);
    }
}
