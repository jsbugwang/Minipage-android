package wang.jsbug.minipage.engine;

import android.webkit.WebView;

/**
 * 插件需要继承这个类，并且要重载其中一个 execute 方法
 */
public class MinipagePlugin {
    public WebView webView;
    public MinipageInterface minipage;
    private String serviceName;

    public String getServiceName() {
        return serviceName;
    }
}
