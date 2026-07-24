package com.yieye.xiangqi;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

public class ImageHelper {
    public static Random Rand = new Random();

    public static boolean compareMemCmp(Bitmap b1, Bitmap b2) {
        if (b1 == null || b2 == null) return b1 == b2;
        if (b1.getWidth() != b2.getWidth() || b1.getHeight() != b2.getHeight()) return false;
        return b1.sameAs(b2);
    }

    public static Bitmap randomObstacle(Bitmap bitmap) {
        Bitmap nbmp = bitmap.copy(bitmap.getConfig(), true);
        Canvas canvas = new Canvas(nbmp);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        int rx = Rand.nextInt(width * 2 / 3);
        int ry = Rand.nextInt(height * 2 / 3);
        int rw = Rand.nextInt(width / 2) + width / 5;
        int rh = Rand.nextInt(height / 2) + height / 5;
        
        Rect rect = new Rect(rx, ry, Math.min(rx + rw, width), Math.min(ry + rh, height));
        Paint paint = new Paint();
        paint.setColor(Color.argb(Rand.nextInt(80) + 50, Rand.nextInt(256), Rand.nextInt(256), Rand.nextInt(256)));
        
        if (Rand.nextInt(2) == 0) {
            canvas.drawRect(new RectF(rect), paint);
        } else {
            canvas.drawOval(new RectF(rect), paint);
        }
        return nbmp;
    }

    public static Bitmap adjustImage(Bitmap bitmap, float brightness, float contrast, float gamma) {
        if (bitmap == null) return null;
        Bitmap adjustedImage = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(adjustedImage);
        
        float b = (brightness - 1.0f) * 255;
        float[] ptsArray = {
            contrast, 0, 0, 0, b,
            0, contrast, 0, 0, b,
            0, 0, contrast, 0, b,
            0, 0, 0, 1, 0
        };

        ColorMatrix cm = new ColorMatrix(ptsArray);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        
        // Note: Gamma adjustment is complex in ColorMatrix. 
        // For full fidelity, a Lookup Table (LUT) would be needed.
        return adjustedImage;
    }

