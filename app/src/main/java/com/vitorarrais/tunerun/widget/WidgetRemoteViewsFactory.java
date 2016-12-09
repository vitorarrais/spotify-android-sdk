package com.vitorarrais.tunerun.widget;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Binder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.vitorarrais.tunerun.R;
import com.vitorarrais.tunerun.data.HistoryContentProvider;
import com.vitorarrais.tunerun.data.HistoryTable;

/**
 * Created by User on 11/04/2016.
 */
public class WidgetRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private Context mContext;
    private Intent mIntent;
    private Cursor mData;

    public WidgetRemoteViewsFactory(Context mContext, Intent mIntent) {
        this.mContext = mContext;
        this.mIntent = mIntent;
    }


    @Override
    public void onCreate() {
        //mData = null;
        mData = mContext.getContentResolver().query(HistoryContentProvider.CONTENT_URI,
                new String[]{HistoryTable.COLUMN_ID, HistoryTable.COLUMN_DATE, HistoryTable.COLUMN_DISTANCE},
                null,
                null,
                null);

    }

    @Override
    public void onDataSetChanged() {
        if (mData != null) {
            mData.close();
            mData = null;
        }
        final long identityToken = Binder.clearCallingIdentity();
        Log.d(WidgetProvider.class
        .getSimpleName(), "onDataSetChanged");
        mData = mContext.getContentResolver().query(HistoryContentProvider.CONTENT_URI,
                new String[]{HistoryTable.COLUMN_ID, HistoryTable.COLUMN_DATE, HistoryTable.COLUMN_DISTANCE},
                null,
                null,
                null);
        Binder.restoreCallingIdentity(identityToken);
    }

    @Override
    public void onDestroy() {
        if (mData != null) {
            mData.close();
            mData = null;
        }
    }

    @Override
    public int getCount() {
        return mData == null ? 0 : mData.getCount();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews views = new RemoteViews(mContext.getPackageName(), R.layout.history_item);
        DatabaseUtils.dumpCursor(mData);
        mData.moveToPosition(position);
        views.setTextViewText(R.id.date, mData.getString(mData.getColumnIndex(HistoryTable.COLUMN_DATE)));
        views.setTextViewText(R.id.distance, mData.getString(mData.getColumnIndex(HistoryTable.COLUMN_DISTANCE)));

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return new RemoteViews(mContext.getPackageName(), R.layout.history_item);
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }
}
