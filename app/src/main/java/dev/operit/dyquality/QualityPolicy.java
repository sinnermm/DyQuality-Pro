package dev.operit.dyquality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 抖音清晰度选流策略核心算法
 * 
 * 核心设计目标：
 * 1. 追求高画质、高码率：同分辨率下严格优先选择最高码率，杜绝因降码率导致的画面模糊和细节丢失。
 * 2. 档位阶梯适配：
 *    - 1K 模式：优先匹配 1080P 最高码率档位；无 1080P 时降级匹配最高可用档。
 *    - 2K 模式：优先匹配 1440P 最高码率档位；无 2K 时降级匹配 1080P 或最高可用档。
 *    - 原画模式：直接锁定全候选集中分辨率最高、码率最高的顶格流。
 * 3. 播放流畅性保证（抗卡顿）：
 *    - 全面接纳 ByteVC1(H.265)、ByteVC2(H.266) 与 H.264，打通编码壁垒。
 *    - 异常参数过滤与保底回退（Fail-Open）。
 */
public final class QualityPolicy {

    public static final int MODE_AUTO = 0;
    public static final int MODE_1K = 1;     // 1080P 优先
    public static final int MODE_2K = 2;     // 1440P / 2K 优先
    public static final int MODE_ORIGIN = 3; // 平台最高画质 / 原画

    public static final String[] LABELS = {
        "自动（官方默认）",
        "1K 高清（1080P 顶格码率）",
        "2K 超清（1440P 顶格码率）",
        "原画品质（最高分辨率·满血码率）"
    };

    public static final class Candidate {
        public final int index;
        public final int width;
        public final int height;
        public final int bitrate;
        public final int codecType; // 0: H264, 1: ByteVC1, 2: ByteVC2
        public final int fps;
        public final Object rawObject;

        public Candidate(int index, int width, int height, int bitrate, int codecType, int fps, Object rawObject) {
            this.index = index;
            this.width = width;
            this.height = height;
            this.bitrate = bitrate;
            this.codecType = codecType;
            this.fps = fps;
            this.rawObject = rawObject;
        }

        public int shortSide() {
            return Math.min(width, height);
        }

        public long pixels() {
            return (long) width * height;
        }

        public boolean isValid() {
            return width > 0 && height > 0;
        }

        @Override
        public String toString() {
            return String.format("[%d] %dx%d @ %d bps, codec=%d, fps=%d",
                    index, width, height, bitrate, codecType, fps);
        }
    }

    /**
     * 根据当前清晰度模式选择最佳候选流的索引
     * 
     * @param candidates 视频候选流列表
     * @param mode 当前清晰度模式
     * @return 最佳候选流的原始 index，若返回 -1 表示保持官方默认
     */
    public static int choose(List<Candidate> candidates, int mode) {
        if (mode == MODE_AUTO || candidates == null || candidates.isEmpty()) {
            return -1;
        }

        List<Candidate> valids = new ArrayList<Candidate>();
        for (Candidate c : candidates) {
            if (c != null && c.isValid()) {
                valids.add(c);
            }
        }

        if (valids.isEmpty()) {
            return -1;
        }

        if (mode == MODE_ORIGIN) {
            return pickOrigin(valids);
        } else if (mode == MODE_2K) {
            return pickTarget(valids, 1440);
        } else if (mode == MODE_1K) {
            return pickTarget(valids, 1080);
        }

        return -1;
    }

    /**
     * 原画模式选择器：
     * 1. 短边最大优先（如 2160 > 1440 > 1080 > 720）
     * 2. 总像素数最大优先
     * 3. 码率最高优先（追求画质与细节）
     * 4. 相同分辨率与码率下，优先选择 ByteVC2/ByteVC1（编码压缩效率更佳）
     */
    private static int pickOrigin(List<Candidate> valids) {
        Candidate best = Collections.max(valids, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                if (a.shortSide() != b.shortSide()) {
                    return Integer.compare(a.shortSide(), b.shortSide());
                }
                if (a.pixels() != b.pixels()) {
                    return Long.compare(a.pixels(), b.pixels());
                }
                if (a.bitrate != b.bitrate) {
                    return Integer.compare(a.bitrate, b.bitrate);
                }
                return Integer.compare(a.codecType, b.codecType);
            }
        });
        return best.index;
    }

    /**
     * 目标清晰度（1K / 2K）选择器：
     * 核心逻辑：
     * 1. 优先寻找不超过 targetShort 的候选流；
     * 2. 在不超过 targetShort 的候选流中，挑选短边最大的（尽量逼近目标清晰度）；
     * 3. 在相同短边尺寸下，挑选码率最高（高码率画质最好）；
     * 4. 若视频所有候选流都低于目标（如老视频只有 720P），则选该视频能提供的最高档位（码率最高）；
     * 5. 若视频所有候选流都高于目标（罕见情况），则挑选最接近目标的最高码率流。
     */
    private static int pickTarget(List<Candidate> valids, final int targetShort) {
        List<Candidate> underOrEqual = new ArrayList<Candidate>();
        for (Candidate c : valids) {
            if (c.shortSide() <= targetShort) {
                underOrEqual.add(c);
            }
        }

        if (!underOrEqual.isEmpty()) {
            // 在 <= targetShort 的集合中，找短边最大的；短边相同时找码率最高的
            Candidate best = Collections.max(underOrEqual, new Comparator<Candidate>() {
                @Override
                public int compare(Candidate a, Candidate b) {
                    if (a.shortSide() != b.shortSide()) {
                        return Integer.compare(a.shortSide(), b.shortSide());
                    }
                    if (a.pixels() != b.pixels()) {
                        return Long.compare(a.pixels(), b.pixels());
                    }
                    if (a.bitrate != b.bitrate) {
                        return Integer.compare(a.bitrate, b.bitrate);
                    }
                    return Integer.compare(a.codecType, b.codecType);
                }
            });
            return best.index;
        }

        // 如果全部候选流都大于 targetShort（例如 1080P 模式下视频最低只有 1440P），挑选最接近 targetShort 的
        Candidate best = Collections.min(valids, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                if (a.shortSide() != b.shortSide()) {
                    return Integer.compare(a.shortSide(), b.shortSide());
                }
                // 相同超限分辨率下仍选码率较高者保证不糊
                return Integer.compare(b.bitrate, a.bitrate);
            }
        });
        return best.index;
    }
}
