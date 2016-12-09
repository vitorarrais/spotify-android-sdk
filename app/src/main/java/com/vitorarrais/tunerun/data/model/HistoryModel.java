package com.vitorarrais.tunerun.data.model;

import java.util.List;

/**
 * Created by User on 29/11/2016.
 */

public class HistoryModel {

    private long _id;
    private String date;
    private String distance;
    private List<LocationModel> path;


    public HistoryModel() {
    }

    public long get_id() {
        return _id;
    }

    public void set_id(long _id) {
        this._id = _id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public List<LocationModel> getPath() {
        return path;
    }

    public void setPath(List<LocationModel> path) {
        this.path = path;
    }
}
