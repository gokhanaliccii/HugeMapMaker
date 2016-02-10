package com.wanari.infinitemarker.overlay;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.wanari.infinitemarker.data.LatLngWrapper;
import com.wanari.infinitemarker.renderer.DefaultMarkerRenderer;
import com.wanari.infinitemarker.renderer.IMarkerRenderer;
import com.wanari.infinitemarker.renderer.RenderParams;
import com.wanari.infinitemarker.rx.ParallelRendererFunc1;
import com.wanari.infinitemarker.rx.VisibleBoundFilter;
import com.wanari.infinitemarker.utils.PositionUtils;

import java.util.List;

import rx.Observable;
import rx.functions.Action1;
import timber.log.Timber;

/**
 * Created by agocs on 2016.01.20..
 */
public class OverlayFactory implements RenderParams {

    public static final String TAG = "OverlayFactory";
    public static final int DEFAULT_WIDTH_PX = 600;
    public static final int PARALLEL_DRAWING_TASK = 4;

    double south = Double.POSITIVE_INFINITY;
    double west = Double.POSITIVE_INFINITY;
    double north = Double.NEGATIVE_INFINITY;
    double east = Double.NEGATIVE_INFINITY;

    float widthInMeters;
    float heightInMeters;

    private IMarkerRenderer markerRenderer = new DefaultMarkerRenderer();
    private Context context;

    private LatLngBounds drawingBounds;
    private int calculatedHeight;

    public OverlayFactory(Context context) {
        this.context = context;
    }

    public Observable<GroundOverlayOptions> generateMapOverlay(@NonNull GoogleMap map, @NonNull List<? extends LatLngWrapper> latLngList) {
        Log.i(TAG, "Unfiltered pos count: " + latLngList.size());
        return filterInBoundLatLng(latLngList, map.getProjection().getVisibleRegion().latLngBounds)
                .doOnNext(new Action1<List<LatLngWrapper>>() {
                    @Override
                    public void call(List<LatLngWrapper> latLngList) {
                        Timber.i("Filtered pos count: " + latLngList.size());
                        drawingBounds = new LatLngBounds(new LatLng(south, west), new LatLng(north, east));
                        Timber.i("DrawingBounds: " + drawingBounds.toString());
                        widthInMeters = PositionUtils.calculateHaversineDistance(drawingBounds.northeast.latitude, drawingBounds.northeast.longitude, drawingBounds.northeast.latitude, drawingBounds.southwest.longitude);
                        heightInMeters = PositionUtils.calculateHaversineDistance(drawingBounds.northeast.latitude, drawingBounds.northeast.longitude, drawingBounds.southwest.latitude, drawingBounds.northeast.longitude);
                        Timber.i("Overlay dimensions: " + widthInMeters + "x" + heightInMeters + " meters");
                        double imageWidthHeightRatio = widthInMeters / heightInMeters;
                        calculatedHeight = (int) (DEFAULT_WIDTH_PX / imageWidthHeightRatio);
                        Timber.i("Calculated canvas dimension: " + DEFAULT_WIDTH_PX + "x" + calculatedHeight);
                    }
                })
                .flatMap(new ParallelRendererFunc1(context, map, PARALLEL_DRAWING_TASK, markerRenderer, this));

    }

    private Observable<List<LatLngWrapper>> filterInBoundLatLng(List<? extends LatLngWrapper> latLngList, LatLngBounds bounds) {
        return Observable.from(latLngList)
                .filter(new VisibleBoundFilter(bounds))
                .doOnNext(new Action1<LatLngWrapper>() {
                    @Override
                    public void call(LatLngWrapper latLngWrapper) {
                        final LatLng latLng = latLngWrapper.getLatLng();
                        if (latLng.longitude > east) {
                            east = latLng.longitude;
                        }
                        if (latLng.longitude < west) {
                            west = latLng.longitude;
                        }

                        if (latLng.latitude > north) {
                            north = latLng.latitude;
                        }
                        if (latLng.latitude < south) {
                            south = latLng.latitude;
                        }
                    }
                })
                .toList();
    }

    @Override
    public LatLngBounds getDrawingBounds() {
        return drawingBounds;
    }

    @Override
    public int getOutputHeight() {
        return calculatedHeight;
    }

    @Override
    public int getOutputWidth() {
        return DEFAULT_WIDTH_PX;
    }

    @Override
    public float getHeightInMeter() {
        return heightInMeters;
    }

    @Override
    public float getWidthInMeter() {
        return widthInMeters;
    }
}