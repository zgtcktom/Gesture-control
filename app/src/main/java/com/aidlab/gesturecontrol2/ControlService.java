package com.aidlab.gesturecontrol2;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.gesture.Gesture;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.widget.CameraViewInterface;

public class ControlService extends AccessibilityService {

    private static final String TAG = "ControlService";
    private CameraHandler cameraHandler;
    private WindowManager windowManager;
    private View previewView;
    private View topView;
    private TextureView textureView;
    private HandTracking handTracking;
    private GestureDetector gestureDetector;
    private TextView result;
    private UVCCameraHelper mCameraHelper;

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String s1 = intent.getStringExtra("result");
            result.setText(s1);
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("InflateParams")
    @Override
    public void onCreate() {
        super.onCreate();

        WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        window.getDefaultDisplay().getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        String CHANNEL_ID = "my_channel_01";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT);

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("")
                .setContentText("").build();
        startForeground(1, notification);
        // Add permission for camera and let user grant the permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LayoutInflater layoutInflater = LayoutInflater.from(this);
        previewView = layoutInflater.inflate(R.layout.preview_top, null);
        WindowManager.LayoutParams topParams = new WindowManager.LayoutParams(
//                360,
//                640,
                640,
                480,
                TYPE_APPLICATION_OVERLAY,
                FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        topParams.x = 0;
        topParams.y = 0;
        topParams.gravity = Gravity.BOTTOM | Gravity.END;
        windowManager.addView(previewView, topParams);

        textureView = previewView.findViewById(R.id.texture);
        textureView.setOpaque(false);
        textureView.setAlpha(0.5f);
        result = previewView.findViewById(R.id.result);



        layoutInflater = LayoutInflater.from(this);
        topView = layoutInflater.inflate(R.layout.top, null);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                screenWidth,
                screenHeight,
                TYPE_APPLICATION_OVERLAY,
                FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.x = 0;
        params.y = 0;
        params.gravity = Gravity.BOTTOM | Gravity.END;
        windowManager.addView(topView, params);

        topRenderer=new PreviewRenderer(topView.findViewById(R.id.top), screenWidth, screenHeight);

        PreviewRenderer preview = new PreviewRenderer(previewView.findViewById(R.id.preview), 640, 480);

        AtomicLong prevTimestamp = new AtomicLong(-1);
        handTracking = new HandTracking(this,
                handsResult -> {
                    long timestamp = System.nanoTime();

                    if (prevTimestamp.get() != -1) {
                        Log.v(TAG, "FPS: " + 1000000000.0 / (timestamp - prevTimestamp.get()));
                    }

                    prevTimestamp.set(timestamp);
                    Log.v(TAG, handTracking.getMultiHandLandmarksDebugString(handsResult.multiHandLandmarks()));
                    gestureDetector.onResult(handsResult.multiHandLandmarks());
                    preview.renderAll(handsResult.multiHandLandmarks());
//                    topRenderer.renderAll(handsResult.multiHandLandmarks());

                });

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

//                mCameraHelper.initUSBMonitor(ControlService.this, textureView, listener, new SurfaceTexture[]{handTracking.start(640, 480), textureView.getSurfaceTexture()});
                mCameraHelper.initUSBMonitor(ControlService.this, textureView, listener, new SurfaceTexture[]{textureView.getSurfaceTexture()});
                mCameraHelper.registerUSB();
                Toast.makeText(ControlService.this, "usb device searching", Toast.LENGTH_SHORT).show();
//
//                cameraHandler.addOutput(new Surface(textureView.getSurfaceTexture()));
//
//                SurfaceTexture st = handTracking.start(640, 480);
//                cameraHandler.addOutput(new Surface(st));
//
//                cameraHandler.open();

            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
//                Toast.makeText(ControlService.this, "update" + updateCount, Toast.LENGTH_SHORT).show();
                updateCount++;
                handTracking.read(textureView);
            }
        });

        cameraHandler = new CameraHandler(this);

        GestureClassifier gesture = new GestureClassifier(this);
