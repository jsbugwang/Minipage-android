package wang.jsbug.minipage.engine;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;

/**
 * MinipageActivity 实现的 Activity 接口
 */
public interface MinipageInterface {

    /**
     * 启动一个 Activity ，并且结束时返回一个结果。退出时 onActivityResult() 方法会被调用。
     * @param command
     * @param intent
     * @param requestCode
     */
    abstract public void startActivityForResult(/*CordovaPlugin command, */Intent intent, int requestCode);

    abstract public void setActivityResultCallback(/*CordovaPlugin plugin*/);


    /**
     * 获取当前的 Activity
     * @return 当前的 Activity
     */
    abstract public AppCompatActivity getActivity();

    /**
     * 获取当前的 Context
     * @return 当前的 Context
     */
    public Context getContext();

    /**
     * 插件收到消息的处理方法
     * @param id 消息ID
     * @param data 消息数据
     * @return 对象或 null
     */
    public Object onMessage(String id, Object data);

    /**
     * 向 activity 发送单个权限请求
     */
    public void requestPermission(/*CordovaPlugin plugin, */int requestCode, String permission);

    /**
     * 获取共享线程池，供后台任务使用
     * @return
     */
    public ExecutorService getThreadPool();

    /**
     * 向 activity 发送多个权限请求
     */
    public void requestPermissions(/*CordovaPlugin plugin, */int requestCode, String [] permissions);

    /**
     * 检测是否已授权单个权限
     */
    public boolean hasPermission(String permission);
}
