package suzaku.szk.ektp_reader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.nfc.NfcAdapter.getDefaultAdapter;

/** EktpReaderPlugin */
public class EktpReaderPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  private Activity activity;
  private NfcAdapter nfc;
  private BinaryMessenger messenger;
  private ThreadPoolExecutor mDecodeThreadPool;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    BlockingQueue<Runnable> mDecodeWorkQueue = new LinkedBlockingQueue<Runnable>();
    // Sets the amount of time an idle thread waits before terminating
    int KEEP_ALIVE_TIME = 1;
    // Sets the Time Unit to seconds
    TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

    //create thread to running not in main thread
    mDecodeThreadPool = new ThreadPoolExecutor(
            1,       // Initial pool size
            1,       // Max pool size
            KEEP_ALIVE_TIME,
            KEEP_ALIVE_TIME_UNIT,
            mDecodeWorkQueue);

    messenger = flutterPluginBinding.getBinaryMessenger();

    channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "ektp_reader");
    channel.setMethodCallHandler(this);
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "ektp_reader");
    channel.setMethodCallHandler(new EktpReaderPlugin());
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    nfc = getDefaultAdapter(activity);

    //check if device has nfc or not
    if (!nfc.isEnabled()) {
      result.error("404", "NFC not available", null);
      return;
    }

    if (call.method.equals("getEKTPPhoto")) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        nfc.enableReaderMode(activity, new NfcAdapter.ReaderCallback() {
          @Override
          public void onTagDiscovered(Tag tag) {
            GetEKTPPhoto task = new GetEKTPPhoto(messenger, tag);
            task.executeOnExecutor(mDecodeThreadPool);
          }
        }, 1, null);
      }
      //send true to flutter, but standby a callback if user tap a ektp to nfc
      result.success(true);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {

  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

  }

  @Override
  public void onDetachedFromActivity() {

  }

  public static byte[] hexStringToByteArray(String str) {
    //convert hexString to ByteArray
    int length = str.length();
    byte[] bArr = new byte[(length / 2)];
    for (int i = 0; i < length; i += 2) {
      bArr[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
    }
    return bArr;
  }

  public static String byte2Hex(byte[] bArr, String str) {
    if (bArr == null || bArr.length < 1) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (byte valueOf : bArr) {
      sb.append(String.format("%02X" + str, new Object[]{Byte.valueOf(valueOf)}));
    }
    return sb.toString().trim();
  }

  public static String int2Hex(int i, int i2, String str) {
    ByteBuffer wrap = ByteBuffer.wrap(new byte[4]);
    wrap.putInt(i);
    return byte2Hex(Arrays.copyOfRange(wrap.array(), 4 - (i2 / 2), 4), str);
  }

  private class GetEKTPPhoto extends AsyncTask<String, Void, Void> {
    BinaryMessenger messenger;
    Tag tag;
    ByteBuffer buffer;

    GetEKTPPhoto(BinaryMessenger messenger, Tag tag) {
      super();
      this.messenger = messenger;
      this.tag = tag;
    }

    @Override
    protected Void doInBackground(String... strings) {
      try {
        String res = "";
        byte[] bytesArray = null;
        byte[] hexStringToByteArray;
        IsoDep isoDep = IsoDep.get(tag);
        isoDep.connect();
        //transcieve to get Photo in EKTP
        isoDep.transceive(hexStringToByteArray("00A40000027F0A00"));
        isoDep.transceive(hexStringToByteArray("00A40000026FF2"));
        byte[] transceive = isoDep.transceive(hexStringToByteArray("00B0000008"));

        int parseInt = Integer.parseInt(byte2Hex(Arrays.copyOfRange(transceive, 0, 2), ""), 16) + 2;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(transceive, 2, transceive.length - 2);
        int i = 8;
        while (i < parseInt) {
          int i2 = i + 112;
          if (i2 > parseInt) {
            hexStringToByteArray = hexStringToByteArray("00B0" + int2Hex(i, 4, "") + int2Hex(parseInt - i, 2, ""));
          } else {
            hexStringToByteArray = hexStringToByteArray("00B0" + int2Hex(i, 4, "") + int2Hex(112, 2, ""));
          }
          byte[] transceive2 = isoDep.transceive(hexStringToByteArray);
          byteArrayOutputStream.write(transceive2, 0, transceive2.length - 2);
          i = i2;
        }

        // create base64 string of ektp photo
        res = Base64.encodeToString(byteArrayOutputStream.toByteArray(), 0).replace("\n", "").replace("\r", "");

        //decode from base64 ,save it to bitmap and get bytesArray
        byte[] decodedString = Base64.decode(res, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        ByteArrayOutputStream bitmapStream = new ByteArrayOutputStream();
        decodedByte.compress(Bitmap.CompressFormat.JPEG, 100, bitmapStream);
        bytesArray = bitmapStream.toByteArray();

        buffer = ByteBuffer.allocateDirect(bytesArray.length);
        buffer.put(bytesArray);
        isoDep.close();

      } catch (Exception ex) {
        Log.d("error", ex.getCause().toString());
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      try {
        //send buffer of image to flutter
        this.messenger.send("getEKTPPhoto", buffer);
      } catch (Exception ex) {
        Log.d("error", ex.getCause().toString());
      }
      super.onPostExecute(aVoid);
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }
}