//        NDArray<Float> ndarray = NDArray.array(new Float[][][][]{{{{0.7656735181808472, 0.9956099390983582}, {0.48742011189460754, 1.0}, {0.25252485275268555, 0.9158596992492676}, {0.08947831392288208, 0.8199265599250793}, {0.07647056877613068, 0.7336122393608093}, {0.34111252427101135, 0.5908830761909485}, {0.11819829791784286, 0.5034666657447815}, {0.09526710957288742, 0.5975188612937927}, {0.13389049470424652, 0.6904770731925964}, {0.4371209442615509, 0.49401021003723145}, {0.22447067499160767, 0.265678346157074}, {0.09013903886079788, 0.1285553127527237}, {0.0, 0.030261900275945663}, {0.5337800979614258, 0.46071919798851013}, {0.3770633935928345, 0.24561843276023865}, {0.27734556794166565, 0.10515239089727402}, {0.21021106839179993, 0.0}, {0.6190279722213745, 0.4752565324306488}, {0.546207845211029, 0.28807029128074646}, {0.507331132888794, 0.18338467180728912}, {0.48426640033721924, 0.09985922276973724}}}, {{{0.7609496116638184, 0.994665801525116}, {0.4878763258457184, 1.0}, {0.2511581480503082, 0.9220247864723206}, {0.08369380980730057, 0.8317267298698425}, {0.07589569687843323, 0.7419882416725159}, {0.3404676616191864, 0.587861955165863}, {0.11672726273536682, 0.5028502941131592}, {0.09407778829336166, 0.5975444912910461}, {0.13614489138126373, 0.6936644315719604}, {0.4348195195198059, 0.4885086715221405}, {0.22088560461997986, 0.26047563552856445}, {0.08764822781085968, 0.12079178541898727}, {0.0, 0.021751001477241516}, {0.5293958187103271, 0.4544377326965332}, {0.3686755299568176, 0.24398019909858704}, {0.2678619921207428, 0.10408975183963776}, {0.20141713321208954, 0.0}, {0.6121099591255188, 0.46864354610443115}, {0.5401371717453003, 0.281037300825119}, {0.5033588409423828, 0.17733582854270935}, {0.48265358805656433, 0.09616787731647491}}}, {{{0.7676169872283936, 0.9975602626800537}, {0.486743688583374, 1.0}, {0.24563057720661163, 0.9153285026550293}, {0.07816022634506226, 0.8183532357215881}, {0.07222943753004074, 0.7264680862426758}, {0.3414570391178131, 0.5899231433868408}, {0.11750348657369614, 0.5018384456634521}, {0.09623098373413086, 0.5971101522445679}, {0.13855166733264923, 0.6927231550216675}, {0.4376240074634552, 0.49222949147224426}, {0.22464707493782043, 0.2625187039375305}, {0.09014471620321274, 0.12290753424167633}, {0.0, 0.024283789098262787}, {0.5327573418617249, 0.4586135149002075}, {0.3735774755477905, 0.24401691555976868}, {0.2729136049747467, 0.10406450182199478}, {0.2062220722436905, 0.0}, {0.6160471439361572, 0.47246167063713074}, {0.5430991649627686, 0.28509384393692017}, {0.5047159790992737, 0.1798630654811859}, {0.48459291458129883, 0.09682516008615494}}}, {{{0.7632354497909546, 0.9973993301391602}, {0.48571476340293884, 1.0}, {0.24752986431121826, 0.9149563312530518}, {0.08228792995214462, 0.8167629837989807}, {0.07782010734081268, 0.725324273109436}, {0.34205928444862366, 0.5908752083778381}, {0.1168675646185875, 0.5051708221435547}, {0.09534630179405212, 0.5974315404891968}, {0.13635428249835968, 0.6885067224502563}, {0.43744632601737976, 0.49367108941078186}, {0.22248166799545288, 0.26691415905952454}, {0.08843666315078735, 0.1290123462677002}, {0.0, 0.03106021322309971}, {0.5311946868896484, 0.4600712060928345}, {0.3722692131996155, 0.2461436688899994}, {0.27227088809013367, 0.10528148710727692}, {0.2061263918876648, 0.0}, {0.6130015850067139, 0.47381678223609924}, {0.540062427520752, 0.2873835265636444}, {0.5006539821624756, 0.18261563777923584}, {0.47907784581184387, 0.09985337406396866}}}, {{{0.7587209343910217, 0.9963989853858948}, {0.48255404829978943, 1.0}, {0.242584690451622, 0.9169018268585205}, {0.07428758591413498, 0.8217862248420715}, {0.06807924807071686, 0.7311611175537109}, {0.33799129724502563, 0.5876479148864746}, {0.11108420789241791, 0.5055447220802307}, {0.08932875096797943, 0.5999737977981567}, {0.13131801784038544, 0.6927490830421448}, {0.4323212802410126, 0.489607036113739}, {0.21812257170677185, 0.26412108540534973}, {0.0859885886311531, 0.12605071067810059}, {0.0, 0.028194256126880646}, {0.5254592895507812, 0.45606526732444763}, {0.3649337589740753, 0.2429145872592926}, {0.26429420709609985, 0.10354116559028625}, {0.19928699731826782, 0.0}, {0.6064649224281311, 0.47070589661598206}, {0.5327613353729248, 0.28164729475975037}, {0.49370822310447693, 0.1771327704191208}, {0.4738459289073944, 0.09563511610031128}}}, {{{0.7635454535484314, 0.9970068335533142}, {0.4843696057796478, 1.0}, {0.24525827169418335, 0.9197945594787598}, {0.07749848067760468, 0.8281116485595703}, {0.07048298418521881, 0.7383149862289429}, {0.3382781147956848, 0.592583179473877}, {0.11330481618642807, 0.5060954093933105}, {0.09066186100244522, 0.6005309224128723}, {0.1328524351119995, 0.6955975294113159}, {0.4325563311576843, 0.4942161440849304}, {0.21930459141731262, 0.2661539912223816}, {0.08678817749023438, 0.1257275938987732}, {0.0, 0.026287376880645752}, {0.5270524621009827, 0.459428995847702}, {0.365405797958374, 0.2470259815454483}, {0.26503196358680725, 0.10544156283140182}, {0.19966401159763336, 0.0}, {0.6104105114936829, 0.47273576259613037}, {0.5376063585281372, 0.285218745470047}, {0.5006590485572815, 0.18017356097698212}, {0.48139187693595886, 0.09739911556243896}}}, {{{0.7705519199371338, 0.9958568811416626}, {0.48895028233528137, 1.0}, {0.2505272924900055, 0.9148812890052795}, {0.08448518067598343, 0.8153414726257324}, {0.0819547176361084, 0.7219632863998413}, {0.3427196741104126, 0.5893479585647583}, {0.11733822524547577, 0.5034777522087097}, {0.09517218172550201, 0.596923828125}, {0.13595300912857056, 0.6895635724067688}, {0.4370581805706024, 0.49201130867004395}, {0.22427429258823395, 0.264380544424057}, {0.08998921513557434, 0.12504814565181732}, {0.0, 0.02605832740664482}, {0.532149076461792, 0.4583589434623718}, {0.3761387765407562, 0.24581275880336761}, {0.2772747874259949, 0.10487844049930573}, {0.21087756752967834, 0.0}, {0.6155033707618713, 0.4722249209880829}, {0.5436363816261292, 0.28699323534965515}, {0.5059726238250732, 0.1827150583267212}, {0.4847630262374878, 0.100856713950634}}}, {{{0.7587510347366333, 0.9975849986076355}, {0.4807816743850708, 1.0}, {0.24559982120990753, 0.9154271483421326}, {0.0827203169465065, 0.8171812891960144}, {0.07366704195737839, 0.724883496761322}, {0.3419197201728821, 0.5916445255279541}, {0.11635731905698776, 0.5035828351974487}, {0.08996361494064331, 0.5977224111557007}, {0.1283532977104187, 0.6912776231765747}, {0.43606194853782654, 0.4921276569366455}, {0.22258765995502472, 0.26358646154403687}, {0.08803509920835495, 0.12714551389217377}, {0.0, 0.030001316219568253}, {0.5289732813835144, 0.4565846025943756}, {0.3687096834182739, 0.24155360460281372}, {0.2656734585762024, 0.10314715653657913}, {0.197910875082016, 0.0}, {0.6101886630058289, 0.4686936140060425}, {0.5389285683631897, 0.2796149253845215}, {0.4981919229030609, 0.17455291748046875}, {0.47297531366348267, 0.09268362075090408}}}, {{{0.7604236602783203, 0.9933896064758301}, {0.486136794090271, 1.0}, {0.2492980659008026, 0.9252894520759583}, {0.08322035521268845, 0.839637041091919}, {0.06712915748357773, 0.7571606636047363}, {0.33186501264572144, 0.5897287130355835}, {0.10530097782611847, 0.5090169906616211}, {0.08269557356834412, 0.6039542555809021}, {0.12426556646823883, 0.6972935795783997}, {0.426836222410202, 0.49026307463645935}, {0.21613295376300812, 0.2636255919933319}, {0.08611812442541122, 0.12480478733778}, {0.0, 0.02406766451895237}, {0.5231080055236816, 0.4553431570529938}, {0.3666635751724243, 0.24247071146965027}, {0.2689700722694397, 0.10426691919565201}, {0.20547831058502197, 0.0}, {0.6084519028663635, 0.4685245752334595}, {0.535965621471405, 0.28021007776260376}, {0.495590478181839, 0.1739162653684616}, {0.47162652015686035, 0.09007071703672409}}}, {{{0.7601504325866699, 0.9927405118942261}, {0.48076876997947693, 1.0}, {0.24116414785385132, 0.9149863719940186}, {0.077190101146698, 0.8143783211708069}, {0.07141557335853577, 0.7202033400535583}, {0.33264970779418945, 0.5923770666122437}, {0.1100773811340332, 0.5068822503089905}, {0.08849822729825974, 0.599040150642395}, {0.12907832860946655, 0.6895073056221008}, {0.42914339900016785, 0.4942122995853424}, {0.21812203526496887, 0.2689617872238159}, {0.08567643910646439, 0.1316142976284027}, {0.0, 0.033494625240564346}, {0.5247655510902405, 0.4587518274784088}, {0.3668394982814789, 0.24460743367671967}, {0.2669145464897156, 0.10453630238771439}, {0.20278768241405487, 0.0}, {0.6086798906326294, 0.4704305827617645}, {0.5340559482574463, 0.28310543298721313}, {0.49389010667800903, 0.17841175198554993}, {0.47179940342903137, 0.09692945331335068}}}});
//        Log.v(TAG, String.valueOf(ndarray));
//        gesture.predict(ndarray);

        gestureDetector = new GestureDetector(this, gesture);

        controller=new GestureController(this);

