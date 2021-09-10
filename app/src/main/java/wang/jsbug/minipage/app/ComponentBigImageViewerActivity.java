package wang.jsbug.minipage.app;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import wang.jsbug.minipage.R;

public class ComponentBigImageViewerActivity extends AppCompatActivity {
    ImageView image;
    Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_component_big_image_viewer);

        String url = "http://106.12.150.47/img/5eca3e24-d89c-4766-aacb-77bbc0db0d8d.png?size=750_2217";

        // 新建一个线程下载图片
        new Task().execute(url);
    }

    public Bitmap GetImageInputStream(String imageurl){
        URL url;
        HttpURLConnection connection=null;
        Bitmap bitmap=null;
        try {
            url = new URL(imageurl);
            connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(6000); //超时设置
            connection.setDoInput(true);
            connection.setUseCaches(false); //设置不使用缓存
            InputStream inputStream=connection.getInputStream();
            bitmap= BitmapFactory.decodeStream(inputStream);
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * 异步线程下载图片
     *
     */
    class Task extends AsyncTask<String, Integer, Void> {

        protected Void doInBackground(String... params) {
            bitmap=GetImageInputStream((String)params[0]);
            return null;
        }

        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            Message message=new Message();
            message.what=0x123;
            handler.sendMessage(message);
        }
    }

    Handler handler=new Handler(){
        public void handleMessage(android.os.Message msg) {
            if(msg.what==0x123){
                // 显示图片
                SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(R.id.imageView);
                // imageView.setImage(ImageSource.resource(R.drawable.failure_image));
                // imageView.setImage(ImageSource.uri(url));
                imageView.setImage(ImageSource.bitmap(bitmap));

                // image.setImageBitmap(bitmap);
            }
        };
    };
}