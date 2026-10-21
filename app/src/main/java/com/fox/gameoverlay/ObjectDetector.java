package com.nyx.esp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class ObjectDetector {
    private Interpreter tfLite;
    private static final int INPUT_SIZE = 300; // Ukuran standar SSD MobileNet

    public ObjectDetector(Context context, String modelPath) {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            tfLite = new Interpreter(buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<float[]> detect(Bitmap bitmap) {
        List<float[]> results = new ArrayList<>();
        if (tfLite == null) return results;

        // Resize bitmap ke 300x300
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // R
            inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);  // G
            inputBuffer.putFloat((pixel & 0xFF) / 255.0f);         // B
        }

        // Output standar Google SSD: [1, 10, 4], [1, 10], [1, 10], [1, 1]
        float[][][] outputLocations = new float[1][10][4];
        float[][] outputClasses = new float[1][10];
        float[][] outputScores = new float[1][10];
        float[] outputNumDetections = new float[1];

        Object[] inputs = {inputBuffer};
        java.util.Map<Integer, Object> outputs = new java.util.HashMap<>();
        outputs.put(0, outputLocations);
        outputs.put(1, outputClasses);
        outputs.put(2, outputScores);
        outputs.put(3, outputNumDetections);

        tfLite.runForMultipleInputsOutputs(inputs, outputs);

        int numDetections = (int) outputNumDetections[0];
        for (int i = 0; i < numDetections; i++) {
            float score = outputScores[0][i];
            if (score > 0.5f) { // Confidence minimal 50%
                float top = outputLocations[0][i][0];
                float left = outputLocations[0][i][1];
                float bottom = outputLocations[0][i][2];
                float right = outputLocations[0][i][3];
                
                // Output dalam bentuk koordinat relatif (0.0 - 1.0)
                results.add(new float[]{left, top, right, bottom, score});
            }
        }
        return results;
    }
}