//        CameraViewInterface UVCCameraView = (CameraViewInterface) topView;
        mCameraHelper = UVCCameraHelper.getInstance(640, 480);
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("ControlService");
        registerReceiver(broadcastReceiver, intentFilter);

        self = this;
    }
    PreviewRenderer topRenderer;
    private int screenWidth, screenHeight;

    private int updateCount = 0;

    GestureController controller;

    static NDArray<Float> boundingBox=NDArray.array(new Float[]{0.3f, 0.3f, 0.6f, 0.6f});
    public NDArray<Float> screenPosition(NDArray<Float> position){
        float x=position.getValue(0);
        float y=position.getValue(1);
        x = Math.min(Math.max(x, boundingBox.getValue(0)), boundingBox.getValue(2));
        y = Math.min(Math.max(y, boundingBox.getValue(1)), boundingBox.getValue(3));
        x =(x-boundingBox.getValue(0))/(boundingBox.getValue(2)-boundingBox.getValue(0));
        y =(y-boundingBox.getValue(1))/(boundingBox.getValue(3)-boundingBox.getValue(1));
        return NDArray.array(new Float[]{x,y});
    }

    static ControlService self;
    public static void showShortMsg(String msg) {
        Toast.makeText(self, msg, Toast.LENGTH_SHORT).show();
    }

    private List<DeviceInfo> getUSBDevInfo() {
        if(mCameraHelper == null)
            return null;
        List<DeviceInfo> devInfos = new ArrayList<>();
        List<UsbDevice> list = mCameraHelper.getUsbDeviceList();
        for(UsbDevice dev : list) {
            DeviceInfo info = new DeviceInfo();
            info.setPID(dev.getVendorId());
            info.setVID(dev.getProductId());
            devInfos.add(info);
        }
        return devInfos;
    }

    private void popCheckDevDialog() {
//        Toast.makeText(ControlService.this, "usb device detected", Toast.LENGTH_SHORT).show();
        List<DeviceInfo> infoList = getUSBDevInfo();
        if (infoList==null || infoList.isEmpty()) {
            Toast.makeText(ControlService.this, "Find devices failed.", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> dataList = new ArrayList<>();
        for(DeviceInfo deviceInfo : infoList){
            dataList.add("Device：PID_"+deviceInfo.getPID()+" & "+"VID_"+deviceInfo.getVID());
        }
        Toast.makeText(ControlService.this, String.join(", ", dataList), Toast.LENGTH_SHORT).show();
//        AlertCustomDialog.createSimpleListDialog(this, "Please select USB devcie", dataList, new AlertCustomDialog.OnMySelectedListener() {
//            @Override
//            public void onItemSelected(int position) {
//                Toast.makeText(ControlService.this,  "usb device selected: "+position, Toast.LENGTH_SHORT).show();
////                mCameraHelper.requestPermission(position);
//            }
//        });
        mCameraHelper.requestPermission(dataList.size()-1);
    }


    private boolean isRequest = false;
    private boolean isPreview;

    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            // request open permission
            if (!isRequest) {
                isRequest = true;
            }
            popCheckDevDialog();
        }


        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected, SurfaceTexture[] outputs) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
                // initialize seekbar
                // need to wait UVCCamera initialize over
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
                        if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
