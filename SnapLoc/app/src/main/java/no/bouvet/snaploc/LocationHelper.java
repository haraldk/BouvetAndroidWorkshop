/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.bouvet.snaploc;

import android.annotation.TargetApi;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Optimized implementation of Last Location Finder for devices running Gingerbread  
 * and above.
 *
 * This class let's you find the "best" (most accurate and timely) previously 
 * detected location using whatever providers are available. 
 *
 * Where a timely / accurate previous location is not detected it will
 * return the newest location (where one exists) and setup a oneshot 
 * location update to find the current location.
 */
public class LocationHelper {
    // Source: https://code.google.com/p/android-protips-location/source/browse/trunk/src/com/radioactiveyak/location_best_practices/utils/GingerbreadLastLocationFinder.java

    protected LocationListener locationListener;
    protected LocationManager locationManager;
    protected Criteria criteria;

    /**
     * Construct a new LocationHelper.
     * @param context Context
     */
    public LocationHelper(Context context) {
        locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);

        // Coarse accuracy is specified here to get the fastest possible result.
        // The calling Activity will likely (or have already) request ongoing
        // updates using the Fine location provider.
        criteria = new Criteria();

        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setSpeedAccuracy(Criteria.NO_REQUIREMENT);
    }

    /**
     * Returns the most accurate and timely previously detected location.
     * Where the last result is beyond the specified maximum distance or
     * latency a one-off location update is returned.
     * @param minDistance Minimum distance before we require a location update.
     * @param minTime Minimum time required between location updates.
     * @return The most accurate and / or timely previously detected location.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public Future<Location> getLastBestLocation(int minDistance, long minTime) {
        Location bestResult = null;
        float bestAccuracy = Float.MAX_VALUE;
        long bestTime = Long.MIN_VALUE;

        // Iterate through all the providers on the system, keeping
        // note of the most accurate result within the acceptable time limit.
        // If no result is found within minTime, return the newest Location.
        List<String> matchingProviders = locationManager.getAllProviders();
        for (String provider: matchingProviders) {
            Location location = locationManager.getLastKnownLocation(provider);

            if (location != null) {
                float accuracy = location.getAccuracy();
                long time = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        ? System.currentTimeMillis() - location.getTime()
                        : TimeUnit.NANOSECONDS.toMillis(location.getElapsedRealtimeNanos());

                if ((time < minTime && accuracy < bestAccuracy)) {
                    bestResult = location;
                    bestAccuracy = accuracy;
                    bestTime = time;
                }
                else if (time > minTime && bestAccuracy == Float.MAX_VALUE && time < bestTime) {
                    bestResult = location;
                    bestTime = time;
                }
            }
        }

        // If the best result is beyond the allowed time limit, or the accuracy of the
        // best result is wider than the acceptable maximum distance, request a single update.
        // This check simply implements the same conditions we set when requesting regular
        // location updates every [minTime] and [minDistance].
        if (locationListener != null && (bestTime > minTime || bestAccuracy > minDistance)) {
            FutureLocationListener futureLocation = new FutureLocationListener();
            locationManager.requestSingleUpdate(criteria, futureLocation, null);
            return futureLocation;
        }

        return new StaticFuture<>(bestResult);
    }

    private static class FutureLocationListener implements LocationListener, Future<Location> {
        private volatile boolean isCancelled;
        private volatile boolean isDone;

        private Location location;

        // Future
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            isCancelled = true;
            return isCancelled;
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Location get() throws InterruptedException, ExecutionException {
            while (!isDone && !isCancelled) {
                Thread.sleep(100);
            }

            return location;
        }

        @Override
        public Location get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            long ns = unit.toNanos(timeout);
            long deadline = System.nanoTime() + ns;

            while (!isDone && !isCancelled) {
                if (System.nanoTime() > deadline) {
                    throw new TimeoutException();
                }

                Thread.sleep(100);
            }

            return location;
        }

        // LocationListener
        @Override
        public void onLocationChanged(final Location location) {
            if (!isCancelled) {
                this.location = location;
                isDone = true;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    private static class StaticFuture<T> implements Future<T> {
        private final T value;

        public StaticFuture(T value) {
            this.value = value;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return value;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return value;
        }
    }
}
