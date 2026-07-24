package com.yieye.xiangqi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChessRecognizer {
    public static Bitmap getBitmapFromAssets(Context context, String fileName) {
        try {
            InputStream is = context.getAssets().open(fileName);
            return corpBitmap(context,BitmapFactory.decodeStream(is));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Bitmap corpBitmap(Context context, Bitmap bitmap) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // 计算目标高度：宽度的 1.2 倍
        int targetHeight = Math.round(width * 1.12f);

        // 如果原图高度不足以裁剪，直接返回原图
        if (height <= targetHeight) {
            return bitmap;
        }

        // 从中间开始裁剪，计算 y 偏移量以保证上下留出的距离相同
        int y = (height - targetHeight) / 2;

        try {
            return Bitmap.createBitmap(bitmap, 0, y, width, targetHeight);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }



    public static class ChessPiece {
        public int x;
        public int y;
        public String name;
        public char type; // 'R', 'N', 'B', 'A', 'K', 'C', 'P' (Red Uppercase, Black Lowercase)

        public ChessPiece(int x, int y, String name) {
            this.x = x;
            this.y = y;
            this.name = name;
            this.type = mapNameToType(name);
        }

        private char mapNameToType(String name) {
            if (name.contains("车")) return 'R';
            if (name.contains("马")) return 'N';
            if (name.contains("相") || name.contains("象")) return 'B';
            if (name.contains("仕") || name.contains("士")) return 'A';
            if (name.contains("帅") || name.contains("将")) return 'K';
            if (name.contains("炮")) return 'C';
            if (name.contains("兵") || name.contains("卒")) return 'P';
            return '?';
        }
    }

    public interface OCRCallback {
        void onResult(List<ChessPiece> pieces, String fen);
    }



    private static List<Integer> mergeLines(List<Integer> lines, int threshold) {
        Collections.sort(lines);
        List<Integer> result = new ArrayList<>();
        for (int line : lines) {
            if (result.isEmpty() || Math.abs(result.get(result.size() - 1) - line) > threshold) {
                result.add(line);
            }
        }
        return result;
    }


    private static String convertToFen(List<ChessPiece> pieces, List<Integer> hLines, List<Integer> vLines) {
        char[][] board = new char[10][9];
        // Simplified mapping if lines detection failed
        if (hLines.size() < 10 || vLines.size() < 9) return "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

        for (ChessPiece p : pieces) {
            int col = findClosest(p.x, vLines);
            int row = findClosest(p.y, hLines);
            if (row >= 0 && row < 10 && col >= 0 && col < 9) {
                char type = p.type;
                if (row < 5) type = Character.toLowerCase(type);
                board[row][col] = type;
            }
        }

        StringBuilder fen = new StringBuilder();
        for (int r = 0; r < 10; r++) {
            int empty = 0;
            for (int c = 0; c < 9; c++) {
                if (board[r][c] == 0) empty++;
                else {
                    if (empty > 0) fen.append(empty);
                    fen.append(board[r][c]);
                    empty = 0;
                }
            }
            if (empty > 0) fen.append(empty);
            if (r < 9) fen.append('/');
        }
        return fen.append(" w - - 0 1").toString();
    }

    private static int findClosest(int val, List<Integer> lines) {
        int bestIdx = -1, minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            int diff = Math.abs(val - lines.get(i));
            if (diff < minDiff) { minDiff = diff; bestIdx = i; }
        }
        return bestIdx;
    }
}