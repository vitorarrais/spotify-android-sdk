package com.vitorarrais.tunerun;

import android.app.ActivityOptions;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.vitorarrais.tunerun.data.HistoryContentProvider;
import com.vitorarrais.tunerun.data.HistoryTable;
import com.vitorarrais.tunerun.data.model.HistoryModel;
import com.vitorarrais.tunerun.data.model.LocationModel;
import com.vitorarrais.tunerun.rest.HistoryResource;

import java.io.IOException;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;

public class HistoryActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    public static String PATH = "path";
    public static String EXTRA_USER_ID = "userId";

    private Toolbar mToolbar;

    @BindView(R.id.history_list_view)
    protected ListView mHistoryListView;


    private SimpleCursorAdapter mDataAdapter;

    private FirebaseDatabase mDatabase;
    private DatabaseReference mHistoryRef;

    private String mCurrentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        ButterKnife.bind(this);

        mCurrentUserId = getIntent().getStringExtra(EXTRA_USER_ID);

        mDatabase = FirebaseDatabase.getInstance();
        mHistoryRef = mDatabase.getReference(getResources().getString(R.string.history_ref));

        mToolbar = (Toolbar) findViewById(R.id.toolbar); // Attaching the layout to the toolbar object
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        prepareHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Starts a new or restarts an existing Loader in this manager
        getSupportLoaderManager().restartLoader(0, null, this);
    }

    private void prepareHistory() {
        String[] columns = new String[]{
                HistoryTable.COLUMN_DATE,
                HistoryTable.COLUMN_DISTANCE,
        };

        int[] to = new int[]{
                R.id.date,
                R.id.distance
        };

        getSupportLoaderManager().initLoader(0, null, this);

        mDataAdapter = new SimpleCursorAdapter(
                this,
                R.layout.history_item,
                null,
                columns,
                to,
                0);

        mHistoryListView.setAdapter(mDataAdapter);

        mHistoryListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                retrieveDataById(l);
            }
        });

    }

    private void retrieveDataById(long id) {
        String idString = String.valueOf(id);
        HistoryResource service = HistoryResource.retrofit.create(HistoryResource.class);

        Call<HistoryModel> call = service.historyItem(mCurrentUserId, idString);

        new retrieveDataTask().execute(call);

    }


    private class retrieveDataTask extends AsyncTask<Call<HistoryModel>, Void, HistoryModel> {


        @Override
        protected HistoryModel doInBackground(Call<HistoryModel>... calls) {
            Call<HistoryModel> call = calls[0];
            HistoryModel model = null;
            try {
                model = call.execute().body();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return model;
        }

        @Override
        protected void onPostExecute(HistoryModel result) {
            Intent i = new Intent(HistoryActivity.this, HistoryMapActivity.class);
            ArrayList<LocationModel> path;
            if (result.getPath() != null && !result.getPath().isEmpty()) {
                path = (ArrayList<LocationModel>) result.getPath();
                i.putParcelableArrayListExtra(PATH, path);
            }
            startActivity(i, ActivityOptions.makeSceneTransitionAnimation(HistoryActivity.this).toBundle());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle arrow click here
        if (item.getItemId() == android.R.id.home) {
            finish(); // close this activity and return to preview activity (if there is any)
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection = {HistoryTable.COLUMN_ID, HistoryTable.COLUMN_DATE, HistoryTable.COLUMN_DISTANCE};
        CursorLoader cursorLoader = new CursorLoader(this,
                HistoryContentProvider.CONTENT_URI, projection, null, null, null);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
// Swap the new cursor in.  (The framework will take care of closing the
        // old cursor once we return.)
        mDataAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        mDataAdapter.swapCursor(null);
    }
}
