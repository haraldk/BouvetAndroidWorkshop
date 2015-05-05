package no.bouvet.snaploc;

import android.location.Location;
import android.media.ExifInterface;

/**
 * Created by harald.kuhr on 05/05/15.
 */
public final class ExifUtils {
    private ExifUtils() {}

    // Converts EXIF GPS pos to degree
    private static double convertToDegree(final String degreeMinuteSecond) {
        String[] dms = degreeMinuteSecond.split(",", 3);

        double degree = parseRational(dms[0]);
        double minute = parseRational(dms[1]);
        double second = parseRational(dms[2]);

        return degree + (minute / 60) + (second / 3600);
    }

    private static double parseRational(final String rational) {
        String[] deg = rational.split("/", 2);
        return Double.parseDouble(deg[0]) / Double.parseDouble(deg[1]);
    }

    /**
     * Get GPS location from image.
     *
     * @param exif Exif data from image.
     *
     * @return the GPS location or {@code null} if not found.
     */
    public static Location getLocation(final ExifInterface exif) {
        String latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
        String lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
        String lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
        String lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);

        // TODO: Altitude, timestamp etc...

        if (lat != null && lon != null) {
            double latitude = "N".equals(latRef) ? convertToDegree(lat) : -convertToDegree(lat);
            double longitude = "E".equals(lonRef) ? convertToDegree(lon) : -convertToDegree(lon);

            Location location = new Location("Exif");

            location.setLatitude(latitude);
            location.setLongitude(longitude);

            return location;
        }

        return null;
    }

    /**
     * Geo-tag an image (set/update GPS location).
     *
     * @param exif Exif data from image.
     * @param location the new GPS location.
     */
    public static void setLocation(final ExifInterface exif, final Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        int num1Lat = (int) Math.floor(latitude);
        int num2Lat = (int) Math.floor((latitude - num1Lat) * 60);
        double num3Lat = (latitude - ((double) num1Lat + ((double) num2Lat / 60))) * 3600000;

        int num1Lon = (int) Math.floor(longitude);
        int num2Lon = (int) Math.floor((longitude - num1Lon) * 60);
        double num3Lon = (longitude - ((double) num1Lon + ((double) num2Lon / 60))) * 3600000;

        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, num1Lat + "/1," + num2Lat + "/1," + num3Lat + "/1000");
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, num1Lon + "/1," + num2Lon + "/1," + num3Lon + "/1000");

        if (latitude > 0) {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N");
        } else {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S");
        }

        if (longitude > 0) {
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E");
        } else {
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W");
        }
    }

}
