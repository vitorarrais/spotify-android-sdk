package com.vitorarrais.tunerun.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

/**
 * Created by User on 11/04/2016.
 */
public class WidgetRemoteViewsService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new WidgetRemoteViewsFactory(this.getApplicationContext(), intent);
    }

}
