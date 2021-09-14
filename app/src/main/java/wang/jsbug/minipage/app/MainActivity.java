package wang.jsbug.minipage.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.blankj.utilcode.util.ResourceUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import wang.jsbug.minipage.BuildConfig;
import wang.jsbug.minipage.R;

/**
 * 首先明确2个术语：① Native ② JS
 * JS 调用 Native 设计为异步得到 Native 的执行结果
 * 因此我们封装一个 MinipageActivity ，作为其包含的 Webview 调用 Native 的桥梁。
 *
 * 这个 MinipageActivity 需要 3 个步骤（以 Native 方法通过 startActivityForResult(Intent intent, int requestCode) 执行 Native 操作为例）：
 * 1. 调用 Webview.addJavascriptInterface(Object obj, String interfaceName) 注入 Native 方法到 JS 。
 * 2. 通过 Activity.onActivityResult(int requestCode, int resultCode, Intent intent) 得到 Native 方法的执行结果
 * 3. 通过 WebView.evaluateJavascript(String script, ValueCallback<String> resultCallback) 异步通知 JS
 *
 * 具体异步回调怎么做呢？
 * 这就需要在 JS 层封装了，我们把这次叫做 JSSDK 。那以上的步骤细化如下：
 * ① 步骤1注入的方法，返回一个由 Native 生成的唯一 CallbackName （字符串类型），JSSDK 也【约定】用这个 CallbackName 定义一个接受异步结果的回调函数
 * ② 步骤3异步通知 JS，就是执行 JSSDK 定义的 CallbackName ，参数就是我们的数据啦
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private WebView webView;
    private boolean onPageFinished = false;
    private Uri imageUri; // 拍照的 Uri

    private int callbackId = 0;
    private String serviceName = "camera";

    private static final String CALLBACK_SUFFIX_SUCCESS = "Success";
    private static final String CALLBACK_SUFFIX_FAIL = "Fail";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String base = Environment.getExternalStorageDirectory().getAbsolutePath().toString();
        Log.d(TAG, base);

        webView = (WebView) findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        // 允许加载本地 file:// 文件
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);

        // `WebChromeClient` 辅助 WebView 处理 JS 的对话框，网站图标，网站标题，加载进度等
        WebChromeClient webChromeClient = new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                // JsResult有两个函数：confirm() 和 cancel()，confirm()表示点击了弹出框的确定按钮，cancel()则表示点击了弹出框的取消按钮。
                result.confirm();
                return true;
            }
        };
        webView.setWebChromeClient(webChromeClient);

        // `WebViewClient` 帮助 WebView 处理各种通知、请求事件：
        webView.setWebViewClient(new WebViewClient() {
            // 开始加载页面时调用，每个 frame 只调用一次
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // webView.setVisibility(View.GONE);
            }

            // TODO: 到期在哪个时间注入JS，以及注入成功后的 ready 事件
            // 仅 main frame 会触发。但是会触发 3 次。@see https://stackoverflow.com/questions/18282892/android-webview-onpagefinished-called-twice
            @Override
            public void onPageFinished(final WebView view, String url) {
                if (onPageFinished) {
                    return;
                }
                onPageFinished = true;

                injectJsSDK();

                webView.evaluateJavascript("javascript:location.hash='#/camera';console.log('执行了js')", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.d(TAG, "onReceiveValue(value): " + value);
                    }
                });
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });

        // 注入 Native Api 给 JS ，命名为 StringJSBridge , 因为数据结构（即对象、数组）类型的参数和返回值会被序列化成 String 再传输
        webView.addJavascriptInterface(new JavaScriptInterface(this), "StringJSBridge");

        // 加载唯一入口文件
        webView.loadUrl("file:///android_asset/html2image/dist/index.html");

        // 测试调用拍照功能
        // takePicture();
    }

    /**
     * 注入基础库JsSDK
     */
    private void injectJsSDK() {
        String script = "javascript:" + ResourceUtils.readAssets2String("jssdk/type-wrapper.js", "UTF-8");
        // ValueCallback 泛型实际只支持字符串。@see https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback%3Cjava.lang.String%3E)
        // UI 线程上调用，且 callback 也是在 UI 线程执行
        webView.evaluateJavascript(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.i(TAG, "value is " + value);
            }
        });
    }

    /**
     * addJavascriptInterface 方法注入的对象
     */
    public class JavaScriptInterface {
        private Context context;

        JavaScriptInterface(Context context) {
            this.context = context;
        }

        /**
         * @return Object
         * {
         * brand {String} 设备品牌
         * model {string} 设备型号
         * pixelRatio {number} 设备像素比
         * screenWidth {number} 屏幕宽度，单位px
         * screenHeight {number} 屏幕高度，单位px
         * windowWidth {number} 可使用窗口宽度，单位px
         * windowHeight {number} 可使用窗口高度，单位px
         * version {string} App的版本号
         * platform {string} 客户端平台，包含值：['iOS', 'Android']
         * VueVersion {string} Vue版本号
         * SDKVersion {string} 客户端基础库版本
         * albumAuthorized {boolean} 允许微信使用相册的开关（仅 iOS 有效）
         * cameraAuthorized {boolean} 允许微信使用摄像头的开关
         * wifiEnabled {boolean} Wi-Fi 的系统开关
         * }
         * <p>
         * 备注：对于数组类型，转换失败，Native 获取到的值为 null
         */
        @JavascriptInterface
        public String _getSystemInfo() {
            final float scale = getResources().getDisplayMetrics().density;
            // 获取屏幕的高宽
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics outMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(outMetrics);
            int screenWidth = outMetrics.widthPixels;
            int screenHeight = outMetrics.heightPixels;

            // 获取 Webview 的高宽
            webView.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int windowWidth = webView.getMeasuredWidth();
            int windowHeight = webView.getMeasuredHeight();

            JSONObject data = new JSONObject();
            try {
                data.put("pixelRatio", (int) scale);
                data.put("screenWidth", screenWidth);
                data.put("screenHeight", screenHeight);
                data.put("windowWidth", windowWidth);
                data.put("windowHeight", windowHeight);
                data.put("platform", "Android");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return data.toString();
        }

        @JavascriptInterface
        public String takePicture() {
            ((MainActivity) context).takePicture();
            return getUniqCallbackName();
        }
    }

    public void takePicture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // 在临时目录创建一个文件，文件名为 .Pic.后缀
        File photo = new File(getTempDirectoryPath(), ".Pic.jpg");
        // wang.jsbug.minipage.engine.plugin.camera.provider
        // 在外部存储或cache目录
        this.imageUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".engine.plugin.camera.provider", photo);
        // 设置图像输出 Uri
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        // 注意：多次调用，每次用唯一的 callbackId
        callbackId++;
        startActivityForResult(intent, callbackId);
    }

    /**
     * 获取唯一的Callback
     * @return
     */
    private String getUniqCallbackName() {
        return serviceName + callbackId;
    }

    /**
     * 拍完照异步通知 JS
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        /*
        if (resultCode == Activity.RESULT_OK) {
            // 得到新 Activity 关闭后返回的数据
            // 注意：如果内容是 base64 字符串，可以在 extra 获取，但是 Uri 类型的获取不到的
            // Bitmap bitmap = (Bitmap) intent.getExtras().get("data");

            // 从 Uri 得到 Bitmap ，然后显示到 ImageView
            InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                ImageView imageView = (ImageView) findViewById(R.id.imageView);
                imageView.setImageBitmap(bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
         */

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMG_" + timeStamp + ".jpg";
        File file = new File(getTempDirectoryPath(), imageFileName);
        JSONObject data = new JSONObject();
        if (resultCode == Activity.RESULT_OK) {
            try {
                // 从 Uri 得到 Bitmap ，然后显示到 ImageView
                InputStream inputStream = null;
                FileOutputStream out = new FileOutputStream(file);
                inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                // 把位图压缩转换成 JPG 图片。
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();

                data.put("imageUri", "file://" + file.getAbsolutePath());
                // 为了简化 JSSDK 的处理，回调总是JSON对象
                webView.evaluateJavascript("javascript:" + getUniqCallbackName()  + CALLBACK_SUFFIX_SUCCESS + "(" + data.toString() + ")", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.i(TAG, "value is " + value);
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // 失败的情况
        else {
            try {
                data.put("code", Activity.RESULT_OK);
                webView.evaluateJavascript("javascript:" + getUniqCallbackName()  + CALLBACK_SUFFIX_FAIL + "(" + data.toString() + ")", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.i(TAG, "value is " + value);
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    /**
     * 获取临时目录
     * @return
     */
    private String getTempDirectoryPath() {
        File cache = this.getCacheDir();
        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }
}