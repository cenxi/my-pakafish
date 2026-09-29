package com.pakafish.ai.service;

import com.pakafish.ai.model.BookMove;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * 中国象棋标准开局库服务 (解析 XQWizard/ElephantEye 标准 BOOK.DAT)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpeningBookService {

    private final ChessCoordinateConverter coordinateConverter;

    private static class BookEntry {
        int lock;
        int mv;
        int weight;

        BookEntry(int lock, int mv, int weight) {
            this.lock = lock;
            this.mv = mv;
            this.weight = weight;
        }
    }

    private static class RC4 {
        int[] s = new int[256];
        int x = 0, y = 0;

        RC4() {
            int[] key = new int[4];
            for (int i = 0; i < 256; i++) s[i] = i;
            int j = 0;
            for (int i = 0; i < 256; i++) {
                j = (j + s[i] + key[i % 4]) & 255;
                int tmp = s[i]; s[i] = s[j]; s[j] = tmp;
            }
        }

        int nextByte() {
            x = (x + 1) & 255;
            y = (y + s[x]) & 255;
            int tmp = s[x]; s[x] = s[y]; s[y] = tmp;
            return s[(s[x] + s[y]) & 255] & 0xFF;
        }

        int nextLong() {
            int b0 = nextByte();
            int b1 = nextByte();
            int b2 = nextByte();
            int b3 = nextByte();
            return (b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);
        }
    }

    private static final int[][] ZOBR_LOCK1 = new int[14][256];
    private static final int PLAYER_LOCK1;

    static {
        RC4 rc4 = new RC4();
        rc4.nextLong(); // key
        rc4.nextLong(); // lock0
        PLAYER_LOCK1 = rc4.nextLong(); // lock1

        for (int i = 0; i < 14; i++) {
            for (int j = 0; j < 256; j++) {
                rc4.nextLong(); // key
                rc4.nextLong(); // lock0
                ZOBR_LOCK1[i][j] = rc4.nextLong(); // lock1
            }
        }
    }

    // 已排好序的条目列表
    private BookEntry[] entries = new BookEntry[0];
    private final Random random = new Random();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("book/BOOK.DAT");
            if (!resource.exists()) {
                log.warn("未找到开局库文件 book/BOOK.DAT");
                return;
            }
            byte[] data;
            try (InputStream is = resource.getInputStream()) {
                data = is.readAllBytes();
            }

            int count = data.length / 8;
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            entries = new BookEntry[count];
            for (int i = 0; i < count; i++) {
                int lock = bb.getInt();
                int mv = bb.getShort() & 0xFFFF;
                int weight = bb.getShort() & 0xFFFF;
                entries[i] = new BookEntry(lock, mv, weight);
            }
            // 确保条目严格按 unsigned lock 升序排列，彻底避免 BOOK.DAT 格式差异导致的二分查找遗漏
            Arrays.sort(entries, (a, b) -> Integer.compareUnsigned(a.lock, b.lock));
            log.info("成功加载中国象棋开局库 BOOK.DAT，共 {} 条着法", count);
        } catch (Exception e) {
            log.error("加载开局库 BOOK.DAT 失败", e);
        }
    }

    public boolean hasBook() {
        return entries != null && entries.length > 0;
    }

    /**
     * 查询指定 FEN 局面下的所有开局库推荐走法
     *
     * @param fen 当前局面 FEN
     * @return 开局走法列表 (按权重降序排序)
     */
    public List<BookMove> getBookMoves(String fen) {
        if (!hasBook() || fen == null || fen.isBlank()) {
            return Collections.emptyList();
        }

        // 1. 先尝试原局面
        int directLock = computeLock1(fen, false);
        List<BookMove> moves = findMovesByLock(fen, directLock, false);

        // 2. 如果原局面没有，尝试左右镜像局面
        if (moves.isEmpty()) {
            int mirrorLock = computeLock1(fen, true);
            moves = findMovesByLock(fen, mirrorLock, true);
        }

        // 按权重从高到低排序
        moves.sort((a, b) -> Integer.compare(b.getWeight(), a.getWeight()));
        return moves;
    }

    /**
     * 从候选着法中按权重随机挑选一步走法 (权重越高选中概率越大)
     */
    public BookMove pickMoveWeighted(List<BookMove> moves) {
        if (moves == null || moves.isEmpty()) {
            return null;
        }
        if (moves.size() == 1) {
            return moves.get(0);
        }
        int totalWeight = moves.stream().mapToInt(BookMove::getWeight).sum();
        if (totalWeight <= 0) {
            return moves.get(0);
        }
        int r = random.nextInt(totalWeight);
        int acc = 0;
        for (BookMove m : moves) {
            acc += m.getWeight();
            if (r < acc) {
                return m;
            }
        }
        return moves.get(0);
    }

    private List<BookMove> findMovesByLock(String fen, int targetLock, boolean mirrored) {
        int idx = binarySearch(targetLock);
        if (idx < 0) {
            return Collections.emptyList();
        }

        // 往前扫描同 lock 的起始点
        int start = idx;
        while (start > 0 && entries[start - 1].lock == targetLock) {
            start--;
        }

        // 往后扫描所有匹配的记录
        List<BookMove> res = new ArrayList<>();
        while (start < entries.length && entries[start].lock == targetLock) {
            BookEntry e = entries[start];
            String uci = convertEntryMoveToUci(e.mv, mirrored);
            if (uci != null) {
                String chinese = coordinateConverter.uciToChinese(fen, uci);
                res.add(BookMove.builder()
                        .uci(uci)
                        .chinese(chinese)
                        .weight(e.weight)
                        .build());
            }
            start++;
        }
        return res;
    }

    private int binarySearch(int lock) {
        int low = 0;
        int high = entries.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midLock = entries[mid].lock;
            int cmp = Integer.compareUnsigned(midLock, lock);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    private String convertEntryMoveToUci(int mv, boolean mirrored) {
        int src = mv & 0xFF;
        int dst = (mv >> 8) & 0xFF;
        int srcX = src & 15;
        int srcY = src >> 4;
        int dstX = dst & 15;
        int dstY = dst >> 4;

        int c1 = srcX - 3;
        int c2 = dstX - 3;
        if (mirrored) {
            c1 = 8 - c1;
            c2 = 8 - c2;
        }

        if (c1 < 0 || c1 > 8 || c2 < 0 || c2 > 8) {
            return null;
        }

        int r1 = 9 - (srcY - 3);
        int r2 = 9 - (dstY - 3);
        if (r1 < 0 || r1 > 9 || r2 < 0 || r2 > 9) {
            return null;
        }

        char chC1 = (char) ('a' + c1);
        char chR1 = (char) ('0' + r1);
        char chC2 = (char) ('a' + c2);
        char chR2 = (char) ('0' + r2);

        return "" + chC1 + chR1 + chC2 + chR2;
    }

    public static int computeLock1(String fen, boolean mirror) {
        String[] parts = fen.trim().split("\\s+");
        String boardPart = parts[0];
        boolean isBlack = parts.length > 1 && parts[1].equals("b");

        String[] rows = boardPart.split("/");
        int lock1 = 0;

        for (int r = 0; r < 10; r++) {
            String row = rows[r];
            int c = 0;
            for (int i = 0; i < row.length(); i++) {
                char ch = row.charAt(i);
                if (ch >= '1' && ch <= '9') {
                    c += (ch - '0');
                } else {
                    int col = mirror ? (8 - c) : c;
                    int sq = (col + 3) + ((r + 3) << 4);
                    int pt;
                    switch (Character.toUpperCase(ch)) {
                        case 'K': pt = 0; break;
                        case 'A': pt = 1; break;
                        case 'B': case 'E': pt = 2; break;
                        case 'N': case 'H': pt = 3; break;
                        case 'R': pt = 4; break;
                        case 'C': pt = 5; break;
                        case 'P': pt = 6; break;
                        default: pt = -1;
                    }
                    if (pt >= 0) {
                        if (Character.isLowerCase(ch)) {
                            pt += 7; // black
                        }
                        lock1 ^= ZOBR_LOCK1[pt][sq];
                    }
                    c++;
                }
            }
        }

        if (isBlack) {
            lock1 ^= PLAYER_LOCK1;
        }

        return lock1;
    }
}
