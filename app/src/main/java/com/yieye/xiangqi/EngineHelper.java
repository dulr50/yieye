// This file interfaces with Pikafish (GPLv3, https://github.com/official-pikafish/Pikafish).
// See THIRD_PARTY_NOTICES.md and LICENSE at the project root.
package com.yieye.xiangqi;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EngineHelper {
    private static final String TAG = "EngineHelper";
    
    static {
        System.loadLibrary("pikafish");
    }

    private Context context;

    public String engineType = "uci";
    public String engineName = "";
    public String engineAuthor = "";
    public String lastBestMove = "";
    public String lastPonderMove = "";

    // Handshake flags
    private volatile boolean isUciOk = false;
    private volatile boolean isReadyOk = false;

    public interface BestMoveCallback {
        void onBestMove(String sourceFen, String bestMove, String ponderMove);
    }

    public interface InfoCallback {
        void onInfo(String cmd, Map<String, String> infos);
    }

    public BestMoveCallback bestMoveEvent;
    public InfoCallback infoEvent;

    public List<String> optionList = new ArrayList<>();
    public Map<String, String> configs = new HashMap<>();
    
    private volatile String currentFen = "";
    
    public long lastOutputTime = 0;
    public String ignoreMove = "";
    public boolean initialized = false;

    public native void initJNI(String workDir);
    public native void sendCommandJNI(String cmd);

    public EngineHelper(Context context, Map<String, String> configs) {
        this.context = context;
        if (configs != null) {
            this.configs = configs;
        }
    }

    public void stop() {
        sendCommand("quit");
    }

    public void init() {
        initialized = false;
        isUciOk = false;
        isReadyOk = false;
        currentFen = "";
        
        deployAssets(context);

        String workDir = context.getFilesDir().getAbsolutePath();

        Thread engineThread = new Thread(() -> {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                initJNI(workDir);

                int waitCount = 0;
                while (!isUciOk && waitCount < 50) {
                    Thread.sleep(100);
                    waitCount++;
                }

                if (!isUciOk) {
                    LogUtil.e(TAG, context.getString(R.string.engine_load_failed) + " (uciok timeout)");
                }

                File debugLogFile = new File(context.getFilesDir(), "engine_debug.log");
                if (debugLogFile.exists()) debugLogFile.delete();
                setOption("Debug Log File", debugLogFile.getAbsolutePath());

                File nnueFile = new File(context.getFilesDir(), "pikafish.nnue");
                if (nnueFile.exists()) {
                    setOption("EvalFile", nnueFile.getAbsolutePath());
                }

                // 功耗优化：限制线程数为 1 或 2，手机上 1 个核心通常就足够强了
                setOption("Threads", "1");
                // 功耗优化：减慢移动速度，降低 CPU 活跃度
                setOption("Slow Mover", "50");

                for (Map.Entry<String, String> option : configs.entrySet()) {
                    setOption(option.getKey(), option.getValue());
                }

                sendCommand("isready");
                waitCount = 0;
                while (!isReadyOk && waitCount < 50) {
                    Thread.sleep(100);
                    waitCount++;
                }

                initialized = true;
                LogUtil.d(TAG, "Engine fully initialized and ready.");

            } catch (Exception e) {
                LogUtil.e(TAG, context.getString(R.string.engine_load_failed) + e.getMessage(), e);
            }
        });
        engineThread.start();
    }

    private void deployAssets(Context context) {
        copyAssetAndRename(context, "pikafish.nnue", "pikafish.nnue");
    }

    private void copyAssetAndRename(Context context, String assetName, String targetName) {
        File targetFile = new File(context.getFilesDir(), targetName);
        try {
            InputStream is = context.getAssets().open(assetName);
            long assetSize = is.available();
            if (targetFile.exists() && targetFile.length() == assetSize) {
                is.close();
                return;
            }
            if (targetFile.exists()) targetFile.delete();
            FileOutputStream os = new FileOutputStream(targetFile);
            byte[] buffer = new byte[1024 * 64]; 
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {
                os.write(buffer, 0, byteCount);
            }
            os.close();
            is.close();
        } catch (IOException e) {
            LogUtil.e(TAG, "Failed to copy asset: " + assetName, e);
        }
    }

    // This method is called from C++
    public void onEngineOutput(String line) {
        if (line == null || line.trim().isEmpty()) return;
        lastOutputTime = System.currentTimeMillis();
        handleOutputLine(line);
    }

    public void handleOutputLine(String line) {
//        LogUtil.d(TAG, "Engine Output: " + line);
//        if (line.contains("\n")) {
//            for (String l : line.split("\n")) {
//                if (!l.trim().isEmpty()) handleOutputLine(l);
//            }
//            return;
//        }
        String[] args = line.trim().split("\\s+");
        if (args.length == 0) return;
        String cmd = args[0];

        if (cmd.equals("uciok")) {
            isUciOk = true;
        } else if (cmd.equals("readyok")) {
            isReadyOk = true;
        } else if (cmd.equals("info")) {
            // ... (keep existing info logic)
            Map<String, String> infos = new HashMap<>();
            List<String> infoTypes = Arrays.asList("depth", "seldepth", "time", "nodes", "pv", "multipv", "score", "currmove", "currmovenumber", "hashfull", "nps", "tbhits", "cpuload", "string", "refutation", "currline");
            for (int i = 1; i < args.length; i++) {
                if (args[i].equals("pv")) {
                    StringBuilder pv = new StringBuilder();
                    for (int j = i + 1; j < args.length; j++) {
                        pv.append(args[j]).append(" ");
                    }
                    infos.put(args[i], pv.toString().trim());
                    break;
                } else if (args[i].equals("string")) {
                    StringBuilder str = new StringBuilder();
                    for (int j = i + 1; j < args.length; j++) {
                        str.append(args[j]).append(" ");
                    }
                    infos.put(args[i], str.toString().trim());
                    break;
                } else if (args[i].equals("score")) {
                    if (i + 2 < args.length && args[i + 1].equals("cp")) {
                        infos.put(args[i], args[i + 2]);
                        i += 2;
                    } else if (i + 2 < args.length && args[i + 1].equals("mate")) {
                        // 额外保留原始的绝杀步数(可能为负数)，方便调用方做数值判断，不用去解析本地化后的字符串
                        infos.put("mate", args[i + 2]);
                        infos.put(args[i], context.getString(R.string.mate_in, args[i + 2]));
                        i += 2;
                    } else if (i + 1 < args.length) {
                        infos.put(args[i], args[i + 1]);
                        i++;
                    }
                } else {
                    if (args.length > i + 1 && !infoTypes.contains(args[i + 1])) {
                        infos.put(args[i], args[i + 1]);
                        i++;
                    } else {
                        infos.put(args[i], "");
                    }
                }
            }
            if (infoEvent != null) infoEvent.onInfo(cmd, infos);
        } else if (cmd.equals("bestmove")) {
            String sourceFen = this.currentFen;
            String bestMove = args.length > 1 ? args[1] : "";
            String ponderMove = "";
            if (args.length > 3 && args[2].equals("ponder")) {
                ponderMove = args[3];
            }
            LogUtil.d(TAG, "Processing bestmove: " + bestMove + " sourceFen: " + sourceFen);
            if (sourceFen != null && !sourceFen.isEmpty() && bestMoveEvent != null) {
                bestMoveEvent.onBestMove(sourceFen, bestMove, ponderMove);
            } else {
                LogUtil.w(TAG, "Skipping bestMoveEvent: sourceFen is " + (sourceFen == null ? "null" : "empty") + " or callback is null");
            }
            this.currentFen = "";
        } else if (cmd.equals("option")) {
            optionList.add(line);
        } else if (cmd.equals("id")) {
            if (args.length >= 3) {
                String type = args[1].toLowerCase();
                if (type.equals("name")) {
                    engineName = line.substring(line.indexOf("name") + 5).trim();
                } else if (type.equals("author")) {
                    engineAuthor = line.substring(line.indexOf("author") + 7).trim();
                }
            }
        }
    }

    public void setOption(String key, String value) {
        if (value != null && !value.isEmpty()) {
            sendCommand("setoption name " + key + " value " + value);
        } else {
            sendCommand("setoption name " + key);
        }
    }

    public void stopAnalyze() {
        this.currentFen = "";
        sendCommand("stop");
    }

    public void sendCommand(String cmd) {
        sendCommandJNI(cmd);
    }

    /**
     * @return 局面合法并已开始分析返回 true；非法局面被跳过返回 false。
     */
    public boolean startAnalyze(String fen, double timeSec, int depth) {
        // 防止非法局面导致 Pikafish 引擎原生层崩溃 (SIGSEGV in NNUE evaluate)
        if (!isValidFen(fen)) {
            LogUtil.w("EngineHelper", "非法 FEN，跳过分析: " + fen);
            return false;
        }
//        sendCommand("stop");
        this.currentFen = fen;
        sendCommand("position fen " + fen);
        // 对齐 Windows EngineHelper.StartAnalyze：depth 与 movetime 双重限制，谁先到谁生效，
        // 避免简单局面也死等固定深度、复杂局面又跑到超时才被强制打断
        if (timeSec > 0) {
            sendCommand("go movetime " + (int) (timeSec * 1000) + " depth " + depth);
        } else {
            sendCommand("go depth " + depth);
        }
        return true;
    }

    /**
     * 校验 FEN 的棋盘部分是否为合法象棋局面：
     * 1. 必须有 10 行，每行列数之和为 9；
     * 2. 每种棋子数量不得超过合法上限（将1/士2/象2/马2/车2/炮2/兵5），双方将必须各有一个。
     * 识别错误时可能产生多余棋子，Pikafish 原生层会写溢出内部数组导致 evaluate 崩溃，
     * 因此这里严格校验，非法局面直接跳过。
     */
    private boolean isValidFen(String fen) {
        if (fen == null || fen.isEmpty()) return false;
        String boardPart = fen.split(" ")[0];
        String[] rows = boardPart.split("/");
        if (rows.length != 10) return false;

        // 各棋子计数（小写=黑，大写=红）
        java.util.Map<Character, Integer> count = new java.util.HashMap<>();
        for (int y = 0; y < 10; y++) {
            String row = rows[y];
            int x = 0;
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (Character.isDigit(c)) {
                    x += c - '0';
                } else {
                    count.merge(c, 1, Integer::sum);
                    x++;
                }
            }
            if (x != 9) return false;
        }

        // 双方将各一个
        if (cnt(count, 'K') != 1 || cnt(count, 'k') != 1) return false;
        // 各兵种数量上限
        if (cnt(count, 'A') > 2 || cnt(count, 'a') > 2) return false; // 士
        if (cnt(count, 'B') > 2 || cnt(count, 'b') > 2) return false; // 象
        if (cnt(count, 'N') > 2 || cnt(count, 'n') > 2) return false; // 马
        if (cnt(count, 'R') > 2 || cnt(count, 'r') > 2) return false; // 车
        if (cnt(count, 'C') > 2 || cnt(count, 'c') > 2) return false; // 炮
        if (cnt(count, 'P') > 5 || cnt(count, 'p') > 5) return false; // 兵
        return true;
    }

    private int cnt(java.util.Map<Character, Integer> m, char c) {
        Integer v = m.get(c);
        return v == null ? 0 : v;
    }
}
