package thack.ac.cardsafe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;


public class MainActivity extends BaseActivity {

    final MainActivity self = this;
    private BroadcastReceiver receiver;
    private IntentFilter filter;

    //Boolean to track if a delete has been perform
    boolean deleted = false;

    // Limit on length of user inputs
    int maxLengthTitle = 20;
    int maxLengthContent = 30;

    //Text for dialogs
    private String TITLE_HINT;
    private String CONTENT_HINT;

    //Texts used for status
    private final String textSuccess = "Success!";
    private final String textSuccessUIDWarning =
            "Success! However... \n" +
            "Due to hardware limitations,\n" +
            "Only partial ID(MAY NOT be unique) can be read.";
    private final String textID = "Card UID: ";
    private final String textDate = "\nDate created: ";
    private final String textRange = "\n" +
            "Range of possible manufactured dates:\n";
    private final String textAdditional = "Additional NfcA data:\n";
    public final String textIncomplete = "Reading was incomplete. Please do not remove the card.";
    private final String textReading = "Reading data from the card...";
    private final String textAuth = "Trying to authenticate...";
    private final String textInvalid = "Invalid card.";
    private final String textMatric = "Matric card detected.";
    private final String textBankCard = "Please take care of your Bank card.";
    public final String textErrorReading = "Sorry, this card is not supported.";
    private final String textHardwareFailed = "Sorry, your device does not support the NFC technology used in matric card.";
    private final String textNamePre = "";

    private final String textKOPI = "Please take care of your Kopitiam card.";

    //Identifiers for card types in async task
    private final String TYPE_KOPI = "KOPITIAM";

    private static final String MIME_TEXT_PLAIN = "text/plain";

    private String CardID;
    private String name;
    private String content;
    private String date;


    // Views
    private TextView mInfoView;
    private TextView mContentView;
    private TextView mExtraView;
    public TextView mStatusView;
    private Button mButton;
    private Button mLeave;
//    private EditText mEditText;
    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        //Get resources
        TITLE_HINT = getResources().getString(R.string.safe_title);
        CONTENT_HINT= getResources().getString(R.string.content_title);

        mInfoView = (TextView) findViewById(R.id.info);
        mContentView = (TextView) findViewById(R.id.safe_content);
        mExtraView = (TextView) findViewById(R.id.safe_content_extra);
        mStatusView = (TextView) findViewById(R.id.status);
        mButton = (Button) findViewById(R.id.button);
        mLeave = (Button) findViewById(R.id.leave);

        //Set the default texts
        if (!mNfcAdapter.isEnabled()) {
            mStatusView.setText("NFC is disabled. Please enable it in the settings.");
        }
        mContentView.setText(R.string.status);

        // Listeners
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Connect with the database and read data
                db = (new DataBaseHelper(getApplicationContext())).getWritableDatabase();
                AlertDialog.Builder alert = new AlertDialog.Builder(self);
                alert.setTitle(getResources().getString(R.string.modify_safe_title));

                // Set an EditText view to get user input
                final EditText mEditTextTitle = AlertDialogHelper.getEditTextWithText(self, name, maxLengthTitle);
                final EditText mEditTextContent = AlertDialogHelper.getEditTextWithText(self, content, maxLengthContent);

                AlertDialogHelper.setDialogView(self, alert, mEditTextTitle, mEditTextContent);

                alert.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String name = mEditTextTitle.getText().toString();
                        String content = mEditTextContent.getText().toString();
                        DataBaseHelper.insertDataIntoDatabase(db, CardID, name, content);
                        db.close();
                        QueryDataBase(CardID);
                        mContentView.invalidate();
                        mExtraView.invalidate();
                        // Or: String value: input.getText().toString();
                        // Do something with value!
                    }
                });

                alert.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        //Confirmation Required
                        DialogInterface.OnClickListener dialogClickListenerRealDelete = new OnClickListenerRealDelete();

                        AlertDialog.Builder builder = new AlertDialog.Builder(self);
                        builder.setMessage("Are you sure about deleting?").setPositiveButton("Yes", dialogClickListenerRealDelete)
                                .setNegativeButton("No", dialogClickListenerRealDelete).show();
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        db.close();
                        // Canceled.
                    }
                });
                alert.show();
            }
        });

        mLeave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Button Clicked");
