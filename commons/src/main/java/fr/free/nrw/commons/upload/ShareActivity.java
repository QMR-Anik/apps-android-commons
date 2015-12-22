package fr.free.nrw.commons.upload;

import android.content.*;
import android.os.*;
import com.nostra13.universalimageloader.core.ImageLoader;
import android.net.*;
import android.support.v4.app.NavUtils;
import com.actionbarsherlock.view.MenuItem;

import android.util.Log;
import android.widget.*;

import fr.free.nrw.commons.*;
import fr.free.nrw.commons.modifications.CategoryModifier;
import fr.free.nrw.commons.modifications.TemplateRemoveModifier;
import fr.free.nrw.commons.CommonsApplication;
import fr.free.nrw.commons.EventLog;
import fr.free.nrw.commons.category.CategorizationFragment;
import fr.free.nrw.commons.contributions.*;
import fr.free.nrw.commons.auth.*;
import fr.free.nrw.commons.modifications.ModificationsContentProvider;
import fr.free.nrw.commons.modifications.ModifierSequence;

import java.util.ArrayList;


public  class       ShareActivity
        extends     AuthenticatedActivity
        implements  SingleUploadFragment.OnUploadActionInitiated,
        CategorizationFragment.OnCategoriesSaveHandler {

    private SingleUploadFragment shareView;
    private CategorizationFragment categorizationFragment;

    private CommonsApplication app;

    private String source;
    private String mimeType;
    private String mediaUriString;

    private Uri mediaUri;

    private Contribution contribution;

    private ImageView backgroundImageView;

    private UploadController uploadController;

    public ShareActivity() {
        super(WikiAccountAuthenticator.COMMONS_ACCOUNT_TYPE);
    }

    public void uploadActionInitiated(String title, String description) {
        Toast startingToast = Toast.makeText(getApplicationContext(), R.string.uploading_started, Toast.LENGTH_LONG);
        startingToast.show();
        uploadController.startUpload(title, mediaUri, description, mimeType, source, new UploadController.ContributionUploadProgress() {
            public void onUploadStarted(Contribution contribution) {
                ShareActivity.this.contribution = contribution;
                showPostUpload();
            }
        });
    }

    private void showPostUpload() {
        if(categorizationFragment == null) {
            categorizationFragment = new CategorizationFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.single_upload_fragment_container, categorizationFragment, "categorization")
                .commit();
    }

    public void onCategoriesSave(ArrayList<String> categories) {
        if(categories.size() > 0) {
            ModifierSequence categoriesSequence = new ModifierSequence(contribution.getContentUri());

            categoriesSequence.queueModifier(new CategoryModifier(categories.toArray(new String[]{})));
            categoriesSequence.queueModifier(new TemplateRemoveModifier("Uncategorized"));
            categoriesSequence.setContentProviderClient(getContentResolver().acquireContentProviderClient(ModificationsContentProvider.AUTHORITY));
            categoriesSequence.save();
        }

        // FIXME: Make sure that the content provider is up
        // This is the wrong place for it, but bleh - better than not having it turned on by default for people who don't go throughl ogin
        ContentResolver.setSyncAutomatically(app.getCurrentAccount(), ModificationsContentProvider.AUTHORITY, true); // Enable sync by default!

        EventLog.schema(CommonsApplication.EVENT_CATEGORIZATION_ATTEMPT)
                .param("username", app.getCurrentAccount().name)
                .param("categories-count", categories.size())
                .param("files-count", 1)
                .param("source", contribution.getSource())
                .param("result", "queued")
                .log();
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(contribution != null) {
            outState.putParcelable("contribution", contribution);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if(categorizationFragment != null && categorizationFragment.isVisible()) {
            EventLog.schema(CommonsApplication.EVENT_CATEGORIZATION_ATTEMPT)
                    .param("username", app.getCurrentAccount().name)
                    .param("categories-count", categorizationFragment.getCurrentSelectedCount())
                    .param("files-count", 1)
                    .param("source", contribution.getSource())
                    .param("result", "cancelled")
                    .log();
        } else {
            EventLog.schema(CommonsApplication.EVENT_UPLOAD_ATTEMPT)
                    .param("username", app.getCurrentAccount().name)
                    .param("source", getIntent().getStringExtra(UploadService.EXTRA_SOURCE))
                    .param("multiple", true)
                    .param("result", "cancelled")
                    .log();
        }
    }

    @Override
    protected void onAuthCookieAcquired(String authCookie) {
        super.onAuthCookieAcquired(authCookie);
        app.getApi().setAuthCookie(authCookie);


        shareView = (SingleUploadFragment) getSupportFragmentManager().findFragmentByTag("shareView");
        categorizationFragment = (CategorizationFragment) getSupportFragmentManager().findFragmentByTag("categorization");
        if(shareView == null && categorizationFragment == null) {
                shareView = new SingleUploadFragment();
                this.getSupportFragmentManager()
                        .beginTransaction()
                        .add(R.id.single_upload_fragment_container, shareView, "shareView")
                        .commit();
        }

        uploadController.prepareService();
    }

    @Override
    protected void onAuthFailure() {
        super.onAuthFailure();
        Toast failureToast = Toast.makeText(this, R.string.authentication_failed, Toast.LENGTH_LONG);
        failureToast.show();
        finish();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uploadController = new UploadController(this);
        setContentView(R.layout.activity_share);
        
        app = (CommonsApplication)this.getApplicationContext();
        backgroundImageView = (ImageView)findViewById(R.id.backgroundImage);

        Intent intent = getIntent();

        if(intent.getAction().equals(Intent.ACTION_SEND)) {
            mediaUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(intent.hasExtra(UploadService.EXTRA_SOURCE)) {
                source = intent.getStringExtra(UploadService.EXTRA_SOURCE);
            } else {
                source = Contribution.SOURCE_EXTERNAL;
            }

            mimeType = intent.getType();
        }

        mediaUriString = mediaUri.toString();
        Log.d("Image", "Uri: " + mediaUriString);

        //convert image Uri to file path
        FilePathConverter uriObj = new FilePathConverter(this, mediaUri);
        String filePath = uriObj.getFilePath();

        //extract the coordinates of image in decimal degrees
        GPSExtractor imageObj = new GPSExtractor(filePath);
        String coords = imageObj.getCoords();
        Log.d("Image", "Coords of image: " + coords);

        //build URL with image coords for MediaWiki API calls
        String apiUrl = UrlBuilder.buildUrl(coords);
        Log.d("Image", "URL: " + apiUrl);


        //asynchronous calls to MediaWiki Commons API to match image coords with nearby Commons categories
        APICalls apiCall = new APICalls(this);
        apiCall.request(apiUrl);


        ImageLoader.getInstance().displayImage(mediaUriString, backgroundImageView);

        if(savedInstanceState != null)  {
            contribution = savedInstanceState.getParcelable("contribution");
        }

        requestAuthToken();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        uploadController.cleanup();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Builds URL with image coords for MediaWiki API calls
     * Example URL: https://commons.wikimedia.org/w/api.php?action=query&prop=categories|coordinates|pageprops&format=json&clshow=!hidden&coprop=type|name|dim|country|region|globe&codistancefrompoint=38.11386944444445|13.356263888888888&
     * generator=geosearch&redirects=&ggscoord=38.11386944444445|13.356263888888888&ggsradius=100&ggslimit=10&ggsnamespace=6&ggsprop=type|name|dim|country|region|globe&ggsprimary=all&formatversion=2
     */
    public static class UrlBuilder {
        private static String buildUrl (String coords){

            Uri.Builder builder = Uri.parse("https://commons.wikimedia.org/").buildUpon();

            builder.appendPath("w")
                    .appendPath("api.php")
                    .appendQueryParameter("action", "query")
                    .appendQueryParameter("prop", "categories|coordinates|pageprops")
                    .appendQueryParameter("format", "json")
                    .appendQueryParameter("clshow", "!hidden")
                    .appendQueryParameter("coprop", "type|name|dim|country|region|globe")
                    .appendQueryParameter("codistancefrompoint", coords)
                    .appendQueryParameter("generator", "geosearch")
                    .appendQueryParameter("ggscoord", coords)
                    .appendQueryParameter("ggsradius", "100")
                    .appendQueryParameter("ggslimit", "10")
                    .appendQueryParameter("ggsnamespace", "6")
                    .appendQueryParameter("ggsprop", "type|name|dim|country|region|globe")
                    .appendQueryParameter("ggsprimary", "all")
                    .appendQueryParameter("formatversion", "2");

            return builder.build().toString();
        }
    }

}
