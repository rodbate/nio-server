package armero.com.xunlei.armero;

import java.util.Collections;
import java.util.List;
import com.xunlei.netty.httpserver.cmd.common.ArmeroReportCmd.CmdAdminItem;
import com.xunlei.netty.httpserver.cmd.common.ArmeroReportCmd.CmdManifest;
import com.xunlei.netty.httpserver.cmd.common.ArmeroReportCmd.CmdMonitorItem;

/**
 * @author 曾东
 * @since 2012-10-11 下午5:01:29
 */
public class MonitorItemManifest {

    private List<CmdMonitorItem> monitorItem;
    private List<CmdAdminItem> adminItem;

    public MonitorItemManifest(CmdManifest cm) {
        if (cm == null) {
            this.monitorItem = Collections.emptyList();
            this.adminItem = Collections.emptyList();
        } else {
            this.monitorItem = cm.getMonitorItem();
            this.adminItem = cm.getAdminItem();
        }
    }

    public List<CmdMonitorItem> getMonitorItem() {
        return monitorItem;
    }

    public List<CmdAdminItem> getAdminItem() {
        return adminItem;
    }

    public int getLength() {
        return monitorItem == null ? 0 : monitorItem.size();
    }
}