//                onPause();
//                onResume();
                self.recreate();
            }
        });


        // Handling of intent
        filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                            NfcAdapter.STATE_OFF);
                    switch (state) {
                        case NfcAdapter.STATE_OFF:
                            Toast.makeText(MainActivity.this, "NFC disabled.", Toast.LENGTH_SHORT).show();
                            mStatusView.setText("NFC is disabled. Please enable it in the settings.");
                            break;
                        case NfcAdapter.STATE_TURNING_OFF:
//                            Toast.makeText(MainActivity.this, "NFC enabled.", Toast.LENGTH_SHORT).show();
                            break;
                        case NfcAdapter.STATE_ON:
                            Toast.makeText(MainActivity.this, "NFC enabled.", Toast.LENGTH_SHORT).show();
                            mStatusView.setText(R.string.status);
                            break;
                        case NfcAdapter.STATE_TURNING_ON:
//                            Toast.makeText(MainActivity.this, "NFC enabled.", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }

            }
        };
        registerReceiver(receiver, filter);

        Log.d(TAG,"onCreate Card ID: " + CardID);

        //Do not handle intent if a delete was just performed
        if(deleted){
            deleted = false;
        }else{
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        registerReceiver(receiver, filter);
        setupForegroundDispatch(this, mNfcAdapter);
    }

    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, mNfcAdapter);
        unregisterReceiver(receiver);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {

        String action = intent.getAction();
//        Log.e(TAG, "in handleIntent, action: " + action);
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);
            } else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String tech_mfc = MifareClassic.class.getName();
            String tech_NfcA = NfcA.class.getName();
            String tech_IsoDep = IsoDep.class.getName();
            String tech_NfcB = NfcB.class.getName();
            String tech_Ndef = Ndef.class.getName();
            Log.d(TAG, "tags: " + tag);
            // Get the ID directly
            String serialId = null;
            try {
                byte[] tagId = tag.getId();
                serialId = Helper.getHexString(tagId, tagId.length);
                Log.d(TAG, "Direct Read - Serial Number: " + serialId);
            } catch (NullPointerException ex) {
                ex.printStackTrace();
                serialId = "ERROR";
            }

            // Check if it is a matric card by looking at techlist
            if (Arrays.asList(techList).containsAll(Arrays.asList(tech_mfc, tech_NfcA))) {
                // Matric card tags
                new MifareClassicReaderTask().execute(tag);
//                new NfcAReaderTask().execute(tag);
            } else if (Arrays.asList(techList).containsAll(Arrays.asList(tech_IsoDep, tech_NfcB))) {
                // Bank Card tags
                new IsoDepReaderTask().execute(tag);
            } else if (Arrays.asList(techList).contains(tech_NfcA)) {
                // Matric Card for S4, etc which do not support Mifare Classic
                Log.d(TAG, "NfcA detected");
                new NfcAReaderTask().execute(tag);
            } else if (Arrays.asList(techList).contains(tech_Ndef)) {
                // Ndef tags
                String type = intent.getType();
                if (MIME_TEXT_PLAIN.equals(type)) {
                    new NdefReaderTask().execute(tag);
                } else {
                    Log.d(TAG, "Wrong mime type: " + type);
                }
            } else {
                mStatusView.setText(textInvalid);
            }
        } else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            mStatusView.setText(textInvalid);
        }
    }

    /**
     * Method to format the datetime from SQLDatabase to readable string
     * @return  String of date and time for displaying
     * @param mainActivity
     */
    public String formatDateTimeFromSQL(MainActivity mainActivity) {
        DateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date dateTime = new Date();
        try {
            dateTime = iso8601Format.parse(date);
        } catch (ParseException e) {
            Log.e(TAG, "Parsing ISO8601 datetime failed", e);
        }

        long when = dateTime.getTime();
        int flags = 0;
        flags |= android.text.format.DateUtils.FORMAT_SHOW_TIME;
        flags |= android.text.format.DateUtils.FORMAT_SHOW_DATE;
        flags |= android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
        flags |= android.text.format.DateUtils.FORMAT_SHOW_YEAR;

        return android.text.format.DateUtils.formatDateTime(mainActivity,
                when + TimeZone.getDefault().getOffset(when), flags);
    }

    /**
     * Class to read bank cards
     */
    private class IsoDepReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            Log.d(TAG, "IsoDepReader");

            byte[] SELECT = {
                    (byte) 0x00, // CLA Class
                    (byte) 0xA4, // INS Instruction
                    (byte) 0x04, // P1  Parameter 1
                    (byte) 0x00, // P2  Parameter 2
                    (byte) 0x0A, // Length
//                    0x63,0x64,0x63,0x00,0x00,0x00,0x00,0x32,0x32,0x31 // AID
                    (byte) 0xA0, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00 //AID
            };

            IsoDep mIsoDep = IsoDep.get(tag);
            if (mIsoDep == null) {
                // IsoDep is not supported by this Tag.
                return null;
            }
            String cardData = null;

