package thack.ac.cardsafe;

import android.content.Context;
import android.text.InputFilter;
import android.widget.EditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Class to provide general useful methods
 * Created by paradite on 17/8/14.
 */
public class Helper {

    private static final byte[] HEX_CHAR_TABLE = {(byte) '0', (byte) '1',
            (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6',
            (byte) '7', (byte) '8', (byte) '9', (byte) 'A', (byte) 'B',
            (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F'};

    /**
     * Get Hex string from raw byte array
     * @param raw   Raw byte array
     * @param len   Length of array
     * @return  Hex String
     */
    static String getHexString(byte[] raw, int len) {
                byte[] hex = new byte[2 * len];
                int index = 0;
                int pos = 0;

                for (byte b : raw) {
                    if (pos >= len)
                        break;

                    pos++;
                    int v = b & 0xFF;
                    hex[index++] = HEX_CHAR_TABLE[v >>> 4];
                    hex[index++] = HEX_CHAR_TABLE[v & 0xF];
        //            Log.e("getHexString", "raw byte: " + b + " v: " + v + " converted: " + HEX_CHAR_TABLE[v >>> 4] + " & " + HEX_CHAR_TABLE[v & 0xF]);
                }

                return new String(hex);
            }

    /**
     * Get the displayable date from week and year
     * @param week week of year
     * @param year year
     * @return Date string for displaying
     */
    public static String getDate(int week, int year) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
        Calendar cal = Calendar.getInstance();
//        Log.i(TAG, sdf.format(cal.getTime()));
        cal.set(Calendar.YEAR, year);
//        Log.i(TAG, sdf.format(cal.getTime()));
        cal.set(Calendar.WEEK_OF_YEAR, week);
//        Log.i(TAG, sdf.format(cal.getTime()));
        cal.set(Calendar.DAY_OF_WEEK, 1);
//        Log.i(TAG, sdf.format(cal.getTime()));
        String start = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_WEEK, 7);
//        Log.i(TAG, sdf.format(cal.getTime()));
        String end = sdf.format(cal.getTime());
        return start + " to " + end;
    }

}
