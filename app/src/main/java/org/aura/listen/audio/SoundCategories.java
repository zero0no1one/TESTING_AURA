package org.aura.listen.audio;

/** Fixed grouping for the pinned YAMNet 521-label vocabulary, not source separation. */
public final class SoundCategories {
    public static final String[] KEYS = {"vocals", "vehicles", "air", "sirens"};
    private static final int[][] GROUPS = {
        {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,65},
        {294,295,296,297,298,299,300,301,302,303,304,305,306,307,308,309,310,311,312,313,314,315,316,317,318,319,320,321,322,323,324,325,326,327,328,329,330,331,332,333,334,335,336,337,338,342,343,344,345,346,347},
        {277,278,279,406,407},
        {317,318,319,390,391}
    };
    private SoundCategories() {}
    public static float[] scores(float[] modelOutput) {
        if (modelOutput.length != 521) throw new IllegalArgumentException("Unexpected label vocabulary.");
        float[] result = new float[4];
        for (int group = 0; group < GROUPS.length; group++) {
            for (int index : GROUPS[group]) {
                float value = modelOutput[index];
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid model score.");
                result[group] = Math.max(result[group], Math.max(0, Math.min(1, value)));
            }
        }
        return result;
    }
}
