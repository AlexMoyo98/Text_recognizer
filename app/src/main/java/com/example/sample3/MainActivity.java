package com.example.sample3;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

public class MainActivity<request> extends AppCompatActivity implements IMainActivityView {

    private static final int TIME_INTERVAL = 2000;
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int STORAGE_REQUEST_CODE = 101;
    private static final String TAG = "MAIN_TAG";
    private long backPressed;

    private Button button, button2;
    private ImageView imageView;
    private EditText editText;
    private ProgressDialog progressDialog;
    private TextRecognizer textRecognizer;

    private Uri imageUri = null;

    private String[] cameraPermissions;
    private String[] storagePermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intializeViews();
        int i = 0;

        //init arrays of permissions required for camera, gallery
        cameraPermissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        storagePermissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        //init setup the progress dialog, show while text from image is being recognized
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false);

        //init TextRecognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        //handle click, show input image dialog
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputImageDialog();
            }
        });

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               //if (imageUri == null) {
                  //  Toast.makeText(MainActivity.this, "Pick image first...", Toast.LENGTH_LONG).show();
               // } else {
                    recognizeTextFromImage();
                //}



            }
        });
    }

    protected void intializeViews() {
        button = findViewById(R.id.inputImageBtn);
        button2 = findViewById(R.id.recognize);
        imageView = findViewById(R.id.picture);
        editText = findViewById(R.id.recognized);
    }

    private void recognizeTextFromImage() {
        Log.d(TAG, "recognizeTextFromImage");
        //set message and show progress dialog
        progressDialog.setMessage("Preparing image......");

        progressDialog.show();

        try {
            InputImage inputImage = InputImage.fromFilePath(this, imageUri);

            progressDialog.setMessage("Recognizing Text....");

            Task<Text> textTaskResult = textRecognizer.process(inputImage)
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text text) {
                            //process completed, dismiss dialog
                            progressDialog.dismiss();
                            //get the recognized text
                            String recognizedText = text.getText();
                            Log.d(TAG, "onSuccess: recognizedText: " + recognizedText);
                            //set the recognized text to edit text
                            editText.setText(recognizedText);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            //failed recognizing text from image, dismiss dialog, show reason in toast
                            progressDialog.dismiss();
                            Log.e(TAG, "onFailure: ", e);
                            Toast.makeText(MainActivity.this, "Failed recognizing text due to " + e.getMessage(), Toast.LENGTH_LONG).show();

                        }
                    });
        } catch (Exception e) {
            //exception occurred while preparing InputImage, dismiss dialog, show reason in Toast
            progressDialog.dismiss();
            Log.e(TAG, "recognizeTextFromImage: ", e);
            Toast.makeText(MainActivity.this, "Failed recognizing image due to " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showInputImageDialog() {
        PopupMenu popupMenu = new PopupMenu(this, button);

        //Add items Camera, Gallery to PopupMenu, parm 2 is menu id, param 3 is position of this menu item in menu items list, param 4 is title of the menu
        popupMenu.getMenu().add(Menu.NONE, 1, 1, "CAMERA");
        popupMenu.getMenu().add(Menu.NONE, 2, 2, "GALLERY");

        //Show PopupMenu
        popupMenu.show();

        //Handle PopupMenu item clicks
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                //get item id that is clicked from PopupMenu
                int id = menuItem.getItemId();
                if (id == 1) {
                    //Camera is click, check if camera permissions are granted or not
                    Log.d(TAG, "onMenuItemClick: Camera Clicked.... ");

                    if (checkCameraPermission()) {
                        //camera permissions granted, we can launch camera intent
                        pickImageCamera();
                    } else {
                        //camera permissions not granted, request the camera permissions
                        requestCameraPermissions();

                    }

                } else if (id == 2) {
                    //Gallery is clicked, check if storage permission is granted or not
                    Log.d(TAG, "onMenuItemClick: Gallery Clicked");
                    if (checkStoragePermission()) {
                        //storage permission granted, we can launch the gallery intent
                        pickImageGallery();

                    } else {
                        //storage permission not granted, request the storage permission
                        requestStoragePermission();
                    }

                }
                return true;
            }
        });
    }

    private void pickImageGallery() {
        Log.d(TAG, "pickImageGallery: ");
        //intent to pick image from gallery, will show all resources from where we can pick the image
        Intent intent = new Intent(Intent.ACTION_PICK);
        //set type of file we want to pick i.e image
        intent.setType("image/*");
        galleryActivityResultLauncher.launch(intent); //missed code
    }

    private ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    //HERE WE WILL RECEIVE THE IMAGE, IF PICKED
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        //image picked
                        Intent data = result.getData();
                        imageUri = data.getData(); //fixed code from the missed ones
                        Log.d(TAG, "onActivityResults: imageUri " + imageUri);
                        //set to imageView
                        imageView.setImageURI(imageUri);
                    } else {
                        Log.d(TAG, "onActivityResult: cancelled");
                        //cancelled
                        Toast.makeText(MainActivity.this, "Cancelled...", Toast.LENGTH_LONG).show();
                    }

                }

            }
    );

    private void pickImageCamera() {
        Log.d(TAG, "pickImageCamera: ");
        //get ready the image data to store in MediaStore
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Sample Title");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Sample Description");
        //the Image uri
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        //intent to launch camera
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraActivityResultLauncher.launch(intent);

    }

    private ActivityResultLauncher<Intent> cameraActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    //here we will receive the image, if taken from camera
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        //image is taken from camera
                        //we already have the image in imageUri using function pickImageCamera()

                        Log.d(TAG, "onActivityResult: imageUri" + imageUri);
                        imageView.setImageURI(imageUri);
                    } else {
                        //cancelled
                        Log.d(TAG, "onActivityResult: cancelled ");
                        Toast.makeText(MainActivity.this, "Cancelled...", Toast.LENGTH_LONG).show();
                    }


                }
            }
    );

    private boolean checkStoragePermission() {
         /*check if camera & storage permissions are allowed or not
        return tru if allowed, false if not allowed*/
        boolean result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);

        return result;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQUEST_CODE);
    }

    private boolean checkCameraPermission() {
        /*check if camera & storage permissions are allowed or not
        return tru if allowed, false if not allowed*/

        boolean cameraResult = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED);
        boolean storageResult = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);

        return cameraResult && storageResult;
    }

    private void requestCameraPermissions() {
        //request camera permission (for camera intent)
        ActivityCompat.requestPermissions(this, cameraPermissions, CAMERA_REQUEST_CODE);
    }
    //handle permission results


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case CAMERA_REQUEST_CODE: {

                if (grantResults.length > 0) {
                    //check if camera, storage permissions granted, contains boolean results either true or false
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean storageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if (cameraAccepted && storageAccepted) {
                        //both permissions are granted or not
                        pickImageCamera();
                    } else {
                        //one or both permissions are denied, cant launch camera intent
                        Toast.makeText(this, "Camera & Storage permissions are required", Toast.LENGTH_LONG).show();
                    }
                } else {
                    //nether allowed not denied rather cancelled
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
                }

            }
            break;
            case STORAGE_REQUEST_CODE: {
                //check if some action from permission dialog performed or not allow/deny
                if (grantResults.length > 0) {
                    //check if storage permissions granted, contains boolean results either true or false
                    boolean storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    //check if storage permissions is granted or not

                    if (storageAccepted) {
                        //storage permission granted, we can launch gallery intent
                        pickImageGallery();
                    } else {
                        //storage permission denied, can't launch gallery intent
                        Toast.makeText(this, "Storage permission is required", Toast.LENGTH_LONG).show();

                    }
                }

            }
            break;
        }
    }

    @Override
    public void onBackPressed() {
        if (backPressed + TIME_INTERVAL > System.currentTimeMillis()) {
            super.onBackPressed();
            return;
        } else {
            //Toast.makeText(getBaseContext(), "Press Back Again to Exit App", Toast.LENGTH_LONG).show();
            Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "Press back to exit App", Snackbar.LENGTH_LONG);
            snackbar.show();
        }
        backPressed = System.currentTimeMillis();
    }

    @Override
    public void display() {
        Log.d(TAG, "display: ***************");
    }
}