/*            try {
                mIsoDep.connect();
                Log.d(TAG, "Connected to Bank Card");
                byte[] result = mIsoDep.transceive(SELECT);
                Log.d(TAG, "SELECT Result: " + Arrays.toString(result));
                if (!(result[0] == (byte) 0x90 && result[1] == (byte) 0x00)){
                    Log.e(TAG, "could not select applet");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mStatusView.setText("could not select applet");
                        }
                    });
                    throw new IOException("could not select applet");
                }


                byte[] GET_STRING = {
                        (byte) 0x80, // CLA Class
                        0x04, // INS Instruction
                        0x00, // P1  Parameter 1
                        0x00, // P2  Parameter 2
                        0x10  // LE  maximal number of bytes expected in result
                };

                result = mIsoDep.transceive(GET_STRING);
                Log.d(TAG, "Result: " + result);
                int len = result.length;
                if (!(result[len-2]==(byte)0x90&&result[len-1]==(byte) 0x00)){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mStatusView.setText("could not retrieve msisdn");
                        }
                    });
                    throw new RuntimeException("could not retrieve msisdn");
                }


                byte[] data = new byte[len-2];
                System.arraycopy(result, 0, data, 0, len-2);
                String str = new String(data).trim();
//                cardData = "Bank Card data:\n" + str;
                mIsoDep.close();
            } catch (IOException e) {
                Log.e(TAG, "Card Error.");
                return "Error";
            }*/

            if (cardData == null || cardData.equals("")){
                // Authorization failed
                byte[] idbtye = tag.getId();
                String UID = Helper.getHexString(idbtye, idbtye.length);
                Log.d(TAG, "isoDep UID: " + UID);
                if(UID.length()>=8){
                    // Reasonable length for UID
                    cardData = UID;
                    Log.d(TAG, "isoDep UID: " + UID);
                }

            }
            return cardData;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                if(result.equals("Error")){
                    mStatusView.setText(textErrorReading);
                }else{
                    QueryDataBase(result);
                }
            } else {
                mStatusView.setText(textIncomplete);
            }
        }
    }

    /**
     * Class to read Nfc cards
     * This includes matric card for devices that do not support MifareClassic
     */
    private class NfcAReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            Log.d(TAG, "NfcAReader");
//            NfcA mfcA = NfcA.get(tag);
//            if (mfcA == null) {
//                NfcA is not supported by this Tag.
//                return null;
//            }


            String cardData = "";

//            try {
//                mfcA.connect();
//                Short s = mfcA.getSak();
//                byte[] a = mfcA.getAtqa();
////                Log.e(TAG, "a: " + Arrays.toString(a));
////                String atqa = new String(a, Charset.forName("US-ASCII"));
//                String atqa = Arrays.toString(a);
//                cardData = "SAK = " + s + "\nATQA = " + atqa;
//                mfcA.close();
//            } catch (IOException e) {
//                Log.e(TAG, "Card Removed.");
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        mStatusView.setText(textIncomplete);
//                    }
//                });
//                return null;
//            }
            byte[] idbtye = tag.getId();
            String UID = Helper.getHexString(idbtye, idbtye.length);
            if(UID.length()>=8){
                // Reasonable length for UID
                cardData = UID;
                Log.d(TAG, "NfcA UID: " + UID);
            }
            return cardData;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                if(result.length() == 0 || result.equals("")){
                    mStatusView.setText(textHardwareFailed);
                } else{
                    QueryDataBase(result);
                }
            } else {
                mStatusView.setText(textIncomplete);
            }
        }
    }

    /**
     * Class to read MifareClassic Cards
     * This includes matric card
     */
    private class MifareClassicReaderTask extends AsyncTask<Tag, Void, Pair> {
        @Override
        protected Pair doInBackground(Tag... params) {
            Tag tag = params[0];
            Log.d(TAG, "MifareClassicReader");
            MifareClassic mfc = MifareClassic.get(tag);
            byte[] data;
            if (mfc == null) {
                // MifareClassic is not supported by this Tag.
                return null;
            }

            String cardData = "";
            String date;

            try {       //  5.1) Connect to card
                mfc.connect();
                boolean auth;

                // 5.2) and get the number of sectors this card has..and loop thru these sectors
//                int secCount = mfc.getSectorCount();
//                Log.d(TAG, "No of sectors: " + secCount);
                int bCount;
                int bIndex;
                int secValid = 1;
                int week = 0;
                int year = 0;
                String newString;
                String oldString = "";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusView.setText(textAuth);
                    }
                });
                for (int j = 0; j < secValid; j++) {
//                    Log.d(TAG, "In sector " + j + ": ");
                    // 6.1) authenticate the sector
                    Log.d(TAG, "Trying to authenticate with KEY_NFC_FORUM");

                    auth = mfc.authenticateSectorWithKeyA(j, MifareClassic.KEY_NFC_FORUM);
                    if (!auth) {
                        Log.d(TAG, "Trying to authenticate with KEY_DEFAULT");
//                        mStatusView.setText("Trying to authenticate with KEY_DEFAULT");
                        auth = mfc.authenticateSectorWithKeyA(j, MifareClassic.KEY_DEFAULT);
                    }
                    if (!auth) {
                        Log.d(TAG, "Trying to authenticate with KEY_MIFARE_APPLICATION_DIRECTORY");
//                        mStatusView.setText("Trying to authenticate with KEY_MIFARE_APPLICATION_DIRECTORY");
                        auth = mfc.authenticateSectorWithKeyA(j, MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY);
                    }

                    if (auth) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mStatusView.setText(textReading);
                            }
                        });
                        // 6.2) In each sector - get the block count
                        bCount = mfc.getBlockCountInSector(j);
                        Log.d(TAG, "No of blocks: " + bCount);
