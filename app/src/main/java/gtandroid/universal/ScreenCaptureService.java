package gtandroid.universal;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;

/** Opt-in screen OCR. Android shows its own screen-capture consent dialog before this service starts. */
public class ScreenCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "capture_result_code";
    public static final String EXTRA_RESULT_DATA = "capture_result_data";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader imageReader;
    private HandlerThread workerThread;
    private Handler worker;
    private TextRecognizer recognizer;
    private long lastProcessedAt;
    private String lastText = "";

    @Override public void onCreate() {
        super.onCreate();
        workerThread = new HandlerThread("RToolsScreenOCR"); workerThread.start();
        worker = new Handler(workerThread.getLooper());
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        startForeground(8801, buildNotification());
    }

    private Notification buildNotification() {
        String channel = "rtools_screen_ocr";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(channel, "RTools захват экрана", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("RTools[+]")
                .setContentText("Распознавание репортов включено").setOngoing(true).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) { stopSelf(); return START_NOT_STICKY; }
        try {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, data);
            if (projection == null) { stopSelf(); return START_NOT_STICKY; }
            DisplayMetrics metrics = new DisplayMetrics();
            ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels, height = metrics.heightPixels, density = metrics.densityDpi;
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(reader -> processLatestImage(reader), worker);
            display = projection.createVirtualDisplay("RToolsOCR", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, worker);
            projection.registerCallback(new MediaProjection.Callback() { @Override public void onStop() { stopSelf(); } }, worker);
        } catch (Exception e) { stopSelf(); }
        return START_STICKY;
    }

    private void processLatestImage(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || System.currentTimeMillis() - lastProcessedAt < 1100) return;
            lastProcessedAt = System.currentTimeMillis();
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride(), rowStride = plane.getRowStride();
            int width = image.getWidth(), height = image.getHeight();
            Bitmap padded = Bitmap.createBitmap(rowStride / pixelStride, height, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            Bitmap bitmap = Bitmap.createBitmap(padded, 0, 0, width, height); padded.recycle();
            InputImage input = InputImage.fromBitmap(bitmap, 0);
            recognizer.process(input).addOnSuccessListener(text -> {
                String detected = text.getText();
                if (detected != null && !detected.equals(lastText)) { lastText = detected; handleDetectedText(detected); }
                bitmap.recycle();
            }).addOnFailureListener(e -> bitmap.recycle());
        } catch (Exception ignored) { }
        finally { if (image != null) image.close(); }
    }

    private void handleDetectedText(String text) {
        // ADM lines use a deterministic format and are passed through the existing form parser.
        for (AdminFormManager.Form form : AdminFormManager.parseForms(text.replace("< ADM>", "<ADM>"))) {
            if (AdminFormManager.addForm(this, form)) {
                Intent update = new Intent("ADMIN_FORM_RECEIVED"); update.setPackage(getPackageName());
                update.putExtra("nickname", form.nickname); update.putExtra("id", form.id); update.putExtra("command", form.command); sendBroadcast(update);
            }
        }
        // Catch explicit report lines only; do not treat arbitrary mentions of the word report as a new ticket.
        String[] lines = text.split("\\r?\\n");
        long now = System.currentTimeMillis();
        android.content.SharedPreferences prefs = getSharedPreferences("GTAndroidReports", MODE_PRIVATE);
        JSONArray existing;
        try { existing = new JSONArray(prefs.getString("recent", "[]")); } catch (Exception e) { existing = new JSONArray(); }
        JSONArray fresh = new JSONArray();
        for (int i=0;i<existing.length();i++) { JSONObject o=existing.optJSONObject(i); if(o!=null && now-o.optLong("time",0)<=60000) fresh.put(o); }
        for (int i=0;i<lines.length;i++) {
            String line=lines[i].trim();
            String lowerLine = line.toLowerCase(java.util.Locale.ROOT);
            if (!lowerLine.contains("к-во репорта") && !lowerLine.contains("репорт от") && !lowerLine.contains("report from")) continue;
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(?:репорт\\s+от\\s+)?([A-Za-zА-Яа-я0-9_]{3,})\\[(\\d{1,6})\\]\\s*:?\\s*(.*)").matcher(line);
            if (!m.find()) continue;
            String nick=m.group(1), id=m.group(2), body=m.group(3).trim();
            if (line.toLowerCase(java.util.Locale.ROOT).contains("<adm>")) continue;
            if (body.isEmpty() && i+1<lines.length) body=lines[i+1].trim();
            if (body.isEmpty()) continue;
            String key=nick+"["+id+"]:"+body;
            boolean duplicate=false;
            for(int j=0;j<fresh.length();j++){JSONObject o=fresh.optJSONObject(j);if(o!=null&&key.equals(o.optString("key")))duplicate=true;}
            if(!duplicate) try { JSONObject o=new JSONObject();o.put("key",key);o.put("nickname",nick);o.put("id",id);o.put("text",body);o.put("time",now);fresh.put(o); } catch(Exception ignored){}
        }
        prefs.edit().putString("recent", fresh.toString()).apply();
        sendBroadcast(new Intent("REPORTS_UPDATED").setPackage(getPackageName()));
    }

    @Override public void onDestroy() {
        if (display != null) display.release(); if (imageReader != null) imageReader.close();
        if (projection != null) projection.stop(); if (recognizer != null) recognizer.close();
        if (workerThread != null) workerThread.quitSafely(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