//                            mSeekBrightness.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_BRIGHTNESS));
//                            mSeekContrast.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_CONTRAST));
                            showShortMsg(Arrays.toString(outputs));
                            showShortMsg("connected");
                        }
                        Looper.loop();
                    }
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnecting");
        }
    };


    public enum GestureType {
        CURSOR, GRAB, POINT, THUMB, FIST, PALM, NEGATIVE
    }
    private GestureType prevGesture = GestureType.NEGATIVE;
    private Point prevPosition=new Point();


    static int MIN_GRAB_DURATION = 10;
    int grab_duration = 0;

    int steps=3;
    boolean touchStart = false;
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onResult(String s1, NDArray<Float> landmarks) {
        Intent intent1 = new Intent();
        intent1.setAction("ControlService");
        intent1.putExtra("result", s1);
        sendBroadcast(intent1);

        NDArray<Float> position=landmarks.get(4).copy();

        GestureType gesture;
        if(s1.equals("cursor")) gesture=GestureType.CURSOR;
        else if(s1.equals("grab")) gesture=GestureType.GRAB;
        else if(s1.equals("point")) gesture=GestureType.POINT;
        else if(s1.equals("thumb")) gesture=GestureType.THUMB;
        else if(s1.equals("fist")) gesture=GestureType.FIST;
        else if(s1.equals("palm")) gesture=GestureType.PALM;
        else gesture=GestureType.NEGATIVE;

//        NDArray<Float> screenPosition=NDArray.div(
//                NDArray.add(
//                        screenPosition(landmarks.get(4)).astype(Float::doubleValue),
//                        screenPosition(landmarks.get(8)).astype(Float::doubleValue)),
//                NDArray.array(new Double[]{2.0})).astype(Double::floatValue);
//
        NDArray<Float> screenPosition=screenPosition(landmarks.get(8)).astype(Float::doubleValue).astype(Double::floatValue);
        int x=Math.round(screenPosition.getValue(0)*screenWidth);
        int y=Math.round(screenPosition.getValue(1)*screenHeight);

        double norm = Math.sqrt(Math.pow(x-prevPosition.x, 2) + Math.pow(y-prevPosition.y, 2));
        double alpha = Math.exp(-norm * norm / 10000);

        x = (int) Math.round((1-alpha)*x + alpha*prevPosition.x);
        y = (int) Math.round((1-alpha)*y + alpha*prevPosition.y);

//        if (steps==3){
//            steps--;
//            controller.dragStart(100, 500);
//        }else if(steps==2){
//            steps--;
//            controller.dragMove(100, 650);
//        }else if(steps==1){
//            steps--;
//            controller.dragMove(100, 1500);
//        }else if(steps==0){
//            steps--;
//            controller.dragEnd(100, 100);
//        }

        topRenderer.clear();
        if(gesture==GestureType.CURSOR || gesture==GestureType.GRAB){
            topRenderer.drawCircle(x, y-110);

            if(touchStart){
                if(gesture==GestureType.GRAB){
                    grab_duration += 1;
                }else if(gesture==GestureType.CURSOR){

                }
                touchStart=false;
            }else if(gesture==GestureType.GRAB && grab_duration>0) {
                if (grab_duration == MIN_GRAB_DURATION) {
//                    controller.touchStart(x, y);
//                    controller.touchMove(x, y);
                    controller.dragMove(x, y);
                } else if (grab_duration > MIN_GRAB_DURATION) {
//                    controller.touchMove(x, y);
                    controller.dragMove(x, y);
                }
                grab_duration++;
            }

            if(prevGesture==GestureType.CURSOR && gesture==GestureType.GRAB) {
                Log.d(TAG, "touchStart");
                controller.dragStart(x, y);
                touchStart = true;
            }
        }

        if(gesture!=GestureType.GRAB && prevGesture==GestureType.GRAB) {
            Log.d(TAG,"GestureC: " + grab_duration);
            if (grab_duration < MIN_GRAB_DURATION) {
//                controller.touchStart(x, y);
                controller.click(prevPosition.x, prevPosition.y);
                Log.d(TAG, "Click: "+x+", "+y);
            }else if (grab_duration>0) {
                controller.dragEnd(x, y);
            }
            grab_duration = 0;
//            controller.touchEnd();
        }

//        if(s1.equals("cursor") || s1.equals("grab")){
//            NDArray<Float> screenPosition=screenPosition(position);
//            int x=Math.round(screenPosition.getValue(0)*screenWidth);
//            int y=Math.round(screenPosition.getValue(1)*screenHeight);
//            topRenderer.drawCircle(x, y);
//
//            if(s1.equals("grab")){
//                controller.click(x, y);
//            }
//        }

        if(s1.equals("grab")){

            Log.d(TAG, "Normalized Coordinates" + screenPosition(position));
//            controller.click()

        }



        if(gesture==GestureType.THUMB) {
            final float VOLUME_STEP = 100;
            NDArray<Float> position_0 = screenPosition(landmarks.get(0));
            int x0 = Math.round(position_0.getValue(0) * screenWidth);
            int y0 = Math.round(position_0.getValue(1) * screenHeight);
            if (holdGesture==gesture) {
                int diff = (int)((x0 - initialPosition.x) / VOLUME_STEP - currentSteps);
                if (diff > 0) {
                    for (int i = 0; i < diff; i++) {
                        controller.dispatch(AudioManager.ADJUST_RAISE);
                        currentSteps++;
                    }
                } else if (diff < 0) {
                    for (int i = 0; i < -diff; i++) {
                        controller.dispatch(AudioManager.ADJUST_LOWER);
                        currentSteps--;
                    }
                }
                Log.d(TAG, "currentSteps: "+currentSteps+", diff: "+diff);
            }else{
                currentSteps = 0;
                initialPosition.set(x0, y0);
            }
        }

        if(gesture==GestureType.POINT){
            final float VOLUME_STEP = 500;
            NDArray<Float> position_0 = screenPosition(landmarks.get(8));
            int x0 = Math.round(position_0.getValue(0) * screenWidth);
            int y0 = Math.round(position_0.getValue(1) * screenHeight);
            if (prevGesture == GestureType.POINT) {
                int diff = (int)((x0 - initialPosition.x) / VOLUME_STEP);
                if (diff > 0) {
                    controller.skipForward();
                    initialPosition.set(x0, y0);
                } else if (diff < 0) {
                    controller.skipBackward();
                    initialPosition.set(x0, y0);
                }
                Log.d(TAG, "currentSteps: "+currentSteps+", diff: "+diff);
            }else{
                currentSteps = 0;
                initialPosition.set(x0, y0);
            }
        }

        if(gesture == prevGesture){
            holdCounter++;
            if(holdCounter==MIN_HOLD){
                lastHoldGesture=holdGesture;
                holdGesture=gesture;
            }
        }else{
            holdCounter=0;
        }

        if(lastHoldGesture==GestureType.PALM && holdGesture==GestureType.FIST){
            controller.playPause();
            resetCombo();
        }

        topRenderer.update();

        prevGesture = gesture;
        prevPosition.set(x, y);

        Log.d(TAG, "Hold gesture: "+holdGesture+", last: "+lastHoldGesture);
    }
    GestureType firstGesture=GestureType.NEGATIVE;
    int currentSteps=0;
    Point initialPosition=new Point();
    float progress=0.0f;

    void resetCombo(){
        holdGesture =GestureType.NEGATIVE;
        lastHoldGesture =GestureType.NEGATIVE;
        holdCounter = 0;
    }
    GestureType holdGesture =GestureType.NEGATIVE;
    GestureType lastHoldGesture =GestureType.NEGATIVE;
    int holdCounter = 0;
    int MIN_HOLD=5;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onDestroy() {
        if (topView != null) {
            windowManager.removeView(topView);
        }
        if (previewView != null) {
            windowManager.removeView(previewView);
        }

        unregisterReceiver(broadcastReceiver);

        cameraHandler.close();
        handTracking.stop();
        controller.close();
        Log.d(TAG, "onDestroy");

        stopForeground(true);

        super.onDestroy();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }
}
