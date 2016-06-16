package armero.com.xunlei.armero;

import org.springframework.stereotype.Service;
import com.xunlei.spring.Config;

/**
 * @author 曾东
 * @since 2012-10-25 上午10:35:39
 */
@Service
public final class ArmeroUtil {

    @Config(resetable = true)
    private static long tokenExpireSec = 600;// 10分钟重取token
    @Config
    private static int listen_port = 2012;

    @Config(resetable = true)
    private static int httpAttempts = 3;
    @Config(resetable = true)
    private static int httpRetrySleepSec = 5;

    public static long getTokenExpireSec() {
        return tokenExpireSec;
    }

    public static String getRootUrl() {
        return "http://armero.cc.sandai.net:" + listen_port;
    }

    public static int getHttpAttempts() {
        return httpAttempts;
    }

    public static int getHttpRetrySleepSec() {
        return httpRetrySleepSec;
    }

}
