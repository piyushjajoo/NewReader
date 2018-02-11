package com.example.pjajoo.newsreader;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {

    private ArrayList<String> articleTitles;
    private ArrayList<String> articleUrls;
    private ArrayAdapter arrayAdapter;
    private SQLiteDatabase articlesDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ListView newsList = (ListView) findViewById(R.id.newsList);

        articleTitles = new ArrayList<>();
        articleUrls = new ArrayList<>();

        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, articleTitles);

        newsList.setAdapter(arrayAdapter);

        newsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Intent intent = new Intent(getApplicationContext(), ArticleActivity.class);
                intent.putExtra("articleUrl", articleUrls.get(position));
                startActivity(intent);
            }
        });

        articlesDB = this.openOrCreateDatabase("NewArticles", MODE_PRIVATE, null);

        articlesDB.execSQL("CREATE TABLE IF NOT EXISTS articles (id INT PRIMARY KEY, articleid INT, title VARCHAR, url VARCHAR)");

        updateListView();

        final DowloadTask dowloadTask = new DowloadTask();
        try {
            dowloadTask.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty").get();
        } catch (final Exception e) {
            e.printStackTrace();
        }

    }

    public void updateListView() {
        Cursor c = articlesDB.rawQuery("SELECT * FROM articles", null);
        int titleIndex = c.getColumnIndex("title");
        int urlIndex = c.getColumnIndex("url");

        if (c.moveToFirst()) {
            articleTitles.clear();
            do {
                articleTitles.add(c.getString(titleIndex));
                articleUrls.add(c.getString(urlIndex));
            } while (c.moveToNext());

            arrayAdapter.notifyDataSetChanged();
        }
    }

    public class DowloadTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... urls) {

            try {
                URL url = new URL(urls[0]);
                HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(in);

                int data = inputStreamReader.read();

                String result = "";
                while (data != -1) {
                    result += (char) data;
                    data = inputStreamReader.read();
                }
                Log.i("Story Ids", result);

                final JSONArray jsonArray = new JSONArray(result);

                int numberOfItems = 20;

                if (jsonArray.length() < numberOfItems) {
                    numberOfItems = jsonArray.length();
                }

                articlesDB.execSQL("DELETE FROM articles");

                for (int i = 0; i < numberOfItems; i++) {

                    final String articleId = jsonArray.getString(i);
                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" + articleId + ".json?print=pretty");
                    urlConnection = (HttpsURLConnection) url.openConnection();
                    in = urlConnection.getInputStream();
                    inputStreamReader = new InputStreamReader(in);

                    data = inputStreamReader.read();

                    result = "";
                    while (data != -1) {
                        result += (char) data;
                        data = inputStreamReader.read();
                    }
                    final JSONObject jsonObject = new JSONObject(result);
                    if (!jsonObject.isNull("title") && !jsonObject.isNull("url")) {
                        final String articleTitle = jsonObject.getString("title");
                        final String articleUrl = jsonObject.getString("url");
                        Log.i("Article Title", articleTitle);
                        Log.i("Article Url", articleUrl);
                        final String sql = "INSERT INTO articles (articleid, title, url) VALUES (?, ?, ?)";
                        SQLiteStatement statement = articlesDB.compileStatement(sql);
                        statement.bindString(1, articleId);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleUrl);
                        statement.execute();
                    }
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            updateListView();
        }
    }
}
