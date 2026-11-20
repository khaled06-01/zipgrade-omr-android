package com.zipgrade.omrscanner;

import java.util.Arrays;

/**
 * Geometry for the teacher-generated A4 form, in the scanner's 600 x 780 canonical
 * coordinates. Question numbering starts at one. Column, option and digit indices start at zero.
 * Code cells are black or white square centers surrounded by white quiet space.
 */
public final class CustomSheetLayout {
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 100;
    public static final int ROWS_PER_COLUMN = 25;
    public static final float MARKER_HALF_SIZE = 7.5f;
    public static final float ANSWER_RADIUS = 5.3f;
    public static final float ID_RADIUS = 4.6f;
    public static final int CELL_COUNT = 33;
    public static final float CELL_SIZE = 8f;

    private static final String PREFIX = "CUSTOM_V1_";
    private static final int VERSION = 1;
    private static final int LEFT_GUARD = 0xd3;
    private static final int RIGHT_GUARD = 0x59;

    private CustomSheetLayout() {}

    public static boolean validCount(int n) {
        return n >= MIN_COUNT && n <= MAX_COUNT;
    }

    public static String code(int n) {
        requireCount(n);
        return PREFIX + n;
    }

    /** Returns 0 for a different family, invalid range or noncanonical spelling. */
    public static int parseCode(String code) {
        if (code == null || !code.startsWith(PREFIX)) return 0;
        int digits = code.length() - PREFIX.length();
        if (digits < 1 || digits > 3 || code.charAt(PREFIX.length()) == '0') return 0;
        int n = 0;
        for (int i = PREFIX.length(); i < code.length(); i++) {
            char c = code.charAt(i);
            if (c < '0' || c > '9') return 0;
            n = 10 * n + c - '0';
        }
        return validCount(n) ? n : 0;
    }

    private static void requireCount(int n) {
        if (!validCount(n)) throw new IllegalArgumentException("Question count must be 1 to 100");
    }

    private static void requireIndex(int index, int length) {
        if (index < 0 || index >= length) throw new IllegalArgumentException("Index outside sheet layout");
    }

    public static float answerX(int count, int question, int option) {
        requireCount(count);
        requireIndex(question - 1, count);
        requireIndex(option, 4);
        return 110 + 110 * ((question - 1) / ROWS_PER_COLUMN) + 18 * option;
    }

    public static float answerY(int count, int question) {
        requireCount(count);
        requireIndex(question - 1, count);
        return 275 + 17 * ((question - 1) % ROWS_PER_COLUMN);
    }

    public static float idX(int col) {
        requireIndex(col, 7);
        return 323 + 22 * col;
    }

    public static float idY(int digit) {
        requireIndex(digit, 10);
        return 101 + 14 * digit;
    }

    /** TL, TR, ML, MR, BL, BR, the order used by the form renderer. */
    public static float fiducialX(int index) {
        requireIndex(index, 6);
        return (index & 1) == 0 ? 64 : 536;
    }

    public static float fiducialY(int index) {
        requireIndex(index, 6);
        return 64 + 326 * (index / 2);
    }

    public static float cellX(int index) {
        requireIndex(index, CELL_COUNT);
        return 108 + 12 * index;
    }

    public static float cellY(int index) {
        requireIndex(index, CELL_COUNT);
        return 64;
    }

    /** CRC-8/ATM, initial value 0, over the two bytes (version, question count). */
    private static int checksum(int version, int count) {
        int crc = 0;
        for (int byteIndex = 0; byteIndex < 2; byteIndex++) {
            crc ^= byteIndex == 0 ? version : count;
            for (int b = 0; b < 8; b++) {
                crc = ((crc << 1) ^ ((crc & 128) == 0 ? 0 : 0x07)) & 255;
            }
        }
        return crc;
    }

    /** Left to right, most significant bit first: 8 guard, 2 version, 7 count, 8 CRC, 8 guard. */
    public static boolean isDark(int count, int cell) {
        requireCount(count);
        requireIndex(cell, CELL_COUNT);
        long word = ((long) LEFT_GUARD << 25) | ((long) VERSION << 23) | ((long) count << 16)
                | (checksum(VERSION, count) << 8) | RIGHT_GUARD;
        return ((word >>> (32 - cell)) & 1L) == 1L;
    }

    private static boolean guardBit(int i) {
        int guard = i < 8 ? LEFT_GUARD : RIGHT_GUARD;
        return ((guard >>> (7 - i % 8)) & 1) == 1;
    }

    /**
     * Input is normalized darkness in [0,1] for each cell. A return value of 0 means the
     * guards were not recognized; -1 means a damaged/unsupported tag. Neither yields a count.
     * The strip has known ink and paper samples at both ends, so gray cells can be rejected
     * using the observed contrast rather than assigning each sample to its nearest bit.
     */
    public static int decode(float[] densities) {
        if (densities == null || densities.length != CELL_COUNT) return -1;
        float[] inks = new float[9];
        float[] papers = new float[7];
        int inkCount = 0, paperCount = 0;
        for (int i = 0; i < CELL_COUNT; i++) {
            float d = densities[i];
            if (!(d >= 0f && d <= 1f)) return -1;
            int guardIndex = i < 8 ? i : i >= 25 ? i - 17 : -1;
            if (guardIndex >= 0) {
                if (guardBit(guardIndex)) inks[inkCount++] = d;
                else papers[paperCount++] = d;
            }
        }
        Arrays.sort(inks, 0, inkCount);
        Arrays.sort(papers, 0, paperCount);
        float white = papers[paperCount / 2], black = inks[inkCount / 2];
        float contrast = black - white;
        if (contrast < 0.18f) return 0;
        float mid = white + 0.5f * contrast;
        int wrongGuard = 0;
        for (int i = 0; i < 16; i++) {
            int cell = i < 8 ? i : i + 17;
            if ((densities[cell] > mid) != guardBit(i)) wrongGuard++;
        }
        if (wrongGuard > 3) return 0;
        if (wrongGuard != 0) return -1;
        int data = 0;
        for (int i = 0; i < CELL_COUNT; i++) {
            float d = densities[i];
            if (d > white + 0.30f * contrast && d < white + 0.70f * contrast) return -1;
            if (i >= 8 && i < 25) data = (data << 1) | (d > mid ? 1 : 0);
        }
        int version = data >>> 15;
        int count = (data >>> 8) & 127;
        if (version != VERSION || !validCount(count) || checksum(version, count) != (data & 255)) return -1;
        return count;
    }
}
