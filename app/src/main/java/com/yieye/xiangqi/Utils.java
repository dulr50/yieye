package com.yieye.xiangqi;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Size;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    public static byte[] readAsset(Context context, String fileName) throws IOException {
        InputStream is = context.getAssets().open(fileName);
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();
        return buffer;
    }

    public static class BoardCompareResult {
        // C# 版本里 BoardCompareResult 是 struct，From/To 未赋值时默认 Point(0,0)；
        // Android 这里是 class，Point 引用不赋值默认是 null。为避免以后有代码在
        // diffCount==0 时读取 from/to 触发空指针，这里显式给一个和项目里其它地方
        // (如 ImageHelper.findImage 找不到时返回 (-1,-1)) 一致的"无效坐标"默认值。
        public Point from = new Point(-1, -1);
        public Point to = new Point(-1, -1);
        public String chess;
        public String target;
        public int diffCount;
        public int redDiff;
        public int blackDiff;
    }

    public static String intToChina(Context context, int x) {
        if (x < 0 || x > 9) return "";
        int[] resIds = {
                R.string.chess_zero, R.string.chess_one, R.string.chess_two,
                R.string.chess_three, R.string.chess_four, R.string.chess_five,
                R.string.chess_six, R.string.chess_seven, R.string.chess_eight, R.string.chess_nine
        };
        return context.getString(resIds[x]);
    }

    public static String nameToChina(Context context, String name) {
        if (name == null || name.length() < 2) return "";
        boolean isRed = name.substring(0, 1).equals("r");
        String type = name.substring(2);
        if (type.equals("che")) {
            return context.getString(R.string.chess_che);
        } else if (type.equals("ma")) {
            return context.getString(R.string.chess_ma);
        } else if (type.equals("xiang")) {
            return isRed ? context.getString(R.string.chess_xiang) : context.getString(R.string.chess_xiang_black);
        } else if (type.equals("shi")) {
            return isRed ? context.getString(R.string.chess_shi) : context.getString(R.string.chess_shi_black);
        } else if (type.equals("jiang")) {
            return isRed ? context.getString(R.string.chess_jiang) : context.getString(R.string.chess_jiang_black);
        } else if (type.equals("pao")) {
            return context.getString(R.string.chess_pao);
        } else if (type.equals("bing")) {
            return isRed ? context.getString(R.string.chess_bing) : context.getString(R.string.chess_bing_black);
        }
        return type;
    }

    public static String changeStrToSBC(String str) {
        char[] c = str.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] >= 33 && c[i] <= 126) {
                c[i] = (char) (c[i] + 65248);
            } else if (c[i] == 32) {
                c[i] = (char) 12288;
            }
        }
        return new String(c);
    }

    public static String[][] fenToBoard(String fen) {
        String[][] board = new String[9][10];
        String[] parts = fen.split(" ");
        String[] rows = parts[0].split("/");
        boolean redSide = parts.length > 1 && parts[1].equals("w");

        Map<Character, String> fenMap = new HashMap<>();
        fenMap.put('r', "che"); fenMap.put('n', "ma"); fenMap.put('b', "xiang");
        fenMap.put('a', "shi"); fenMap.put('k', "jiang"); fenMap.put('c', "pao");
        fenMap.put('p', "bing");

        for (int y = 0; y < 10; y++) {
            String row = rows[y];
            int x = 0;
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (Character.isDigit(c)) {
                    x += Character.getNumericValue(c);
                } else {
                    String side = Character.isUpperCase(c) ? "r" : "b";
                    String type = fenMap.get(Character.toLowerCase(c));
                    board[x][y] = side + "_" + type;
                    x++;
                }
            }
        }
        if (!redSide) {
            String[][] rotatedBoard = new String[9][10];
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 10; y++) {
                    rotatedBoard[8 - x][9 - y] = board[x][y];
                }
            }
            return rotatedBoard;
        }
        return board;
    }

    public static String fenToChina(Context context, String fen, String move) {
        String[][] board = fenToBoard(fen);
        String[] parts = fen.split(" ");
        boolean redSide = parts.length > 1 && parts[1].equals("w");
        return fenToChina(context, board, new String[]{move}, redSide);
    }

    public static String fenToChina(Context context, String[][] cboard, String[] moves, boolean redSide) {
        String[][] board = new String[9][10];
        for (int i = 0; i < 9; i++) {
            System.arraycopy(cboard[i], 0, board[i], 0, 10);
        }
        String[] resultMoves = moves.clone();
        for (int i = 0; i < resultMoves.length; i++) {
            String ret = "";
            Point fromPoint = move2Point(resultMoves[i].substring(0, 2), redSide);
            Point toPoint = move2Point(resultMoves[i].substring(2, 4), redSide);
            String name = "";
            try {
                name = board[fromPoint.x][fromPoint.y];
            } catch (Exception e) {

            }
            if (name == null || name.isEmpty()) continue;
            boolean isRed = name.startsWith("r");

            int X1 = fromPoint.x + 1;
            int X2 = toPoint.x + 1;
            int Y = toPoint.y - fromPoint.y;
            if (redSide == isRed) {
                X1 = 10 - X1;
                X2 = 10 - X2;
                Y = -1 * Y;
            }
            String moveName = "";

            if (name.contains("che") || name.contains("ma") || name.contains("pao") || name.contains("bing")) {
                int front = 0, back = 0;
                for (int j = 0; j < 10; j++) {
                    if (name.equals(board[fromPoint.x][j])) {
                        if (j < fromPoint.y) front++;
                        if (j > fromPoint.y) back++;
                    }
                }
                if (front > 0 || back > 0) {
                    if (back == 0 && front > 0) {
                        moveName = context.getString(R.string.chess_hou);
                    } else if (back > 0 && front == 0) {
                        moveName = context.getString(R.string.chess_qian);
                    } else if (back == 1 && front == 1) {
                        moveName = context.getString(R.string.chess_zhong);
                    } else {
                        moveName = intToChina(context, front + 1);
                    }
                }
            }
            String startStr = "";
            if (moveName.isEmpty()) startStr = isRed ? intToChina(context, X1) : changeStrToSBC(X1 + "");
            
            moveName += nameToChina(context, name);
            String moveDir = "";
            if (Y == 0) {
                moveDir = context.getString(R.string.chess_ping) + (isRed ? intToChina(context, X2) : changeStrToSBC(X2 + ""));
            } else if (Y > 0) {
                moveDir = context.getString(R.string.chess_jin) + (isRed ? intToChina(context, Y) : changeStrToSBC(Y + ""));
            } else {
                moveDir = context.getString(R.string.chess_tui) + (isRed ? intToChina(context, -Y) : changeStrToSBC(-Y + ""));
            }

            board[fromPoint.x][fromPoint.y] = null;
            board[toPoint.x][toPoint.y] = name;

            String type = name.substring(2);
            if (type.equals("jiang")) {
                ret = moveName + startStr + moveDir;
            } else if (type.equals("shi") || type.equals("xiang") || type.equals("ma")) {
                String endStr = isRed ? intToChina(context, X2) : changeStrToSBC(X2 + "");
                if (Y > 0) {
                    moveDir = context.getString(R.string.chess_jin);
                } else {
                    moveDir = context.getString(R.string.chess_tui);
                }
                ret = moveName + startStr + moveDir + endStr;
            } else {
                ret = moveName + startStr + moveDir;
            }
            resultMoves[i] = ret;
        }
        return String.join(" ", resultMoves);
    }

    public static BoardCompareResult compareBoard(String[][] from, String[][] to) {
        BoardCompareResult result = new BoardCompareResult();
        int diffCount = 0;
        if (from == null || to == null) {
            result.diffCount = 32;
            return result;
        }
        int bFromCount = 0, bToCount = 0, rFromCount = 0, rToCount = 0;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (from[x][y] != null && from[x][y].contains("b_")) bFromCount++;
                if (to[x][y] != null && to[x][y].contains("b_")) bToCount++;
                if (from[x][y] != null && from[x][y].contains("r_")) rFromCount++;
                if (to[x][y] != null && to[x][y].contains("r_")) rToCount++;

                if (from[x][y] == null ? to[x][y] != null : !from[x][y].equals(to[x][y])) {
                    if (to[x][y] == null) {
                        result.from = new Point(x, y);
                    } else {
                        result.to = new Point(x, y);
                        result.chess = to[x][y];
                        result.target = from[x][y];
                    }
                    diffCount++;
                }
            }
        }
        result.redDiff = rFromCount - rToCount;
        result.blackDiff = bFromCount - bToCount;
        result.diffCount = diffCount;
        return result;
    }

    public static String boardToFen(String[][] board, boolean redSide) {
        return boardToFen(board, redSide ? "w" : "b", redSide ? "w" : "b");
    }

    public static String boardToFen(String[][] board, String myPos, String nextPlayer) {
        Map<String, String> fenMap = new HashMap<>();
        fenMap.put("che", "r"); fenMap.put("ma", "n"); fenMap.put("xiang", "b");
        fenMap.put("shi", "a"); fenMap.put("jiang", "k"); fenMap.put("pao", "c");
        fenMap.put("bing", "p");

        StringBuilder fen = new StringBuilder();
        int emptyCount = 0;
        if (board == null) return "";

        if (myPos.equals("w")) {
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (board[x][y] == null) {
                        emptyCount++;
                    } else {
                        if (emptyCount > 0) {
                            fen.append(emptyCount);
                            emptyCount = 0;
                        }
                        String[] nameInfo = board[x][y].split("_");
                        String symbol = fenMap.get(nameInfo[1]);
                        if (nameInfo[0].equals("r")) fen.append(symbol.toUpperCase());
                        else fen.append(symbol);
                    }
                }
                if (emptyCount > 0) {
                    fen.append(emptyCount);
                    emptyCount = 0;
                }
                if (y < 9) fen.append("/");
            }
        } else {
            for (int y = 9; y >= 0; y--) {
                for (int x = 8; x >= 0; x--) {
                    if (board[x][y] == null) {
                        emptyCount++;
                    } else {
                        if (emptyCount > 0) {
                            fen.append(emptyCount);
                            emptyCount = 0;
                        }
                        String[] nameInfo = board[x][y].split("_");
                        String symbol = fenMap.get(nameInfo[1]);
                        if (nameInfo[0].equals("r")) fen.append(symbol.toUpperCase());
                        else fen.append(symbol);
                    }
                }
                if (emptyCount > 0) {
                    fen.append(emptyCount);
                    emptyCount = 0;
                }
                if (y > 0) fen.append("/");
            }
        }

        return fen.toString() + " " + nextPlayer;
    }

    public static String mirrorFenLeftRight(String fen) {
        String[] args = fen.split(" ");
        String board = args[0];
        String[] rows = board.split("/");
        List<String> newRows = new ArrayList<>();
        for (String row : rows) {
            newRows.add(new StringBuilder(row).reverse().toString());
        }
        String newBoard = String.join("/", newRows);
        StringBuilder result = new StringBuilder(newBoard);
        for (int i = 1; i < args.length; i++) {
            result.append(" ").append(args[i]);
        }
        return result.toString();
    }

    public static String mirrorFenRedBlack(String fen) {
        String[] args = fen.split(" ");
        String board = args[0];
        String[] rows = board.split("/");
        List<String> newRows = new ArrayList<>();
        for (int i = rows.length - 1; i >= 0; i--) {
            StringBuilder newRow = new StringBuilder();
            for (char c : rows[i].toCharArray()) {
                if (Character.isLowerCase(c)) {
                    newRow.append(Character.toUpperCase(c));
                } else if (Character.isUpperCase(c)) {
                    newRow.append(Character.toLowerCase(c));
                } else {
                    newRow.append(c);
                }
            }
            newRows.add(newRow.toString());
        }
        String newBoard = String.join("/", newRows);
        String nextPlayer = args[1].equals("b") ? "w" : "b";
        StringBuilder result = new StringBuilder(newBoard);
        result.append(" ").append(nextPlayer);
        for (int i = 2; i < args.length; i++) {
            result.append(" ").append(args[i]);
        }
        return result.toString();
    }

    public static boolean checkChessmanValid(String chess, int x, int y, boolean redSide) {
        String[] args = chess.split("_");
        String side = args[0];
        String type = args[1];
        if (!redSide) {
            y = 9 - y;
        }
        if (side.equals("r")) {
            if (type.equals("jiang")) {
                return (x >= 3 && x <= 5 && y >= 7 && y <= 9);
            } else if (type.equals("shi")) {
                return (x == 3 && y == 7 || x == 5 && y == 7 || x == 4 && y == 8 || x == 3 && y == 9 || x == 5 && y == 9);
            } else if (type.equals("xiang")) {
                return (x == 2 && y == 9 || x == 6 && y == 9 ||
                        x == 0 && y == 7 || x == 4 && y == 7 || x == 8 && y == 7 ||
                        x == 2 && y == 5 || x == 6 && y == 5);
            } else if (type.equals("bing")) {
                if ((y == 5 || y == 6) && (x == 0 || x == 2 || x == 4 || x == 6 || x == 8)) {
                    return true;
                } else return y <= 4;
            } else {
                return true;
            }
        } else {
            if (type.equals("jiang")) {
                return (x >= 3 && x <= 5 && y >= 0 && y <= 2);
            } else if (type.equals("shi")) {
                return (x == 3 && y == 0 || x == 5 && y == 0 || x == 4 && y == 1 || x == 3 && y == 2 || x == 5 && y == 2);
            } else if (type.equals("xiang")) {
                return (x == 2 && y == 0 || x == 6 && y == 0 ||
                        x == 0 && y == 2 || x == 4 && y == 2 || x == 8 && y == 2 ||
                        x == 2 && y == 4 || x == 6 && y == 4);
            } else if (type.equals("bing")) {
                if ((y == 3 || y == 4) && (x == 0 || x == 2 || x == 4 || x == 6 || x == 8)) {
                    return true;
                } else return y >= 5;
            } else {
                return true;
            }
        }
    }

    public static boolean checkBoardValid(String[][] board, boolean redSide) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> maxCounts = new HashMap<>();
        maxCounts.put("che", 2); maxCounts.put("ma", 2); maxCounts.put("pao", 2);
        maxCounts.put("xiang", 2); maxCounts.put("shi", 2); maxCounts.put("jiang", 1);
        maxCounts.put("bing", 5);

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (board[x][y] != null && !board[x][y].isEmpty()) {
                    String chessMan = board[x][y];
                    counts.put(chessMan, counts.getOrDefault(chessMan, 0) + 1);
                    if (!checkChessmanValid(chessMan, x, y, redSide)) {
                        return false;
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> c : counts.entrySet()) {
            String type = c.getKey().split("_")[1];
            if (c.getValue() > maxCounts.get(type)) {
                return false;
            }
        }
        return counts.containsKey("b_jiang") && counts.containsKey("r_jiang");
    }

    public static Point move2Point(String move, boolean redSide) {
        int x = move.charAt(0) - 'a';
        int y = move.charAt(1) - '0';
        if (redSide) {
            return new Point(x, 9 - y);
        } else {
            return new Point(8 - x, y);
        }
    }

    public static String point2Move(Point from, Point to) {
        String letters = "abcdefghijklmnopqrstuvwxyz";
        return "" + letters.charAt(from.x) + (9 - from.y) + letters.charAt(to.x) + (9 - to.y);
    }

    public static Rect expendArea(Rect area, Size maxSize) {
        float gridWidth = (float) area.width() / 8;
        float gridHeight = (float) area.height() / 9;
        Rect newArea = new Rect(
                (int) (area.left - gridWidth),
                (int) (area.top - gridHeight),
                (int) (area.right + gridWidth),
                (int) (area.bottom + gridHeight)
        );
        if (newArea.left < 0) newArea.left = 0;
        if (newArea.right > maxSize.getWidth()) newArea.right = maxSize.getWidth();
        if (newArea.top < 0) newArea.top = 0;
        if (newArea.bottom > maxSize.getHeight()) newArea.bottom = maxSize.getHeight();
        return newArea;
    }

    public static Rect restoreArea(Rect cropArea, Rect boardArea) {
        return new Rect(
                cropArea.left + boardArea.left,
                cropArea.top + boardArea.top,
                cropArea.left + boardArea.left + boardArea.width(),
                cropArea.top + boardArea.top + boardArea.height()
        );
    }
}
