package sharpeye.sharpeye.tflite;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/** Generic interface for interacting with different recognition engines. */
public interface Classifier {
    List<Recognition> recognizeImage(Bitmap bitmap);

    void enableStatLogging(final boolean debug);

    String getStatString();

    void close();

    void setNumThreads(int num_threads);

    void setUseNNAPI(boolean isChecked);

    /** An immutable result returned by a Classifier describing what was recognized. */
    class Recognition implements Parcelable {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /** Display name for the recognition. */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        /** Optional location within the source image for the location of the recognized object. */
        private RectF location;

        /** A unique identifier used for the object tracking */
        private int opencvID;

        public Recognition(
                final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
            this.opencvID = -1;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        public int getOpencvID() {
            return opencvID;
        }

        public void setOpencvID(int opencvID) {
            this.opencvID = opencvID;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }

        private Recognition(Parcel in) {
            id = in.readString();
            title = in.readString();
            confidence = in.readFloat();
            location = in.readParcelable(RectF.class.getClassLoader());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(id);
            dest.writeString(title);
            dest.writeFloat(confidence);
            dest.writeParcelable(location, flags);
        }

        public static final Parcelable.Creator<Recognition> CREATOR = new Parcelable.Creator<Recognition>() {
            public Recognition createFromParcel(Parcel in) {
                return new Recognition(in);
            }

            public Recognition[] newArray(int size) {
                return new Recognition[size];
            }
        };
    }
}
