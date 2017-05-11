package photos.example.com.photos;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.DrawableRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    CarouselListAdapter adapter;
    private GridLayoutManager mManager;
    private RecyclerView imageList;
    private EditText searchText;
    private static Map<Integer, DisplayImageOptions> sDisplayOptions =
            new HashMap<Integer, DisplayImageOptions>();
    private ArrayList<String> loadUrls = new ArrayList<String>();
    String errorMsg = null;
    private AdapterView.OnItemClickListener mClickListener;
    private int NUMBER_OF_COLUMNS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adapter = new CarouselListAdapter(MainActivity.this);
        imageList = (RecyclerView) findViewById(R.id.carousel_list);
        searchText = (EditText) findViewById(R.id.my_editor);
        searchText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    new RetrieveTweetsTask().execute(searchText.getText().toString());
                    return true;
                }
                return false;
            }
        });

        imageList.setAdapter(adapter);

        mManager = new GridLayoutManager(this, NUMBER_OF_COLUMNS);
        imageList.setLayoutManager(mManager);

        ImageLoader.getInstance().init(
                new ImageLoaderConfiguration.Builder(this)
                        .threadPoolSize(12)
                        .threadPriority(Thread.MAX_PRIORITY)
                        .tasksProcessingOrder(QueueProcessingType.LIFO)
                        .writeDebugLogs()
                        .build());


    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

    }

    private class CarouselListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        public static final int LIST_ITEM = 0;


        private ArrayList<String> urls = new ArrayList<String>();

        public CarouselListAdapter(AdapterView.OnItemClickListener clickListener) {
            mClickListener = clickListener;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int type) {
            View v = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.item_layout, viewGroup, false);
            return new ImageLayoutHolder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int i) {
            String url = urls.get(i);
            if (viewHolder instanceof ImageLayoutHolder) {
                final ImageLayoutHolder vh = (ImageLayoutHolder) viewHolder;
                loadImageIntoView(getApplicationContext(), url, vh.photoView, 0);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return LIST_ITEM;
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        public void set(ArrayList<String> myUrls) {
            urls.clear();
            urls.addAll(myUrls);
        }

        private class ImageLayoutHolder extends RecyclerView.ViewHolder {

            public ImageView photoView;

            public ImageLayoutHolder(View v) {
                super(v);
                photoView = (ImageView) v.findViewById(R.id.image_grid);
            }
        }

        public void removeListeners() {
            mClickListener = null;
        }
    }

    public static void loadImageIntoView(Context context, String imageUrl, ImageView imageView,
                                         @DrawableRes int placeholder) {
        if (TextUtils.isEmpty(imageUrl)) {
            return;
        }

        DisplayImageOptions options = sDisplayOptions.get(placeholder);

        if (placeholder == 0) {
            if (options == null) {
                options = new DisplayImageOptions.Builder()
                        .cacheInMemory(true)
                        .cacheOnDisk(true)
                        .resetViewBeforeLoading(true)
                        .imageScaleType(ImageScaleType.EXACTLY)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build();
                sDisplayOptions.put(placeholder, options);
            }

            ImageLoader.getInstance().displayImage(imageUrl, new ImageViewAware(imageView, false), options);
        } else {
            if (options == null) {
                options = new DisplayImageOptions.Builder()
                        .showImageOnLoading(placeholder)
                        .showImageForEmptyUri(placeholder)
                        .showImageOnFail(placeholder)
                        .cacheInMemory(true)
                        .cacheOnDisk(true)
                        .resetViewBeforeLoading(true)
                        .imageScaleType(ImageScaleType.EXACTLY)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build();
                sDisplayOptions.put(placeholder, options);
            }

            ImageLoader.getInstance().displayImage(imageUrl, new ImageViewAware(imageView, false), options);
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case Constants.REFRESH_DATA:
                    loadUrls = (ArrayList<String>) msg.obj;
                    adapter.set(loadUrls);
                    adapter.notifyDataSetChanged();
                    break;
                case Constants.ERROR_XXX:
                    errorMsg = (String) msg.obj;
            }
        }
    };

    public class RetrieveTweetsTask extends AsyncTask<String, Void, Void> {

        int statusCode;
        String results;
        String farm = null;
        String server = null;
        String id = null;
        String secret = null;

        ArrayList<String> photoUrls = new ArrayList<>();

        @Override
        protected Void doInBackground(String... keyword) {
            try {
                String searchUrl = "https://api.flickr.com/services/rest/?method=flickr.photos.search&api_key=3e7cc266ae2b0e0d78e279ce8e361736&format=json&nojsoncallback=1&text="+keyword[0];
                URL url= new URL(searchUrl);
                HttpURLConnection getConnection = (HttpURLConnection)url.openConnection();
                getConnection.setRequestMethod("GET");
                getConnection.setRequestProperty("Content-Type", "application/json");
                // update the results with the body of the response
                statusCode = getConnection.getResponseCode();
                if(statusCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(getConnection.getInputStream()));
                    String inputLine;
                    StringBuffer response = new StringBuffer();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    results = response.toString();
                }

                if (results != null || !results.isEmpty()) {
                    JSONObject root = new JSONObject(results);
                    JSONObject photosParent = root.getJSONObject("photos");
                    JSONArray photos = photosParent.getJSONArray("photo");

                    for (int i = 0; i < photos.length(); i++) {
                        JSONObject session = photos.getJSONObject(i);
                        farm = session.getString("farm");
                        server = session.getString("server");
                        id = session.getString("id");
                        secret = session.getString("secret");

                        Photo photo = new Photo(farm, server, id, secret);
                        photoUrls.add(photo.getUrl());
                    }
                }
            } catch (Exception e){
                Message msg = new Message();
                msg.what = Constants.ERROR_XXX;
                msg.obj = getApplicationContext().getString(R.string.retrieve_error);
                mHandler.sendMessage(msg);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            Message msg = new Message();
            msg.what = Constants.REFRESH_DATA;
            msg.obj = photoUrls;
            mHandler.sendMessage(msg);


        }
    }

}