//                        bIndex = 0;
                        bIndex = mfc.sectorToBlock(j);
                        // Only read block 0
                        int bID = 1;
                        for (int i = 0; i < bID; i++) {
                            Log.d(TAG, "In block " + bIndex + ": ");

                            // 6.3) Read the block
                            data = mfc.readBlock(bIndex);
                            // 7) Convert the data into a string from Hex format.
                            newString = Helper.getHexString(data, data.length);
                            int len = newString.length();
                            // Concatenate only if new string is different
                            if (!newString.equals(oldString)) {
                                if (cardData.equals("")) {
                                    cardData = cardData.concat(newString);
                                } else {
                                    cardData = cardData.concat("\n").concat(newString);
                                }
                                oldString = newString;
                            }
                            // Get the manufactured date
                            if (i == 0 && j == 0) {
                                Log.d(TAG, "Getting date");
                                week = Integer.parseInt(newString.substring(len - 4, len - 2));
                                year = 2000 + Integer.parseInt(newString.substring(len - 2));
                            }
//                            Log.d(TAG, "Hex Table: " + Arrays.toString(HEX_CHAR_TABLE));
//                            Log.d(TAG, "Data Byte: " + Arrays.toString(data));
//                            Log.d(TAG, "Data String: " + getHexString(data, data.length));
                            bIndex++;
                        }
                    } else { // Authentication failed - Handle it
                        Log.e(TAG, "Authentication failed in sector " + j);
//                        return "Authentication failed";
                    }
                }
                date = Helper.getDate(week, year);
                mfc.close();
            } catch (IOException e) {
                Log.e(TAG, "Card Removed.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusView.setText(textIncomplete);
                    }
                });
                return null;
            }
            byte[] idbtye = tag.getId();
            String UID = Helper.getHexString(idbtye, idbtye.length);
            Log.d(TAG, "cardData: " + cardData);
            Log.d(TAG, "UID     : " + UID);
            return new Pair(UID, date);
        }

        @Override
        protected void onPostExecute(Pair result) {
//            Log.e(TAG, result.id);
            if (result != null) {
                QueryDataBase(result.id);
            } else {
                mStatusView.setText(textIncomplete);
            }
        }
    }

    /**
     * Method to get data from database as well as wrapper for insertion if not present
     * @param id    ID of card
     * @return      null
     */
    private String QueryDataBase(String id){

        //Connect with the database and read data
        db = (new DataBaseHelper(getApplicationContext())).getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT _id,CardID,Name,Content,created_at,Count,New FROM safe WHERE CardID='" + id + "' ORDER BY created_at DESC", null);
        CardID = id;
        if(cursor!=null && cursor.getCount()>0){
            // Found data stored

            //Set the Status and ID textview
            mStatusView.setText(textSuccess);
            mInfoView.setText(textID + id);

            // looping through all items and add info to the views
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                CardID = cursor.getString(cursor.getColumnIndex("CardID"));
                name = cursor.getString(cursor.getColumnIndex("Name"));
                content = cursor.getString(cursor.getColumnIndex("Content"));
                date = cursor.getString(cursor.getColumnIndex("created_at"));
                //Format the date to local timezone
                String finalDateTime = formatDateTimeFromSQL(self);

                int count = cursor.getInt(cursor.getColumnIndex("Count"));
                String new_string = cursor.getString(cursor.getColumnIndex("New"));
                if (new_string == null) {
                    new_string = "Old";
                }
                mContentView.setText(textNamePre + name + "\n" + content);
                mExtraView.setText(textDate + finalDateTime);
                mButton.setVisibility(View.VISIBLE);
                mLeave.setVisibility(View.VISIBLE);
                cursor.moveToNext();
            }
            db.close();
        }else{
            //Database empty
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(getResources().getString(R.string.new_safe_title));

            // Set an EditText view to get user input
            final EditText mEditTextTitle = AlertDialogHelper.getEditTextWithHint(this, TITLE_HINT, maxLengthTitle);
            final EditText mEditTextContent = AlertDialogHelper.getEditTextWithHint(this, CONTENT_HINT, maxLengthContent);
            AlertDialogHelper.setDialogView(this, alert, mEditTextTitle, mEditTextContent);

            DialogInterface.OnClickListener onClickListenerSave = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String name = mEditTextTitle.getText().toString();
                    String content = mEditTextContent.getText().toString();
                    DataBaseHelper.insertDataIntoDatabase(db, CardID, name, content);
                    QueryDataBase(CardID);
                    db.close();
                    Toast.makeText(self, "New Card Safe set up successfully!", Toast.LENGTH_LONG).show();
                }
            };
            alert.setPositiveButton("Save", onClickListenerSave);

            DialogInterface.OnClickListener onClickListenerCancel = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    db.close();
                    // Canceled.
                }
            };
            alert.setNegativeButton("Cancel", onClickListenerCancel);
            alert.show();

        }
        return null;
    }

    /**
     * Class for reading Ndef cards
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     * @author Ralf Wondratschek
     */
    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            Log.d(TAG, "In NdefReaderTask");
            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Unsupported Encoding", e);
                    }
                }
            }

            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                mStatusView.setText(textSuccess);
