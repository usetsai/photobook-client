package com.freecoders.photobook;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.etsy.android.grid.StaggeredGridView;
import com.freecoders.photobook.classes.BookmarkAdapter;
import com.freecoders.photobook.classes.GestureListener;
import com.freecoders.photobook.common.Constants;
import com.freecoders.photobook.common.Photobook;
import com.freecoders.photobook.db.FriendEntry;
import com.freecoders.photobook.db.ImageEntry;
import com.freecoders.photobook.db.ImagesDataSource;
import com.freecoders.photobook.gson.ImageJson;
import com.freecoders.photobook.network.ImageUploader;
import com.freecoders.photobook.network.ServerInterface;
import com.freecoders.photobook.utils.FileUtils;
import com.freecoders.photobook.utils.ImageUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;

@SuppressLint("NewApi") 
public class GalleryFragmentTab extends Fragment {
    private static String LOG_TAG = "GalleryFragmentTab";

    private ArrayList<ImageEntry> mImageList;
    private ImageUploader mImageLoader;
    private GalleryAdapter mAdapter;
    private StaggeredGridView mGridView;
    private GestureListener gestureListener;
    private HorizontalScrollView horizontalScrollView;
    private LinearLayout linearLayout;
    private BookmarkAdapter bookmarkAdapter;
    private Boolean boolSyncGallery = true;

    public GalleryFragmentTab(){
        mImageLoader = new ImageUploader();
        mImageList = new ArrayList<ImageEntry>();
        new GalleryLoaderClass(null, null).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_gallery, container, false);
        mGridView = (StaggeredGridView) rootView.findViewById(R.id.gridView);
        horizontalScrollView = (HorizontalScrollView)
                rootView.findViewById(R.id.bookmarkScrollView);
        linearLayout = (LinearLayout) rootView.findViewById(R.id.bookmarkLinearLayout);
        mAdapter = new GalleryAdapter(getActivity(), R.layout.item_gallery,
                mImageList);
        mGridView.setAdapter(mAdapter);
        mGridView.setOnItemClickListener(OnItemClickListener);
        mGridView.setOnItemLongClickListener(new ImageLongClickListener());
        gestureListener = new GestureListener(getActivity(), horizontalScrollView, mGridView);
        mGridView.setOnTouchListener(gestureListener);

        bookmarkAdapter = new BookmarkAdapter(getActivity(), linearLayout,
                getResources().getStringArray(R.array.gallery_bookmark_items));
        bookmarkAdapter.setOnItemSelectedListener(
            new BookmarkAdapter.onItemSelectedListener() {
                @Override
                public void onItemSelected(int position) {
                    if (position == 0) {
                        mAdapter.clear();
                        mAdapter.notifyDataSetChanged();
                        new GalleryLoaderClass(null, null).
                                executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        mGridView.setOnItemClickListener(OnItemClickListener);
                        mGridView.setOnItemLongClickListener(new ImageLongClickListener());
                    } else if (position == 1) {
                        mAdapter.clear();
                        mAdapter.notifyDataSetChanged();
                        showBuckets();
                    } else if (position == 2) {
                        mAdapter.clear();
                        mAdapter.notifyDataSetChanged();
                        new GalleryLoaderClass(null, ImageEntry.INT_STATUS_SHARED).
                                executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        mGridView.setOnItemClickListener(OnItemClickListener);
                        mGridView.setOnItemLongClickListener(new ImageLongClickListener());
                    }
                }
            });

        setRetainInstance(true);

        Photobook.setGalleryFragmentTab(this);

