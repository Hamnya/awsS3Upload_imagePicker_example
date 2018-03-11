package com.example.hamnya.lastawsupload;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Image;
import android.os.Bundle;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferType;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.bumptech.glide.Glide;
import com.esafirm.imagepicker.features.ImagePicker;
import com.esafirm.imagepicker.features.ReturnMode;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * UploadActivity is a ListActivity of uploading, and uploaded records as well
 * as buttons for managing the uploads and creating new ones.
 */
public class UploadActivity extends ListActivity {

    // Indicates that no upload is currently selected
    private static final int INDEX_NOT_CHECKED = -1;

    // TAG for logging;
    private static final String TAG = "UploadActivity";

    // Button for upload operations
    private Button btnUploadImage;
    private Button btnPauseAll;
    private Button btnCancelAll;

    // The TransferUtility is the primary class for managing transfer to S3
    private TransferUtility transferUtility;

    // The SimpleAdapter adapts the data about transfers to rows in the UI
    private SimpleAdapter simpleAdapter;

    // A List of all transfers
    private List<TransferObserver> observers;

    /**
     * This map is used to provide data to the SimpleAdapter above. See the
     * fillMap() function for how it relates observers to rows in the displayed
     * activity.
     */
    private ArrayList<HashMap<String, Object>> transferRecordMaps;

    // Which row in the UI is currently checked (if any)
    private int checkedIndex;

    // Reference to the utility class
    private Util util;

