package com.geeklabs.imtranslator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.annotation.NonNull
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.View
import android.widget.AdapterView
import com.bumptech.glide.Glide
import com.crashlytics.android.Crashlytics
import com.desmond.squarecamera.CameraActivity
import com.geeklabs.imtranslator.util.PrefUtil
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.vision.v1.Vision
import com.google.api.services.vision.v1.VisionRequestInitializer
import com.google.api.services.vision.v1.model.*
import com.google.cloud.translate.Language
import com.google.cloud.translate.Translate
import com.google.cloud.translate.TranslateOptions
import com.google.gson.Gson
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.lang.System.out
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val TAKE_PHOTO_REQUEST: Int = 560
    private lateinit var feature: Feature
    private val visionAPI = arrayOf("LANDMARK_DETECTION", "LOGO_DETECTION", "SAFE_SEARCH_DETECTION", "IMAGE_PROPERTIES", "LABEL_DETECTION")

    val apiKey: String = "AIzaSyCH0OkZs2XeMt3TPaHp-BgIqiUBzWe1w8w"

    private val api = visionAPI[4]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Fabric.with(this, Crashlytics())
        progress.visibility = View.VISIBLE

        feature = Feature()
//        feature.type = "LANDMARK_DETECTION"
        feature.type = "LABEL_DETECTION"
        feature.maxResults = 10

        iv_photo.setOnClickListener {
            openCameraIntent()
        }


        // Instantiates a client
        val prefUtil = PrefUtil(this)
        var languages = prefUtil.lanagues
        val nullOrEmpty = languages.isNullOrEmpty()
        if (nullOrEmpty) {


            doAsync {
                val translate = TranslateOptions.newBuilder().setApiKey(apiKey).build().getService();
                var target = Translate.LanguageListOption.targetLanguage("en");
                var languages = translate.listSupportedLanguages(target);

                for (language in languages) {
                    out.printf("Name: %s, Code: %s\n", language.getName(), language.getCode());
                }
                val gson = Gson()
                val fromJson = gson.toJson(languages)
                prefUtil.lanagues = fromJson
                addLanguagesToSpinner(languages)
            }
        } else {
            val gson = Gson()
            val languageList = gson.fromJson(languages, Array<com.google.cloud.translate.Language>::class.java).asList()
            addLanguagesToSpinner(languageList)
        }
    }

    private fun addLanguagesToSpinner(languageList: List<com.google.cloud.translate.Language>) {
        val customAdapter = CustomAdapter(this, languageList)
        sp_language.adapter = customAdapter
        progress.visibility = View.GONE
    }

    private fun openCameraIntent() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val checkSelfPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            if (checkSelfPermission != PackageManager.PERMISSION_GRANTED) {
                val PERMISSION_CAMERA = 0
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_CAMERA)
            } else {
                // Start CameraActivity
                val startCustomCameraIntent = Intent(this, CameraActivity::class.java)
                startActivityForResult(startCustomCameraIntent, TAKE_PHOTO_REQUEST)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && TAKE_PHOTO_REQUEST == requestCode) {
            val photoUri = data!!.getData()
            Log.i("URL", photoUri.path)
            Glide.with(this).load(photoUri).into(iv_photo)

            //getDataFromImage(photoUri.path)
            val bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.fromFile(File(photoUri.path)));
            processImage(bitmap, feature)
        }
    }


    private fun processImage(bitmap: Bitmap, feature: Feature) {

        progress.visibility = View.VISIBLE
        var featureList = ArrayList<Feature>();
        featureList.add(feature);

        val annotateImageRequests = ArrayList<AnnotateImageRequest>();
        val annotateImageReq = AnnotateImageRequest();
        annotateImageReq.setFeatures(featureList);
        annotateImageReq.setImage(getImageEncodeImage(bitmap));
        annotateImageRequests.add(annotateImageReq);

        doAsync {
            try {

                val httpTransport = AndroidHttp.newCompatibleTransport();
                val jsonFactory = GsonFactory.getDefaultInstance();

                val requestInitializer = VisionRequestInitializer(apiKey);

                val builder = Vision.Builder(httpTransport, jsonFactory, null);
                builder.setVisionRequestInitializer(requestInitializer);

                val vision = builder.build();

                val batchAnnotateImagesRequest = BatchAnnotateImagesRequest();
                batchAnnotateImagesRequest.setRequests(annotateImageRequests);

                val annotateRequest = vision.images().annotate(batchAnnotateImagesRequest);
                annotateRequest.setDisableGZipContent(true);
                val response = annotateRequest.execute();

                convertResponseToString(response)
            } catch (e: GoogleJsonResponseException) {
                progress.visibility = View.GONE
                Log.d("MainActivity", "failed to make API request because " + e.getContent());
            } catch (e: IOException) {
                progress.visibility = View.GONE
                Log.d("MainActivity", "failed to make API request because of other IOException " + e.message);
            }
//               return "Cloud Vision API request failed. Check logs for details.";
        }
    }

    @NonNull
    fun getImageEncodeImage(bitmap: Bitmap): Image {
        val base64EncodedImage = Image();
        // Convert the bitmap to a JPEG
        // Just in case it's a format that Android understands but Cloud Vision
        val byteArrayOutputStream = ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
        val imageBytes = byteArrayOutputStream.toByteArray();

        // Base64 encode the JPEG
        base64EncodedImage.encodeContent(imageBytes);
        return base64EncodedImage;
    }


    fun convertResponseToString(response: BatchAnnotateImagesResponse): String {

        val imageResponses = response.getResponses().get(0);

        var entityAnnotations = null;

        var message = "";

        if (api.equals("LABEL_DETECTION")) {
            message = formatAnnotation(imageResponses.getLabelAnnotations());
            return message;
        }
        /*switch (api) {
            case "LANDMARK_DETECTION":
                entityAnnotations = imageResponses.getLandmarkAnnotations();
                message = formatAnnotation(entityAnnotations);
                break;
            case "LOGO_DETECTION":
                entityAnnotations = imageResponses.getLogoAnnotations();
                message = formatAnnotation(entityAnnotations);
                break;
            case "SAFE_SEARCH_DETECTION":
                SafeSearchAnnotation annotation = imageResponses.getSafeSearchAnnotation();
                message = getImageAnnotation(annotation);
                break;
            case "IMAGE_PROPERTIES":
                ImageProperties imageProperties = imageResponses.getImagePropertiesAnnotation();
                message = getImageProperty(imageProperties);
                break;
            case "LABEL_DETECTION":
                entityAnnotations = imageResponses.getLabelAnnotations();
                message = formatAnnotation(entityAnnotations);
                break;
        }*/
        return message;
    }

    fun formatAnnotation(entityAnnotation: List<EntityAnnotation>): String {
        var message = "";
        var translatedText = ""
        val resultList: MutableList<ImageResult> = mutableListOf()

        if (entityAnnotation != null) {

            for (entity: EntityAnnotation in entityAnnotation) {
                val imageResult = ImageResult()
                message = message + "    " + entity.getDescription() + " " + entity.getScore();
                message += "@";
                imageResult.resultText = "" + entity.description
                imageResult.resultValue = "" + entity.score
                resultList.add(imageResult)
            }

            sp_language.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {

                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    progress.visibility = View.VISIBLE
                    processTranslate(message);
                }

            }

            // Translates some text into Russian
            processTranslate(message);


        } else {
            message = "Nothing Found";
            progress.visibility = View.GONE
        }
//        message = message.replace("@", "\n")
        runOnUiThread(Runnable {
            // Creates a vertical Layout Manager
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.isNestedScrollingEnabled = false;

            // You can use GridLayoutManager if you want multiple columns. Enter the number of columns as a parameter.
//        recyclerView.layoutManager = GridLayoutManager(this, 2)

            // Access the RecyclerView Adapter and load the data into it
            recyclerView.adapter = ResultAdapter(resultList, this)
            recyclerView.adapter.notifyDataSetChanged()

            progress.visibility = View.GONE
            resultLL.visibility = View.VISIBLE
        })

        return message;
    }

    private fun processTranslate(message: String) {

        doAsync {
            var selectedItem = sp_language.selectedItem
            var language = selectedItem as Language
            val translate = TranslateOptions.newBuilder().setApiKey(apiKey).build().getService();
            var translation =
                    translate.translate(
                            message,
                            Translate.TranslateOption.sourceLanguage("en"),
                            Translate.TranslateOption.targetLanguage(language.code));

            var translatedText = translation.getTranslatedText();
            translatedText = translatedText.replace("@", "\n")
            runOnUiThread(Runnable {
                tv_translated_text.text = translatedText
                progress.visibility = View.GONE
            })
        }
    }
}
