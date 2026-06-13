package mgks.os.swv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;

import mgks.os.swv.plugins.QRScannerPlugin;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";
    private boolean isPageLoaded = false;

    static Functions fns = new Functions();
    private FileProcessing fileProcessing;
    private LinearLayout adContainer;
    private PermissionManager permissionManager;
    private ActivityResultLauncher<Intent> fileUploadLauncher;
    private ActivityResultLauncher<ScanOptions> qrScannerLauncher;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        SWVContext.getPluginManager().onActivityResult(requestCode, resultCode, intent);
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongViewCast", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (SWVContext.ASWP_BLOCK_SCREENSHOTS) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (SWVContext.ASWP_EXIT_ON_BACK) {
                    if (SWVContext.ASWP_EXITDIAL) {
                        fns.ask_exit(MainActivity.this);
                    } else {
                        finish();
                    }
                    return;
                }

                if (SWVContext.asw_view.canGoBack()) {
                    SWVContext.asw_view.goBack();
                } else {
                    if (SWVContext.ASWP_EXITDIAL) {
                        fns.ask_exit(MainActivity.this);
                    } else {
                        finish();
                    }
                }
            }
        });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        final SplashScreen splashScreen = androidx.core.splashscreen.SplashScreen.installSplashScreen(this);

        final View content = findViewById(android.R.id.content);
        if (SWVContext.ASWP_EXTEND_SPLASH) {
            content.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (isPageLoaded) {
                            content.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        } else {
                            return false;
                        }
                    }
                }
            );
        }

        permissionManager = new PermissionManager(this);

        fileUploadLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    if (SWVContext.asw_file_path != null) {
                        SWVContext.asw_file_path.onReceiveValue(null);
                        SWVContext.asw_file_path = null; 
                    }
                    return;
                }

                if (result.getResultCode() == Activity.RESULT_OK) {
                    if (null == SWVContext.asw_file_path) {
                        return;
                    }

                    Intent data = result.getData();
                    if (data != null && (data.getDataString() != null || data.getClipData() != null)) {
                        ClipData clipData = data.getClipData();
                        if (clipData != null) {
                            final int numSelectedFiles = clipData.getItemCount();
                            results = new Uri[numSelectedFiles];
                            for (int i = 0; i < numSelectedFiles; i++) {
                                results[i] = clipData.getItemAt(i).getUri();
                            }
                        } else if (data.getDataString() != null) {
                            results = new Uri[]{Uri.parse(data.getDataString())};
                        }
                    }

                    if (results == null) {
                        if (SWVContext.asw_pcam_message != null) {
                            results = new Uri[]{Uri.parse(SWVContext.asw_pcam_message)};
                        } else if (SWVContext.asw_vcam_message != null) {
                            results = new Uri[]{Uri.parse(SWVContext.asw_vcam_message)};
                        }
                    }
                }

                if (SWVContext.asw_file_path != null) {
                    SWVContext.asw_file_path.onReceiveValue(results);
                    SWVContext.asw_file_path = null;
                }

                SWVContext.asw_pcam_message = null;
                SWVContext.asw_vcam_message = null;
            }
        );

        qrScannerLauncher = registerForActivityResult(new ScanContract(),
                result -> {
                    PluginInterface plugin = SWVContext.getPluginManager().getPluginInstance("QRScannerPlugin");
                    if (plugin instanceof QRScannerPlugin) {
                        ((QRScannerPlugin) plugin).handleScanResult(result);
                    }
                }
        );

        SWVContext.setAppContext(getApplicationContext());
        fileProcessing = new FileProcessing(this, fileUploadLauncher);

        String cookie_orientation = !SWVContext.ASWP_OFFLINE ? fns.get_cookies("ORIENT") : "";
        fns.set_orientation((!Objects.equals(cookie_orientation, "") ? Integer.parseInt(cookie_orientation) : SWVContext.ASWV_ORIENTATION), false, this);

        setupLayout();
        initializeWebView();

        SWVContext.loadPlugins(this);
        SWVContext.init(this, SWVContext.asw_view, fns); 

        PluginInterface qrPlugin = SWVContext.getPluginManager().getPluginInstance("QRScannerPlugin");
        if (qrPlugin instanceof QRScannerPlugin) {
            ((QRScannerPlugin) qrPlugin).setLauncher(qrScannerLauncher);
        }

        if (savedInstanceState == null) {
            setupFeatures();
            handleIncomingIntents();
        }

        if(SWVContext.SWV_DEBUGMODE){
            Log.d(TAG, "URL: "+ SWVContext.CURR_URL+"DEVICE INFO: "+ Arrays.toString(fns.get_info(this)));
        }

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
    }

    public void setWindowSecure(boolean secure) {
        runOnUiThread(() -> {
            if (secure) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                if (!SWVContext.ASWP_BLOCK_SCREENSHOTS) {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            }
        });
    }

    private void setupLayout() {
        if (SWVContext.ASWV_LAYOUT == 1) {
            setContentView(R.layout.drawer_main);
            MaterialToolbar toolbar = findViewById(R.id.toolbar); 
            final SwipeRefreshLayout pullRefresh = findViewById(R.id.pullfresh);

            if (SWVContext.ASWP_DRAWER_HEADER) {
                findViewById(R.id.app_bar).setVisibility(View.VISIBLE);
                setSupportActionBar(toolbar);
                Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

                DrawerLayout drawer = findViewById(R.id.drawer_layout);
                ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.open, R.string.close) {
                    @Override
                    public void onDrawerSlide(View drawerView, float slideOffset) {
                        super.onDrawerSlide(drawerView, slideOffset);
                        if (pullRefresh != null && slideOffset > 0 && pullRefresh.isEnabled()) {
                            pullRefresh.setEnabled(false);
                        }
                    }

                    @Override
                    public void onDrawerClosed(View drawerView) {
                        super.onDrawerClosed(drawerView);
                        if (pullRefresh != null && !pullRefresh.isEnabled() && SWVContext.ASWP_PULLFRESH) {
                            pullRefresh.setEnabled(true);
                        }
                    }
                };
                drawer.addDrawerListener(toggle);
                toggle.syncState();
            } else {
                findViewById(R.id.app_bar).setVisibility(View.GONE);
            }

            NavigationView navigationView = findViewById(R.id.nav_view);
            navigationView.setNavigationItemSelectedListener(this);

        } else {
            setContentView(R.layout.activity_main);
        }

        SWVContext.asw_view = findViewById(R.id.msw_view);
        adContainer = findViewById(R.id.msw_ad_container);
        SWVContext.print_view = findViewById(R.id.print_view);
    }

    @SuppressLint("JavascriptInterface")
    private void initializeWebView() {
        SWVContext.init(this, SWVContext.asw_view, fns);

        Playground playground = new Playground(this, SWVContext.asw_view, fns);
        SWVContext.getPluginManager().setPlayground(playground);

        WebSettings webSettings = SWVContext.asw_view.getSettings();

        if (SWVContext.OVERRIDE_USER_AGENT || SWVContext.POSTFIX_USER_AGENT) {
            String userAgent = webSettings.getUserAgentString();
            if (SWVContext.OVERRIDE_USER_AGENT) {
                userAgent = SWVContext.CUSTOM_USER_AGENT;
            }
            if (SWVContext.POSTFIX_USER_AGENT) {
                userAgent = userAgent + " " + SWVContext.USER_AGENT_POSTFIX;
            }
            webSettings.setUserAgentString(userAgent);
        }

        webSettings.setJavaScriptEnabled(true);
        webSettings.setSaveFormData(SWVContext.ASWP_SFORM);
        webSettings.setSupportZoom(SWVContext.ASWP_ZOOM);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        if (SWVContext.ASWP_ACCEPT_THIRD_PARTY_COOKIES) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(SWVContext.asw_view, true);
        }

        if (!SWVContext.ASWP_COPYPASTE) {
            SWVContext.asw_view.setOnLongClickListener(v -> true);
        }

        SWVContext.asw_view.setHapticFeedbackEnabled(false);
        SWVContext.asw_view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        SWVContext.asw_view.setVerticalScrollBarEnabled(false);

        SWVContext.asw_view.setWebViewClient(new WebViewCallback());
        SWVContext.asw_view.setWebChromeClient(createWebChromeClient());
        SWVContext.asw_view.setBackgroundColor(getColor(R.color.colorPrimary));
        
        SWVContext.asw_view.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");
        SWVContext.asw_view.addJavascriptInterface(new AudioToggle(this), "AudioToggle");

        setupDownloadListener();
    }

    private void setupDownloadListener() {
        SWVContext.asw_view.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (!permissionManager.isStoragePermissionGranted()) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PermissionManager.STORAGE_REQUEST_CODE);
                Toast.makeText(this, "Storage permission is required to download files.", Toast.LENGTH_LONG).show();
            } else {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription(getString(R.string.dl_downloading));
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType));

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                assert dm != null;
                dm.enqueue(request);
                Toast.makeText(this, getString(R.string.dl_downloading2), Toast.LENGTH_LONG).show();
            }
        });
    }

    private WebChromeClient createWebChromeClient() {
        return new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if(SWVContext.SWV_DEBUGMODE) {
                    Log.d("SWV_JS", consoleMessage.message() + " -- From line " +
                            consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                }
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                return fileProcessing.onShowFileChooser(webView, filePathCallback, fileChooserParams);
            }

            @Override
            public void onProgressChanged(WebView view, int p) {
                if (SWVContext.ASWP_PBAR) {
                    if (SWVContext.asw_progress == null) SWVContext.asw_progress = findViewById(R.id.msw_progress);
                    SWVContext.asw_progress.setProgress(p);
                    if (p == 100) {
                        SWVContext.asw_progress.setProgress(0);
                    }
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (permissionManager.isLocationPermissionGranted()) {
                    callback.invoke(origin, true, false);
                } else {
                    permissionManager.requestInitialPermissions();
                }
            }

            // 🔥 FIX: WEBVIEW KO CAMERA AUR MIC KI PERMISSION DENE KE LIYE
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    request.grant(request.getResources());
                });
            }
        };
    }

    private void setupFeatures() {
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                return null;
            }
        });

        if (!isTaskRoot()) {
            finish();
            return;
        }

        setupNotificationChannel();
        setupSwipeRefresh();

        if (SWVContext.ASWP_PBAR) {
            SWVContext.asw_progress = findViewById(R.id.msw_progress);
        } else {
            findViewById(R.id.msw_progress).setVisibility(View.GONE);
        }
        SWVContext.asw_loading_text = findViewById(R.id.msw_loading_text);

        fns.get_info(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            permissionManager.requestInitialPermissions();
        }, 1500);

        setupFirebaseMessaging();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        if (searchView != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        }
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_hint));
        }
        assert searchView != null;
        searchView.setIconified(true);
        searchView.setIconifiedByDefault(true);
        searchView.clearFocus();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                fns.aswm_view(SWVContext.ASWV_SEARCH + query, false, SWVContext.asw_error_counter, MainActivity.this);
                searchView.setQuery(query, false);
                return false;
            }

            public boolean onQueryTextChange(String query) {
                return false;
            }
        });
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_exit) {
            fns.exit_app(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        SWVContext.NavItem navItem = SWVContext.ASWV_DRAWER_MENU.get(id);

        if (navItem != null) {
            String action = navItem.action;
            if (action.startsWith("mailto:")) {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(action));
                try {
                    startActivity(Intent.createChooser(intent, "Send Email"));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(this, "No email app found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                fns.aswm_view(action, false, 0, this);
            }
        }

        if (SWVContext.ASWV_LAYOUT == 1) {
            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            if (drawer != null) {
                drawer.closeDrawer(GravityCompat.START);
            }
        }
        return true;
    }

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = new NotificationChannel(
                    SWVContext.asw_fcm_channel,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);

            channel.setDescription(getString(R.string.notification_channel_desc));
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void setupSwipeRefresh() {
        final SwipeRefreshLayout pullRefresh = findViewById(R.id.pullfresh);

        if (SWVContext.ASWP_PULLFRESH) {
            pullRefresh.setOnRefreshListener(() -> {
                fns.pull_fresh(MainActivity.this);
                pullRefresh.setRefreshing(false);
            });

            SWVContext.asw_view.getViewTreeObserver().addOnScrollChangedListener(
                    () -> pullRefresh.setEnabled(SWVContext.asw_view.getScrollY() == 0));
        } else {
            pullRefresh.setRefreshing(false);
            pullRefresh.setEnabled(false);
        }
    }

    private void setAppTheme(boolean isDarkMode) {
        int mode = isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void setupFirebaseMessaging() {
        fns.fcm_token(new Functions.TokenCallback() {
            @Override
            public void onTokenReceived(String token) {
                Log.d(TAG, "FCM Token received: " + token);
            }

            @Override
            public void onTokenFailed(Exception e) {
                Log.e(TAG, "Failed to retrieve FCM token", e);
            }
        });
    }

    private void handleIncomingIntents() {
        Intent intent = getIntent();
        Log.d(TAG, "Intent: " + intent.toUri(0));

        String uri = intent.getStringExtra("uri");
        String share = intent.getStringExtra("s_uri");
        String shareImg = intent.getStringExtra("s_img");

        if (share != null) {
            handleSharedText(share);
        } else if (shareImg != null) {
            Log.d(TAG, "Share image intent: " + shareImg);
            Toast.makeText(this, shareImg, Toast.LENGTH_LONG).show();
            fns.aswm_view(SWVContext.ASWV_URL, false, SWVContext.asw_error_counter, this);
        } else if (uri != null) {
            Log.d(TAG, "Notification intent: " + uri);
            fns.aswm_view(uri, false, SWVContext.asw_error_counter, this);
        } else if (intent.getData() != null) {
            String path = intent.getDataString();
            fns.aswm_view(path, false, SWVContext.asw_error_counter, this);
        } else {
            Log.d(TAG, "Main intent: " + SWVContext.ASWV_URL);
            fns.aswm_view(SWVContext.ASWV_URL, false, SWVContext.asw_error_counter, this);
        }
    }

    private void handleSharedText(String share) {
        Log.d(TAG, "Share text intent: " + share);

        Matcher matcher = Functions.url_pattern().matcher(share);
        String urlStr = "";

        if (matcher.find()) {
            urlStr = matcher.group();
            if (urlStr.startsWith("(") && urlStr.endsWith(")")) {
                urlStr = urlStr.substring(1, urlStr.length() - 1);
            }
        }

        String redirectUrl = SWVContext.ASWV_SHARE_URL +
                "?text=" + share +
                "&link=" + urlStr +
                "&image_url=";

        fns.aswm_view(redirectUrl, false, SWVContext.asw_error_counter, this);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void setNativeTheme(String theme) {
            runOnUiThread(() -> {
                int newMode;
                if ("dark".equals(theme)) {
                    newMode = AppCompatDelegate.MODE_NIGHT_YES;
                } else if ("light".equals(theme)) {
                    newMode = AppCompatDelegate.MODE_NIGHT_NO;
                } else {
                    newMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                }
                if (AppCompatDelegate.getDefaultNightMode() != newMode) {
                    AppCompatDelegate.setDefaultNightMode(newMode);
                }
            });
        }
    }

    public class AudioToggle {
        Context context;
        AudioManager audioManager;

        AudioToggle(Context c) {
            context = c;
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }

        @JavascriptInterface
        public void setAudioMode(int mode) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    if (mode == 1) { 
                        audioManager.setSpeakerphoneOn(true);
                        Toast.makeText(context, "Speaker Mode Active", Toast.LENGTH_SHORT).show();
                    } else if (mode == 0) { 
                        audioManager.setSpeakerphoneOn(false);
                        Toast.makeText(context, "Earpiece Mode Active", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        CookieManager.getInstance().flush(); 
        SWVContext.asw_view.onPause();
        SWVContext.getPluginManager().onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        SWVContext.asw_view.onResume();
        SWVContext.getPluginManager().onResume();

        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(
                getString(R.string.app_name), bm, getColor(R.color.colorPrimary));
        setTaskDescription(taskDesc);
    }

    @Override
    protected void onDestroy() {
        SWVContext.getPluginManager().onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        String theme = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
        String script = "if(typeof setTheme === 'function') { setTheme('" + theme + "', true); }";
        if (SWVContext.asw_view != null) {
            SWVContext.asw_view.evaluateJavascript(script, null);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        SWVContext.asw_view.saveState(outState);
        if (SWVContext.asw_view.getUrl() != null) {
            outState.putString("swv_last_url", SWVContext.asw_view.getUrl());
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        SWVContext.asw_view.restoreState(savedInstanceState);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if (SWVContext.ASWP_EXIT_ON_BACK) {
                if (SWVContext.ASWP_EXITDIAL) {
                    fns.ask_exit(this);
                } else {
                    finish();
                }
                return true;
            }

            if (SWVContext.asw_view.canGoBack()) {
                SWVContext.asw_view.goBack();
            } else {
                if (SWVContext.ASWP_EXITDIAL) {
                    fns.ask_exit(this);
                } else {
                    finish();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        SWVContext.getPluginManager().onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.INITIAL_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Location permission granted.");

                    } else {
                        Log.w(TAG, "Location permission denied.");
                    }
                } else if (permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Notification permission granted.");
                    } else {
                        Log.w(TAG, "Notification permission denied.");
                    }
                }
            }
        }
    }

    private class WebViewCallback extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            SWVContext.getPluginManager().onPageStarted(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            SWVContext.getPluginManager().onPageFinished(url);

            findViewById(R.id.msw_welcome).setVisibility(View.GONE);
            findViewById(R.id.msw_view).setVisibility(View.VISIBLE);
            isPageLoaded = true;
            
            view.evaluateJavascript("if (typeof window.AudioToggle !== 'undefined') { window.AudioToggle.SPEAKER = 1; window.AudioToggle.EARPIECE = 0; }", null);

            if (!url.startsWith("file://") && SWVContext.ASWV_GTAG != null && !SWVContext.ASWV_GTAG.isEmpty()) {
                fns.inject_gtag(view, SWVContext.ASWV_GTAG);
            }

            String theme = SWVContext.ASWP_DARK_MODE ? "dark" : "light";
            String script = "if(typeof applyInitialTheme === 'function') { applyInitialTheme('" + theme + "'); }";
            view.evaluateJavascript(script, null);

            if (SWVContext.ASWP_CUSTOM_CSS) {
                try {
                    InputStream inputStream = getAssets().open("web/custom.css");
                    byte[] buffer = new byte[inputStream.available()];
                    inputStream.read(buffer);
                    inputStream.close();
                    String encoded = Base64.encodeToString(buffer, Base64.NO_WRAP);
                    String js = "javascript:(function() {" +
                            "var parent = document.getElementsByTagName('head').item(0);" +
                            "var style = document.createElement('style');" +
                            "style.type = 'text/css';" +
                            "style.innerHTML = window.atob('" + encoded + "');" +
                            "parent.appendChild(style)" +
                            "})()";
                    view.loadUrl(js);
                    Log.d(TAG, "Custom CSS injected.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to inject custom CSS.", e);
                }
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            if (SWVContext.getPluginManager().shouldOverrideUrlLoading(view, url)) {
                return true;
            }

            if (url.matches("^(https?|file)://.*$")) {
                SWVContext.CURR_URL = url;
            }
            return fns.url_actions(view, url, MainActivity.this);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                int errorCode = error.getErrorCode();
                if (errorCode == ERROR_HOST_LOOKUP ||
                        errorCode == ERROR_TIMEOUT ||
                        errorCode == ERROR_CONNECT ||
                        errorCode == ERROR_UNKNOWN ||
                        errorCode == ERROR_IO) {

                    Log.e(TAG, "Network Error Occurred: " + error.getDescription());

                    view.post(() -> {
                        if (SWVContext.ASWV_OFFLINE_URL != null && !SWVContext.ASWV_OFFLINE_URL.isEmpty()) {
                            view.loadUrl(SWVContext.ASWV_OFFLINE_URL);
                        } else {
                            view.loadUrl("file:///android_asset/error.html");
                        }
                    });
                }
            }
            super.onReceivedError(view, request, error);
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (SWVContext.ASWP_CERT_VERI) {
                super.onReceivedSslError(view, handler, error);
            } else {
                handler.proceed();
                if (SWVContext.SWV_DEBUGMODE) {
                    Toast.makeText(MainActivity.this, "SSL Error: " + error.getPrimaryError(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (SWVContext.SWV_DEBUGMODE) {
                Log.e(TAG, "HTTP Error loading " + request.getUrl().toString() +
                        ": " + errorResponse.getStatusCode());
            }
        }
    }
}
