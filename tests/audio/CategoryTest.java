import org.aura.listen.audio.AudioResampler;
import org.aura.listen.audio.SoundCategories;
public final class CategoryTest {
    static int checks;
    static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    public static void main(String[] args) {
        float[] scores = new float[521]; scores[0] = .8f; scores[317] = .7f; scores[277] = .6f;
        float[] groups = SoundCategories.scores(scores);
        check(groups[0] == .8f && groups[1] == .7f && groups[2] == .6f && groups[3] == .7f, "Groups or overlaps wrong");
        scores[317] = 0; scores[339] = 1; scores[0] = 0; scores[277] = 0;
        groups = SoundCategories.scores(scores);
        check(groups[1] == 0, "Dental drill incorrectly included as vehicle");
        scores[0] = Float.NaN;
        try { SoundCategories.scores(scores); throw new AssertionError("NaN accepted"); } catch (IllegalArgumentException expected) { checks++; }
        try { SoundCategories.scores(new float[520]); throw new AssertionError("Wrong vocabulary accepted"); } catch (IllegalArgumentException expected) { checks++; }
        for (int rate : new int[]{16000, 48000}) {
            short[] pcm = new short[15600 * (rate / 16000)];
            for (int i=0; i<pcm.length; i++) pcm[i]=(short)(16000*Math.sin(2*Math.PI*1000*i/rate));
            float[] result = new float[15600]; AudioResampler.to16k(pcm,rate,result);
            check(rms(result) > .34 && rms(result) < .35, "Speech band was attenuated during resampling");
            if (rate == 16000) for (int i=0; i<pcm.length; i++) check(result[i] == pcm[i]/32768f, "16 kHz input changed");
        }
        short[] high = new short[46800];
        for (int i=0; i<high.length; i++) high[i]=(short)(16000*Math.sin(2*Math.PI*12000*i/48000));
        float[] result = new float[15600]; AudioResampler.to16k(high,48000,result);
        check(rms(result)<.001, "Out-of-band alias not rejected");
        try { AudioResampler.to16k(high,44100,result); throw new AssertionError("Unsupported rate accepted"); } catch (IllegalArgumentException expected) { checks++; }
        System.out.println("PASS: " + checks + " resampler/category assertions");
    }
    static double rms(float[] samples) { double sum=0; for(int i=100;i<samples.length-100;i++)sum+=samples[i]*samples[i];return Math.sqrt(sum/(samples.length-200)); }
}