//                mExtraView.setText("Ndef content:\n" + result);
            } else {
                mStatusView.setText(textIncomplete);
            }
        }
    }


    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    private static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{
                new String[]{Ndef.class.getName()},
                new String[]{MifareClassic.class.getName(), NfcA.class.getName(), NdefFormatable.class.getName()},
                new String[]{NfcA.class.getName()},
                new String[]{IsoDep.class.getName()}
        };

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
//        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
//        try {
//            filters[0].addDataType(MIME_TEXT_PLAIN);
//        } catch (IntentFilter.MalformedMimeTypeException e) {
//            throw new RuntimeException("Check your mime type.");
//        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    /**
     * @param activity The corresponding {@link MainActivity} requesting to stop the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    private static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    private class OnClickListenerRealDelete implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    //Yes button clicked
                    db.delete(DATABASE_TABLE, KEY_ID + "=?", new String[] { CardID });
                    db.close();
                    //Notify user about deletion
                    Toast.makeText(self, "Card Safe deleted!", Toast.LENGTH_LONG).show();

                    // Set the deleted boolean to true so that intent is not handled again
                    deleted = true;
                    self.recreate();

                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    //No button clicked
                    db.close();
                    break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_about) {
            AlertDialog.Builder alert = new AlertDialog.Builder(self);
            alert.setTitle(getResources().getString(R.string.action_about));
            String credit = getResources().getString(R.string.credit);
            String disclaimer = getResources().getString(R.string.disclaimer);
            AlertDialogHelper.setDialogViewMessage(self, alert, credit, disclaimer);
            alert.setNegativeButton("Okay", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });
            alert.setCancelable(true);
            alert.show();
            return true;
        }else if(id == R.id.action_settings){
            AlertDialog.Builder alert = new AlertDialog.Builder(self);
            alert.setTitle(getResources().getString(R.string.action_settings));
            String settings = getResources().getString(R.string.settings);
            AlertDialogHelper.setDialogViewMessage(self, alert, settings);
            alert.setNegativeButton("Okay", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });
            alert.setCancelable(true);
            alert.show();
            return true;
        }else if(id == R.id.action_howto){
            AlertDialog.Builder alert = new AlertDialog.Builder(self);
            alert.setTitle(getResources().getString(R.string.action_howto));
            String how_to = getResources().getString(R.string.howto);
            AlertDialogHelper.setDialogViewMessage(self, alert, how_to);
            alert.setNegativeButton("Okay", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });
            alert.setCancelable(true);
//            alert.show();
            Dialog d = alert.create();
            d.show();
            TextView msgTxt = (TextView) d.findViewById(android.R.id.message);
            msgTxt.setTextSize(14);
        }
        return super.onOptionsItemSelected(item);
    }

}