    static final String path = "/upload/user/awstest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        // Initializes TransferUtility, always do this before using it.
        util = new Util();
        transferUtility = util.getTransferUtility(this);
        checkedIndex = INDEX_NOT_CHECKED;
        transferRecordMaps = new ArrayList<HashMap<String, Object>>();
        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Get the data from any transfer's that have already happened,
        initData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clear transfer listeners to prevent memory leak, or
        // else this activity won't be garbage collected.
        if (observers != null && !observers.isEmpty()) {
            for (TransferObserver observer : observers) {
                observer.cleanTransferListener();
            }
        }
    }

    /**
     * Gets all relevant transfers from the Transfer Service for populating the
     * UI
     */
    private void initData() {
        transferRecordMaps.clear();
        // Use TransferUtility to get all upload transfers.
        observers = transferUtility.getTransfersWithType(TransferType.UPLOAD);
        TransferListener listener = new UploadListener();
        for (TransferObserver observer : observers) {

            // For each transfer we will will create an entry in
            // transferRecordMaps which will display
            // as a single row in the UI
            HashMap<String, Object> map = new HashMap<String, Object>();
            util.fillMap(map, observer, false);
            transferRecordMaps.add(map);

            // Sets listeners to in progress transfers
            if (TransferState.WAITING.equals(observer.getState())
                    || TransferState.WAITING_FOR_NETWORK.equals(observer.getState())
                    || TransferState.IN_PROGRESS.equals(observer.getState())) {
                observer.setTransferListener(listener);
            }
        }
        simpleAdapter.notifyDataSetChanged();
    }

    private void initUI() {
        /**
         * This adapter takes the data in transferRecordMaps and displays it,
         * with the keys of the map being related to the columns in the adapter
         */
        simpleAdapter = new SimpleAdapter(this, transferRecordMaps,
                R.layout.record_item, new String[] {
                 "fileName", "progress", "bytes", "state", "percentage", "image"
        },
                new int[] {
                      R.id.textFileName, R.id.progressBar1, R.id.textBytes,
                        R.id.textState, R.id.textPercentage, R.id.pick_img
                });
        simpleAdapter.setViewBinder(new ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data,
                                        String textRepresentation) {

                switch (view.getId()) {

                    case R.id.textFileName:
                        TextView fileName = (TextView) view;
                        fileName.setText((String) data);
                        return true;
                    case R.id.progressBar1:
                        ProgressBar progress = (ProgressBar) view;
                        progress.setProgress((Integer) data);
                        return true;
                    case R.id.textBytes:
                        TextView bytes = (TextView) view;
                        bytes.setText((String) data);
                        return true;
                    case R.id.textState:
                        TextView state = (TextView) view;
                        String stat = ((TransferState) data).toString();
                        if(stat.equalsIgnoreCase("IN_PROGRESS")){
                            stat = "진행중";
                        }else if(stat.equalsIgnoreCase("PAUSED")){
                            stat = "멈춤";
                        }else if(stat.equalsIgnoreCase("COMPLETED")){
                            stat = "완료";
                        }
                        state.setText(stat);
                        return true;
                    case R.id.textPercentage:
                        TextView percentage = (TextView) view;
                        percentage.setText((String) data);
                        return true;
                    case R.id.pick_img:
                        ImageView imageView = (ImageView) view;
                        String filePath = (String) data;
                        imageView.setImageResource(R.drawable.ic_launcher_background);
                        Glide.with(UploadActivity.this)
                                .load(Uri.fromFile(new File(filePath))).into(imageView);
                        return true;
                }
                return false;
            }
        });
        setListAdapter(simpleAdapter);

        // Updates checked index when an item is clicked
        getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {


                    PopupMenu popup = new PopupMenu(UploadActivity.this, view);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.my_popup_menu, popup.getMenu());


                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            int i = item.getItemId();
                            if (i == R.id.pause) {
                                updateProgress("pause");
                                return true;
                            }else if (i == R.id.resume){
                                updateProgress("resume");
                                return true;
                            }else if (i == R.id.cancel){
                                updateProgress("cancel");
                                return true;
                            }else if (i == R.id.delete){
                                updateProgress("delete");
                                return true;
                            }else {
                                return onMenuItemClick(item);
                            }

                        }
                    });
                    popup.setGravity(Gravity.RIGHT);


                    popup.show();





                    checkedIndex = pos;
                    simpleAdapter.notifyDataSetChanged();
                }
        });

        btnUploadImage = (Button) findViewById(R.id.buttonUploadImage);
        btnPauseAll = (Button) findViewById(R.id.buttonPauseAll);
        btnCancelAll = (Button) findViewById(R.id.buttonCancelAll);

        btnUploadImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent();
                if (Build.VERSION.SDK_INT >= 19) {
                    // For Android versions of KitKat or later, we use a
                    // different intent to ensure
                    // we can get the file path from the returned intent URI
         /*           intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);*/


                    openGallery();


                } else {
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    startActivityForResult(intent, 0);
                }

            }
        });

        btnPauseAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                transferUtility.pauseAllWithType(TransferType.UPLOAD);
            }
        });

        btnCancelAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                transferUtility.cancelAllWithType(TransferType.UPLOAD);
            }
        });

    }

    public void updateProgress(String stat){

        // Make sure a transfer is selected
        if (checkedIndex >= 0 && checkedIndex < observers.size()) {

            switch (stat) {
                case "pause":

                    Boolean paused = transferUtility.pause(observers.get(checkedIndex).getId());
                    /**
                     * If paused does not return true, it is likely because the
                     * user is trying to pause an upload that is not in a
                     * pausable state (For instance it is already paused, or
                     * canceled).
                     */
                    if (!paused) {
                        Toast.makeText(
                                UploadActivity.this,
                                "Cannot pause transfer.  You can only pause transfers in a IN_PROGRESS or WAITING state.",
                                Toast.LENGTH_SHORT).show();
                    }

                    break;
                case "resume":
                        TransferObserver resumed = transferUtility.resume(observers.get(checkedIndex)
                                .getId());
                        // Sets a new transfer listener to the original observer.
                        // This will overwrite existing listener.
                        observers.get(checkedIndex).setTransferListener(new UploadListener());
                        /**
                         * If resume returns null, it is likely because the transfer
                         * is not in a resumable state (For instance it is already
                         * running).
                         */
                        if (resumed == null) {
                            Toast.makeText(
                                    UploadActivity.this,
                                    "Cannot resume transfer.  You can only resume transfers in a PAUSED state.",
                                    Toast.LENGTH_SHORT).show();
                        }

                    break;
                case "cancel":
                    // Make sure a transfer is selected
                        Boolean canceled = transferUtility.cancel(observers.get(checkedIndex).getId());
                        /**
                         * If cancel returns false, it is likely because the
                         * transfer is already canceled
                         */
                        if (!canceled) {
                            Toast.makeText(
                                    UploadActivity.this,
                                    "Cannot cancel transfer.  You can only resume transfers in a PAUSED, WAITING, or IN_PROGRESS state.",
                                    Toast.LENGTH_SHORT).show();
                        }

                    break;
                case "delete":
                        transferUtility.deleteTransferRecord(observers.get(checkedIndex).getId());
                        observers.remove(checkedIndex);
                        transferRecordMaps.remove(checkedIndex);
                        checkedIndex = INDEX_NOT_CHECKED;
                        updateList();

                    break;

            }
        }
    }

    public void openGallery() {
        ImagePicker.create(this)

                .folderMode(true) // folder mode (false by default)
                .toolbarFolderTitle("갤러리") // folder selection title
                .toolbarImageTitle("배출 사진을 선택해주세요!") // image selection title
                .toolbarArrowColor(Color.BLACK) // Toolbar 'up' arrow color
                .single() // single mode
                .multi() // multi mode (default mode)
                .limit(10) // max images can be selected (99 by default)
                .showCamera(true) // show camera or not (true by default)
                .imageDirectory("Camera") // directory name for captured image  ("Camera" folder by default)
                .enableLog(false) // disabling log
                .start(); // start image picker activity with request code
    }

    /*
     * Updates the ListView according to the observers.
     */
    private void updateList() {
        TransferObserver observer = null;
        HashMap<String, Object> map = null;
        for (int i = 0; i < observers.size(); i++) {
            observer = observers.get(i);
            map = transferRecordMaps.get(i);
            util.fillMap(map, observer, i == checkedIndex);
        }
        simpleAdapter.notifyDataSetChanged();

    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {


//                Uri uri = data.getData();
        if (ImagePicker.shouldHandle(requestCode, resultCode, data)) {
            // Get a list of picked images
            List<com.esafirm.imagepicker.model.Image> images = ImagePicker.getImages(data);

            for (int i = 0; i < images.size(); i++) {
                beginUpload(images.get(i).getPath());
            }

        }


    }


    /*
     * Begins to upload the file specified by the file path.
     */
    private void beginUpload(String filePath) {
        if (filePath == null) {
            Toast.makeText(this, "Could not find the filepath of the selected file",
                    Toast.LENGTH_LONG).show();
            return;
        }
        File file = new File(filePath);
        TransferObserver observer = transferUtility.upload(Constants.BUCKET_NAME, file.getName(), file, CannedAccessControlList.PublicRead);

        /*
         * Note that usually we set the transfer listener after initializing the
         * transfer. However it isn't required in this sample app. The flow is
         * click upload button -> start an activity for image selection
         * startActivityForResult -> onActivityResult -> beginUpload -> onResume
         * -> set listeners to in progress transfers.
         */
        // observer.setTransferListener(new UploadListener());
    }


    /*
     * A TransferListener class that can listen to a upload task and be notified
     * when the status changes.
     */
    private class UploadListener implements TransferListener {

        // Simply updates the UI list when notified.
        @Override
        public void onError(int id, Exception e) {
            Log.e(TAG, "Error during upload: " + id, e);
            updateList();
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            Log.d(TAG, String.format("onProgressChanged: %d, total: %d, current: %d",
                    id, bytesTotal, bytesCurrent));
            updateList();
        }

        @Override
        public void onStateChanged(int id, TransferState newState) {
            Log.d(TAG, "onStateChanged: " + id + ", " + newState);
            updateList();
        }
    }
}