    public static byte[] shaHash(Bitmap bitmap) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] bytes = stream.toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static boolean areEqual(Bitmap imageA, Bitmap imageB) {
        if (imageA.getWidth() != imageB.getWidth() || imageA.getHeight() != imageB.getHeight()) return false;
        byte[] hashA = shaHash(imageA);
        byte[] hashB = shaHash(imageB);
        return Arrays.equals(hashA, hashB);
    }

    public static Bitmap cropImage(Bitmap img, Rect cropArea) {
        try {
            return Bitmap.createBitmap(img, cropArea.left, cropArea.top, cropArea.width(), cropArea.height());
        } catch (Exception e) {
            return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        }
    }

    public static boolean detectImage(Bitmap sample, Bitmap model) {
        return findImage(sample, model, 30).x != -1;
    }

    public static Point findImage(Bitmap sample, Bitmap model, int threshold) {
        int sWidth = sample.getWidth(), sHeight = sample.getHeight();
        int mWidth = model.getWidth(), mHeight = model.getHeight();
        
        for (int y = 0; y <= sHeight - mHeight; y++) {
            for (int x = 0; x <= sWidth - mWidth; x++) {
                if (compareImage(sample, model, x, y, threshold)) return new Point(x, y);
            }
        }
        return new Point(-1, -1);
    }

    public static Point findImageFromLeft(Bitmap sample, Bitmap model, int threshold, int startFindX, int startFindY, int deltaX, int deltaY) {
        int sWidth = sample.getWidth(), sHeight = sample.getHeight();
        int mWidth = model.getWidth(), mHeight = model.getHeight();
        
        int endX = Math.min(sWidth - mWidth, startFindX + deltaX);
        int endY = Math.min(sHeight - mHeight, startFindY + deltaY);

        for (int x = startFindX; x < endX; x++) {
            for (int y = startFindY; y < endY; y++) {
                if (compareImage(sample, model, x, y, threshold)) return new Point(x, y);
            }
        }
        return new Point(-1, -1);
    }

    public static Point findImageFromTop(Bitmap sample, Bitmap model, int threshold, int startFindX, int startFindY, int deltaX, int deltaY) {
        int sWidth = sample.getWidth(), sHeight = sample.getHeight();
        int mWidth = model.getWidth(), mHeight = model.getHeight();
        
        int endX = Math.min(sWidth - mWidth, startFindX + deltaX);
        int endY = Math.min(sHeight - mHeight, startFindY + deltaY);

        for (int y = startFindY; y < endY; y++) {
            for (int x = startFindX; x < endX; x++) {
                if (compareImage(sample, model, x, y, threshold)) return new Point(x, y);
            }
        }
        return new Point(-1, -1);
    }

    public static Point findImageFromRight(Bitmap sample, Bitmap model, int threshold, int startFindX, int startFindY, int deltaX, int deltaY) {
        int sWidth = sample.getWidth(), sHeight = sample.getHeight();
        int mWidth = model.getWidth(), mHeight = model.getHeight();
        
        int endX = Math.min(sWidth - mWidth, startFindX + deltaX);
        int endY = Math.min(sHeight - mHeight, startFindY + deltaY);

        for (int x = endX; x >= startFindX; x--) {
            for (int y = startFindY; y < endY; y++) {
                if (compareImage(sample, model, x, y, threshold)) return new Point(x, y);
            }
        }
        return new Point(-1, -1);
    }

    public static Point findImageFromBottomRight(Bitmap sample, Bitmap model, int threshold, int startFindX, int startFindY, int deltaX, int deltaY) {
        int sWidth = sample.getWidth(), sHeight = sample.getHeight();
        int mWidth = model.getWidth(), mHeight = model.getHeight();
        
        int endX = Math.min(sWidth - mWidth, startFindX + deltaX);
        int endY = Math.min(sHeight - mHeight, startFindY + deltaY);

        for (int x = endX; x >= startFindX; x--) {
            for (int y = endY; y >= startFindY; y--) {
                if (compareImage(sample, model, x, y, threshold)) return new Point(x, y);
            }
        }
        return new Point(-1, -1);
    }

    public static boolean compareImage(Bitmap sample, Bitmap model, int startX, int startY, int threshold) {
        int mWidth = model.getWidth(), mHeight = model.getHeight();
        for (int y = 0; y < mHeight; y++) {
            for (int x = 0; x < mWidth; x++) {
                int modelPixel = model.getPixel(x, y);
                if (Color.alpha(modelPixel) != 0) {
                    int samplePixel = sample.getPixel(startX + x, startY + y);
                    if (getColorDiff(samplePixel, modelPixel) > threshold) return false;
                }
            }
        }
        return true;
    }

    public static int getRGBAvg(int c) {
        return (Color.red(c) + Color.green(c) + Color.blue(c)) / 3;
    }

    public static int getColorDiff(int c1, int c2) {
        return Math.abs(Color.red(c1) - Color.red(c2)) +
               Math.abs(Color.green(c1) - Color.green(c2)) +
               Math.abs(Color.blue(c1) - Color.blue(c2));
    }

    public static Bitmap cutImage(Bitmap bm, Rect rect) {
        return cropImage(bm, rect);
    }

    public static byte[][][] image2ByteArray(Bitmap img) {
        int width = img.getWidth();
        int height = img.getHeight();
        byte[][][] imgData = new byte[width][height][4];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = img.getPixel(x, y);
                imgData[x][y][0] = (byte) Color.alpha(p);
                imgData[x][y][1] = (byte) Color.red(p);
                imgData[x][y][2] = (byte) Color.green(p);
                imgData[x][y][3] = (byte) Color.blue(p);
            }
        }
        return imgData;
    }

    public static Bitmap getWhiteTextFromImage(Bitmap bm, int threshold) {
        Bitmap rbm = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < bm.getHeight(); y++) {
            for (int x = 0; x < bm.getWidth(); x++) {
                int c = bm.getPixel(x, y);
                double gray = Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114;
                if (gray >= threshold) {
                    rbm.setPixel(x, y, Color.BLACK);
                } else {
                    rbm.setPixel(x, y, Color.TRANSPARENT);
                }
            }
        }
        return rbm;
    }
}
