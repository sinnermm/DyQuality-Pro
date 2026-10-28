package dev.operit.dyquality;

import java.util.ArrayList;
import java.util.List;

public class QualityPolicyTest {

    public static void main(String[] args) {
        testOriginSelection();
        test1KSelection();
        test2KSelection();
        testFallbackUnderTarget();
        testEmptyAndInvalid();
        System.out.println("ALL QUALITY POLICY TESTS PASSED!");
    }

    private static void testOriginSelection() {
        List<QualityPolicy.Candidate> list = new ArrayList<QualityPolicy.Candidate>();
        list.add(new QualityPolicy.Candidate(0, 720, 1280, 1000000, 0, 30, null));
        list.add(new QualityPolicy.Candidate(1, 1080, 1920, 3000000, 1, 30, null));
        list.add(new QualityPolicy.Candidate(2, 1440, 2560, 6000000, 1, 60, null));
        list.add(new QualityPolicy.Candidate(3, 1440, 2560, 8000000, 2, 60, null)); // 原画最高

        int selected = QualityPolicy.choose(list, QualityPolicy.MODE_ORIGIN);
        if (selected != 3) {
            throw new AssertionError("Origin should pick index 3 (highest bitrate 1440P), got: " + selected);
        }
    }

    private static void test1KSelection() {
        List<QualityPolicy.Candidate> list = new ArrayList<QualityPolicy.Candidate>();
        list.add(new QualityPolicy.Candidate(0, 720, 1280, 1000000, 0, 30, null));
        list.add(new QualityPolicy.Candidate(1, 1080, 1920, 2500000, 1, 30, null)); // 1080P 低码率
        list.add(new QualityPolicy.Candidate(2, 1080, 1920, 4500000, 2, 30, null)); // 1080P 高码率 (应选)
        list.add(new QualityPolicy.Candidate(3, 1440, 2560, 7000000, 2, 60, null)); // 2K 超限

        int selected = QualityPolicy.choose(list, QualityPolicy.MODE_1K);
        if (selected != 2) {
            throw new AssertionError("1K should pick index 2 (1080P highest bitrate), got: " + selected);
        }
    }

    private static void test2KSelection() {
        List<QualityPolicy.Candidate> list = new ArrayList<QualityPolicy.Candidate>();
        list.add(new QualityPolicy.Candidate(0, 1080, 1920, 3000000, 1, 30, null));
        list.add(new QualityPolicy.Candidate(1, 1440, 2560, 5000000, 1, 60, null));
        list.add(new QualityPolicy.Candidate(2, 1440, 2560, 7500000, 2, 60, null)); // 2K 高码率 (应选)
        list.add(new QualityPolicy.Candidate(3, 2160, 3840, 15000000, 2, 60, null)); // 4K 超限

        int selected = QualityPolicy.choose(list, QualityPolicy.MODE_2K);
        if (selected != 2) {
            throw new AssertionError("2K should pick index 2 (1440P highest bitrate), got: " + selected);
        }
    }

    private static void testFallbackUnderTarget() {
        // 当视频最高只有 720P 时，选 1K 或 2K 应自适应降级到 720P 最高码率，而不是返回 -1 或空
        List<QualityPolicy.Candidate> list = new ArrayList<QualityPolicy.Candidate>();
        list.add(new QualityPolicy.Candidate(0, 540, 960, 500000, 0, 30, null));
        list.add(new QualityPolicy.Candidate(1, 720, 1280, 1200000, 1, 30, null));
        list.add(new QualityPolicy.Candidate(2, 720, 1280, 1800000, 2, 30, null)); // 720P 最高码率

        int selected1K = QualityPolicy.choose(list, QualityPolicy.MODE_1K);
        if (selected1K != 2) {
            throw new AssertionError("1K fallback should pick index 2 (720P highest bitrate), got: " + selected1K);
        }

        int selected2K = QualityPolicy.choose(list, QualityPolicy.MODE_2K);
        if (selected2K != 2) {
            throw new AssertionError("2K fallback should pick index 2 (720P highest bitrate), got: " + selected2K);
        }
    }

    private static void testEmptyAndInvalid() {
        List<QualityPolicy.Candidate> list = new ArrayList<QualityPolicy.Candidate>();
        if (QualityPolicy.choose(list, QualityPolicy.MODE_1K) != -1) {
            throw new AssertionError("Empty list should return -1");
        }
        if (QualityPolicy.choose(null, QualityPolicy.MODE_ORIGIN) != -1) {
            throw new AssertionError("Null list should return -1");
        }
        if (QualityPolicy.choose(list, QualityPolicy.MODE_AUTO) != -1) {
            throw new AssertionError("Auto mode should return -1");
        }
    }
}
