package thack.ac.cardsafe;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by Zhu on 6/12/2014.
 * For creating and managing database
 */
public class DataBaseHelper extends SQLiteOpenHelper {
    static String TAG = "DataBaseHelper";

    static List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();

    /*
    * The Database version number will need to be updated each time its content gets changed!
    * */
    Resources res;
    Context context;

    public DataBaseHelper(Context context) {
        super(context, "Experiments.db", null, 4);
        res = context.getResources();
        this.context = context;
    }

    /**
     * Acutal method to insert into database
     * @param db        SQLDatabase
     * @param cardID    Card ID
     * @param name      Safe name
     * @param content   Safe content
     */
    public static void insertDataIntoDatabase(SQLiteDatabase db, String cardID, String name, String content) {
        //Insert data into database
        ContentValues cv = new ContentValues();
        cv.put("CardID", cardID);
        cv.put("Name", name);
        cv.put("Content", content);
        cv.put("New", "New");
//        Log.d(TAG, "Content:" + content);
        try {
            db.insertWithOnConflict("safe", "CardID", cv, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLiteException exception) {
            Log.e("Database", "Insertion error.");
            exception.printStackTrace();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE safe(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "CardID INTEGER UNIQUE, " +
                "Name TEXT," +
                "Content TEXT, " +
                "created_at DATETIME DEFAULT (datetime('now','localtime')), " +
                "Count INTEGER DEFAULT 0," +
                "New TEXT);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS safe");
        onCreate(db);
    }

    public static int deleteId(SQLiteDatabase db, String id) {
        Log.e(TAG, "ID: " + id + " deleted by deletedId.");
        return db.delete("safe", "_id" + "=" + id, null);
    }
}
