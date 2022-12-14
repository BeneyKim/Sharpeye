package sharpeye.sharpeye.tracking;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import sharpeye.sharpeye.signs.BipGenerator;
import sharpeye.sharpeye.tflite.Classifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tracker implements Parcelable {

    static {
        System.loadLibrary("native-lib");
    }

    private long trackerAddress;
    private HashMap<Integer, Classifier.Recognition> trackedObjects;
    private boolean alertCollision;
    private BipGenerator bipGenerator;
    private long lastBip;

    public Tracker() {
        trackerAddress = -1;
        trackedObjects = new HashMap<>();
        alertCollision = false;
        bipGenerator = new BipGenerator();
        lastBip = SystemClock.uptimeMillis();
    }

    public  boolean needInit() {
        return (trackerAddress <= 0);
    }

    public void init() {
        trackerAddress = createTracker();
    }

    public void free() {
        deleteTracker(trackerAddress);
        trackerAddress = -1;
    }

    @SuppressWarnings("unchecked")
    private Tracker(Parcel in) {
        this.trackerAddress = in.readLong();
        this.trackedObjects = new HashMap<>();
        if (in.readByte() == 1) {
            int[] ids = in.createIntArray();
            List<Classifier.Recognition> recognitions = in.readArrayList(Classifier.Recognition.class.getClassLoader());
            for (int i = 0; i < ids.length; ++i) {
                trackedObjects.put(ids[i], recognitions.get(i));
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(trackerAddress);
        if (!trackedObjects.isEmpty()) {
            dest.writeByte((byte)1);
            int[] ids = new int[trackedObjects.size()];
            int i = -1;
            List<Classifier.Recognition> recognitions = new ArrayList<>();
            for (Map.Entry<Integer, Classifier.Recognition> entry : trackedObjects.entrySet()) {
                ids[++i] = entry.getKey();
                recognitions.add(entry.getValue());
            }
            dest.writeIntArray(ids);
            dest.writeList(recognitions);
        } else {
            dest.writeByte((byte)0);
        }
    }

    public static final Parcelable.Creator<Tracker> CREATOR = new Parcelable.Creator<Tracker>() {
        public Tracker createFromParcel(Parcel in) {
            return new Tracker(in);
        }

        public Tracker[] newArray(int size) {
            return new Tracker[size];
        }
    };

    private Mat bitmapToMat(Bitmap bmp) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bmp, mat);
        Mat noAlphaMat = new Mat();
        Imgproc.cvtColor(mat, noAlphaMat, Imgproc.COLOR_RGBA2RGB);
        return noAlphaMat;
    }

    private Classifier.Recognition findRecognitionObjectWithRect(List<Classifier.Recognition> initialList, Rect2f box) {
        for (Classifier.Recognition object: initialList) {
            RectF location = object.getLocation();
            if (location.left == box.x && location.top == box.y &&
                    location.width() == box.width && location.height() == box.height) {
                return object;
            }
        }
        throw new UnknownError("Cannot find the initial recognition object");
    }

    public void track(Bitmap frame, List<Classifier.Recognition> objects) {
        ArrayList<Rect2f> boxes = new ArrayList<>();
        for (Classifier.Recognition object: objects) {
            RectF location = object.getLocation();
            if (location.width() >= 2.0 && location.height() >= 2.0) {
                Rect2f box = new Rect2f(location.left, location.top, location.width(), location.height());
                boxes.add(box);
            }
        }
        Mat matFrame = bitmapToMat(frame);
        long frameAddress = matFrame.nativeObj;
        HashMap<Integer, Rect2f> objectIDs = addBoxes(trackerAddress, frameAddress, boxes);
        HashMap<Integer, Classifier.Recognition> newTrackedObjects = new HashMap<>();
        for (HashMap.Entry<Integer, Rect2f> objectID: objectIDs.entrySet()) {
            Integer id = objectID.getKey();
            Rect2f box = objectID.getValue();
            Classifier.Recognition recognizedObject;
            recognizedObject = findRecognitionObjectWithRect(objects, box);
            recognizedObject.setOpencvID(id);
            newTrackedObjects.put(id, recognizedObject);
        }
        trackedObjects = newTrackedObjects;
    }

    public List<Classifier.Recognition> update(Bitmap frame, double speed) {
        Mat matFrame = bitmapToMat(frame);
        long frameAddress = matFrame.nativeObj;
        HashMap<Integer, Rect2f> objectIDs = updateBoxes(trackerAddress, frameAddress, speed);
        alertCollision = isDangerous(trackerAddress);
        HashMap<Integer, Classifier.Recognition> newTrackedObjects = new HashMap<>();
        List<Classifier.Recognition> recognitionList = new ArrayList<>();
        for (HashMap.Entry<Integer, Rect2f> objectID: objectIDs.entrySet()) {
            Integer id = objectID.getKey();
            Rect2f box = objectID.getValue();
            if (trackedObjects.containsKey(id)) {
                Classifier.Recognition recognizedObject = trackedObjects.get(id);
                if (recognizedObject != null) {
                    recognizedObject.setOpencvID(id);
                    recognizedObject.setLocation(new RectF(box.x, box.y, box.width + box.x, box.height + box.y));
                    newTrackedObjects.put(id, recognizedObject);
                    recognitionList.add(recognizedObject);
                }
            }
        }
        trackedObjects = newTrackedObjects;
        return recognitionList;
    }

    public boolean isAlertCollision() {
        return alertCollision;
    }

    public void alertIfDangerous(double speed) {
        if (speed > 10 && alertCollision) {
            if (bipGenerator == null) {
                bipGenerator = new BipGenerator();
            }
            if (SystemClock.uptimeMillis() - lastBip > 300) {
                bipGenerator.bip(150, 100);
                lastBip = SystemClock.uptimeMillis();
            }
            Log.i("Collision", "Situation is dangerous");
        }
    }

    public static class Rect2f {
        public float x;
        public float y;
        public float width;
        public float height;

        public Rect2f(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private native long createTracker();
    private native void deleteTracker(long ptr);
    private native HashMap<Integer, Rect2f> addBoxes(long trackerAddress, long frameAddress, ArrayList<Rect2f> boxes);
    private native HashMap<Integer, Rect2f> updateBoxes(long trackerAddress, long frameAddress, double speed);
    private native boolean isDangerous(long trackerAddress);

}