        if (boolSyncGallery && !Photobook.getPreferences().strUserID.isEmpty()) {
            //syncGallery();
            syncComments();
            boolSyncGallery = false;
        }
        return rootView;
    }

    public class GalleryLoaderClass extends AsyncTask<String, Void, Boolean> {
        private String strBucketId = null;
        private Integer intImageStatus = null;

        public GalleryLoaderClass(String strBucketId, Integer intImageStatus) {
            this.strBucketId = strBucketId;
            this.intImageStatus = intImageStatus;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            final ArrayList<ImageEntry> imgList = Photobook.
                    getImagesDataSource().getImageList(strBucketId, intImageStatus);
            Photobook.getMainActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mImageList.clear();
                    mImageList.addAll(imgList);
                    if (mAdapter != null) mAdapter.notifyDataSetChanged();
                }
            });
            if (!Photobook.getPreferences().strUserID.isEmpty()) {
                syncGallery();
            }
            return true;
        }
    }

    private final class ImageLongClickListener implements AdapterView.OnItemLongClickListener {

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            final ImageEntry image = mImageList.get(position);
            if (image.getStatus() == ImageEntry.INT_STATUS_SHARED) {
                ImageDialogFragment imageDialogFragment = new ImageDialogFragment();
                imageDialogFragment.setImageMenuHandler(new ImageDialogFragment.ImageMenuHandler() {
                    @Override
                    public void onUnShareImage() {
                        ServerInterface.unShareImageRequest(getActivity(), image.getServerId(),
                                new UnShareImageResponse(image), new DefaultErrorListener());
                    }
                });
                FragmentManager fm = getActivity().getFragmentManager();
                imageDialogFragment.show(fm, "image_menu");
                return true;
            }
            return false;
        }
    }

    private static class DefaultErrorListener implements Response.ErrorListener {
        @Override
        public void onErrorResponse(VolleyError volleyError) {

        }
    }

    private class UnShareImageResponse implements Response.Listener<String> {
        private final ImageEntry image;

        private UnShareImageResponse(ImageEntry image) {
            this.image = image;
        }

        @Override
        public void onResponse(String s) {
            image.setStatus(ImageEntry.INT_STATUS_DEFAULT);
            Photobook.getImagesDataSource().updateImage(image);
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
        }
    }

    AdapterView.OnItemClickListener OnItemClickListener
            = new AdapterView.OnItemClickListener(){

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
            ImageEntry image = mImageList.get(position);
            final int pos = position;
            if (image.getStatus() == ImageEntry.INT_STATUS_DEFAULT) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                LayoutInflater inflater = (LayoutInflater) getActivity().
                        getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View layout = inflater.inflate(R.layout.dialog_upload, parent, false);
                ImageView imgView = (ImageView) layout.findViewById(R.id.fragmentImgView);
                final EditText editText = (EditText) layout.findViewById(R.id.fragmentEditText);
                Button button = (Button) layout.findViewById(R.id.fragmentButton);

                Bitmap b = ImageUtils.decodeSampledBitmap(image.getOrigUri());
                int orientation = ImageUtils.getExifOrientation(image.getOrigUri());
                Log.d(LOG_TAG, "Orientation1 = " + orientation + " for image " + image.getOrigUri()
                        +" "+image.getThumbUri());
                if ((orientation == 90) || (orientation == 270)) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(orientation);
                    Bitmap rotatedBitmap = Bitmap.createBitmap(b, 0, 0,
                            b.getWidth(), b.getHeight(), matrix, true);
                    imgView.setImageBitmap(rotatedBitmap);
                } else
                    imgView.setImageBitmap(b);
                //imgView.setImageURI(Uri.parse(image.getOrigUri()));
                dialog.setView(layout);
                final AlertDialog alertDialog = dialog.create();
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mImageList.get(pos).setTitle(editText.getText().toString());
                        String strOrigUri = mImageList.get(pos).getOrigUri();
                        String strThumbUri = mImageList.get(pos).getThumbUri();
                        String strDestOrigName = mImageList.get(pos).getMediaStoreID();
                        String strDestThumbName = mImageList.get(pos).getMediaStoreID() +
                                "_thumb";
                        if (strOrigUri.contains(".")) {
                            String filenameArray[] = strOrigUri.split("\\.");
                            String extension = filenameArray[filenameArray.length - 1];
                            strDestOrigName = strDestOrigName + "." + extension;
                            strDestThumbName = strDestThumbName + "." + extension;
                        }
                        File destOrigFile = new File(Photobook.getMainActivity().getFilesDir(),
                                strDestOrigName);
                        File destThumbFile = new File(Photobook.getMainActivity().getFilesDir(),
                                strDestThumbName);
                        destOrigFile.getParentFile().mkdirs();
                        destThumbFile.getParentFile().mkdirs();
                        if (FileUtils.copyFileFromUri(new File(strOrigUri), destOrigFile)) {
                            mImageList.get(pos).setOrigUri(destOrigFile.toString());
                            Log.d(LOG_TAG, "Saved local image to " +
                                    destOrigFile.toString());
                        }
                        if (FileUtils.copyFileFromUri(new File(strThumbUri), destThumbFile)) {
                            mImageList.get(pos).setThumbUri(destThumbFile.toString());
                            Log.d(LOG_TAG, "Saved local thumbnail to " +
                                    destThumbFile.toString());
                        }
                        Photobook.getImagesDataSource().saveImage(mImageList.get(pos));
                        //mImageLoader.uploadImage(mImageList, pos, mAdapter);
                        mImageLoader.uploadImageS3(mImageList, pos, strOrigUri.toLowerCase() ,
                                mAdapter);
                        alertDialog.dismiss();
                    }
                });
                alertDialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                alertDialog.show();
            } else if (image.getStatus() == ImageEntry.INT_STATUS_SHARED) {
                Intent mIntent = new Intent(Photobook.getMainActivity(), ImageDetailsActivity.class);
                Bundle b = new Bundle();
                b.putBoolean(Photobook.intentExtraImageDetailsSource, true);
                mIntent.putExtras(b);
                Photobook.setGalleryImageDetails(image);
                startActivity(mIntent);
            }
        }
    };

    public void showBuckets () {
        ArrayList<ImagesDataSource.BucketEntry> buckets =
                Photobook.getImagesDataSource().getBuckets();
        ArrayList<ImageEntry> bucketThumbs = new ArrayList<ImageEntry>();
        for (int i = 0; i < buckets.size(); i++) {
            ImageEntry bucketThumb = new ImageEntry();
            bucketThumb.setTitle(buckets.get(i).strBucketName);
            bucketThumb.setStatus(ImageEntry.INT_STATUS_SHARED);
            bucketThumb.setRatio(1);
            bucketThumb.setThumbUri(buckets.get(i).strTitleImageUrl);
            bucketThumb.setOrigUri(buckets.get(i).strTitleImageUrl);
            bucketThumb.setBucketId(buckets.get(i).strBucketId);
            bucketThumbs.add(bucketThumb);
        }
        mAdapter.clear();
        mAdapter.addAll(bucketThumbs);
        mAdapter.notifyDataSetChanged();
        mGridView.setOnItemClickListener(OnBucketClickListener);
    }

    AdapterView.OnItemClickListener OnBucketClickListener
            = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
            ImageEntry bucket = mAdapter.getItem(position);
            mAdapter.clear();
            mAdapter.notifyDataSetChanged();
            new GalleryLoaderClass(bucket.getBucketId(), null).
                    executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            mGridView.setOnItemClickListener(OnItemClickListener);
            mGridView.setOnItemLongClickListener(new ImageLongClickListener());
        }
    };

    public void syncGallery(){
        ServerInterface.getImageDetailsRequestJson(
            Photobook.getMainActivity(),
            null,
            new Response.Listener<HashMap<String, ImageJson>>() {
                @Override
                public void onResponse(HashMap<String, ImageJson> response) {
                    HashMap<String, ImageJson> uriMap =
                            new HashMap<String, ImageJson>();
                    for (ImageJson image : response.values())
                        if ((image.local_uri != null) &&
                                !image.local_uri.isEmpty())
                            uriMap.put(image.local_uri.toLowerCase(), image);
                    for (int i = 0; i < mImageList.size(); i++)
                        if (uriMap.containsKey(mImageList.get(i).
                                getOrigUri().toLowerCase()) &&
                                (mImageList.get(i).getStatus() ==
                                        ImageEntry.INT_STATUS_DEFAULT) &&
                                (uriMap.get(mImageList.get(i).
                                        getOrigUri().toLowerCase()).status ==
                                        1)) {
                            ImageJson remoteImage = uriMap.get(mImageList.get(i).
                                    getOrigUri().toLowerCase());
                            mImageList.get(i).setStatus(ImageEntry.
                                    INT_STATUS_SHARED);
                            mImageList.get(i).setServerId(remoteImage.image_id);
                            mImageList.get(i).setTitle(remoteImage.title);
                            Photobook.getImagesDataSource().saveImage(mImageList.
                                    get(i));
                        }
                    if (mAdapter != null) mAdapter.notifyDataSetChanged();
                }
            }, null);
    }

    public void syncComments(){
        ServerInterface.getComments(
                null,
                true,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (mAdapter != null) mAdapter.notifyDataSetChanged();
                    }
                }, null);
    }

    public void refreshAdapter() {
        mAdapter.notifyDataSetChanged();
    }
}
