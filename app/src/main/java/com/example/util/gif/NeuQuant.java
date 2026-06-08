package com.example.util.gif;

/*
 * NeuQuant Neural-Net Quantization Algorithm
 * ------------------------------------------
 * Copyright (c) 1994 Anthony Dekker
 * See "Kohonen neural networks for optimal colour quantization" in
 * "Network: Computation in Neural Systems" Vol. 5 (1994) pp 351-367.
 * Any party obtaining a copy is granted permission to use, copy, modify and
 * distribute this software and documentation without fee.
 * Java port by Kevin Weiner.
 */
public class NeuQuant {

    private static final int netsize = 256; // number of colours used

    private static final int prime1 = 499;
    private static final int prime2 = 491;
    private static final int prime3 = 487;
    private static final int prime4 = 503;
    private static final int minpicturebytes = (3 * prime4);

    private static final int maxnetpos = (netsize - 1);
    private static final int netbiasshift = 4;
    private static final int ncycles = 100;

    private static final int intbiasshift = 16;
    private static final int intbias = (1 << intbiasshift);
    private static final int gammashift = 10;
    private static final int betashift = 10;
    private static final int beta = (intbias >> betashift);
    private static final int betagamma = (intbias << (gammashift - betashift));

    private static final int initrad = (netsize >> 3);
    private static final int radiusbiasshift = 6;
    private static final int radiusbias = (1 << radiusbiasshift);
    private static final int initradius = (initrad * radiusbias);
    private static final int radiusdec = 30;

    private static final int alphabiasshift = 10;
    private static final int initalpha = (1 << alphabiasshift);
    private int alphadec;

    private static final int radbiasshift = 8;
    private static final int radbias = (1 << radbiasshift);
    private static final int alpharadbshift = (alphabiasshift + radbiasshift);
    private static final int alpharadbias = (1 << alpharadbshift);

    private byte[] thepicture;
    private int lengthcount;
    private int samplefac;

    private int[][] network;
    private int[] netindex = new int[256];
    private int[] bias = new int[netsize];
    private int[] freq = new int[netsize];
    private int[] radpower = new int[initrad];

    public NeuQuant(byte[] thepic, int len, int sample) {
        thepicture = thepic;
        lengthcount = len;
        samplefac = sample;

        network = new int[netsize][];
        for (int i = 0; i < netsize; i++) {
            network[i] = new int[4];
            int[] p = network[i];
            p[0] = p[1] = p[2] = (i << (netbiasshift + 8)) / netsize;
            freq[i] = intbias / netsize;
            bias[i] = 0;
        }
    }

    private byte[] colorMap() {
        byte[] map = new byte[3 * netsize];
        int[] index = new int[netsize];
        for (int i = 0; i < netsize; i++) {
            index[network[i][3]] = i;
        }
        int k = 0;
        for (int i = 0; i < netsize; i++) {
            int j = index[i];
            map[k++] = (byte) (network[j][0]);
            map[k++] = (byte) (network[j][1]);
            map[k++] = (byte) (network[j][2]);
        }
        return map;
    }

    private void inxbuild() {
        int previouscol = 0;
        int startpos = 0;
        for (int i = 0; i < netsize; i++) {
            int[] p = network[i];
            int smallpos = i;
            int smallval = p[1];
            int[] q;
            for (int j = i + 1; j < netsize; j++) {
                q = network[j];
                if (q[1] < smallval) {
                    smallpos = j;
                    smallval = q[1];
                }
            }
            q = network[smallpos];
            if (i != smallpos) {
                int j = q[0]; q[0] = p[0]; p[0] = j;
                j = q[1]; q[1] = p[1]; p[1] = j;
                j = q[2]; q[2] = p[2]; p[2] = j;
                j = q[3]; q[3] = p[3]; p[3] = j;
            }
            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) >> 1;
                for (int j = previouscol + 1; j < smallval; j++) {
                    netindex[j] = i;
                }
                previouscol = smallval;
                startpos = i;
            }
        }
        netindex[previouscol] = (startpos + maxnetpos) >> 1;
        for (int j = previouscol + 1; j < 256; j++) {
            netindex[j] = maxnetpos;
        }
    }

    private void learn() {
        if (lengthcount < minpicturebytes) {
            samplefac = 1;
        }
        alphadec = 30 + ((samplefac - 1) / 3);
        byte[] p = thepicture;
        int pix = 0;
        int lim = lengthcount;
        int samplepixels = lengthcount / (3 * samplefac);
        int delta = samplepixels / ncycles;
        int alpha = initalpha;
        int radius = initradius;

        int rad = radius >> radiusbiasshift;
        if (rad <= 1) rad = 0;
        for (int i = 0; i < rad; i++) {
            radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad));
        }

        int step;
        if (lengthcount < minpicturebytes) {
            step = 3;
        } else if ((lengthcount % prime1) != 0) {
            step = 3 * prime1;
        } else if ((lengthcount % prime2) != 0) {
            step = 3 * prime2;
        } else if ((lengthcount % prime3) != 0) {
            step = 3 * prime3;
        } else {
            step = 3 * prime4;
        }

        int i = 0;
        while (i < samplepixels) {
            int b = (p[pix] & 0xff) << netbiasshift;
            int g = (p[pix + 1] & 0xff) << netbiasshift;
            int r = (p[pix + 2] & 0xff) << netbiasshift;
            int j = contest(b, g, r);

            altersingle(alpha, j, b, g, r);
            if (rad != 0) {
                alterneigh(rad, j, b, g, r);
            }

            pix += step;
            if (pix >= lim) {
                pix -= lengthcount;
            }

            i++;
            if (delta == 0) delta = 1;
            if (i % delta == 0) {
                alpha -= alpha / alphadec;
                radius -= radius / radiusdec;
                rad = radius >> radiusbiasshift;
                if (rad <= 1) rad = 0;
                for (int k = 0; k < rad; k++) {
                    radpower[k] = alpha * (((rad * rad - k * k) * radbias) / (rad * rad));
                }
            }
        }
    }

    public int map(int b, int g, int r) {
        int bestd = 1000;
        int best = -1;
        int i = netindex[g];
        int j = i - 1;

        while ((i < netsize) || (j >= 0)) {
            if (i < netsize) {
                int[] p = network[i];
                int dist = p[1] - g;
                if (dist >= bestd) {
                    i = netsize;
                } else {
                    i++;
                    if (dist < 0) dist = -dist;
                    int a = p[0] - b;
                    if (a < 0) a = -a;
                    dist += a;
                    if (dist < bestd) {
                        a = p[2] - r;
                        if (a < 0) a = -a;
                        dist += a;
                        if (dist < bestd) {
                            bestd = dist;
                            best = p[3];
                        }
                    }
                }
            }
            if (j >= 0) {
                int[] p = network[j];
                int dist = g - p[1];
                if (dist >= bestd) {
                    j = -1;
                } else {
                    j--;
                    if (dist < 0) dist = -dist;
                    int a = p[0] - b;
                    if (a < 0) a = -a;
                    dist += a;
                    if (dist < bestd) {
                        a = p[2] - r;
                        if (a < 0) a = -a;
                        dist += a;
                        if (dist < bestd) {
                            bestd = dist;
                            best = p[3];
                        }
                    }
                }
            }
        }
        return best;
    }

    public byte[] process() {
        learn();
        unbiasnet();
        inxbuild();
        return colorMap();
    }

    private void unbiasnet() {
        for (int i = 0; i < netsize; i++) {
            network[i][0] >>= netbiasshift;
            network[i][1] >>= netbiasshift;
            network[i][2] >>= netbiasshift;
            network[i][3] = i;
        }
    }

    private void alterneigh(int rad, int i, int b, int g, int r) {
        int lo = i - rad;
        if (lo < -1) lo = -1;
        int hi = i + rad;
        if (hi > netsize) hi = netsize;

        int j = i + 1;
        int k = i - 1;
        int m = 1;
        while ((j < hi) || (k > lo)) {
            int a = radpower[m++];
            if (j < hi) {
                int[] p = network[j++];
                try {
                    p[0] -= (a * (p[0] - b)) / alpharadbias;
                    p[1] -= (a * (p[1] - g)) / alpharadbias;
                    p[2] -= (a * (p[2] - r)) / alpharadbias;
                } catch (Exception e) { /* ignore */ }
            }
            if (k > lo) {
                int[] p = network[k--];
                try {
                    p[0] -= (a * (p[0] - b)) / alpharadbias;
                    p[1] -= (a * (p[1] - g)) / alpharadbias;
                    p[2] -= (a * (p[2] - r)) / alpharadbias;
                } catch (Exception e) { /* ignore */ }
            }
        }
    }

    private void altersingle(int alpha, int i, int b, int g, int r) {
        int[] n = network[i];
        n[0] -= (alpha * (n[0] - b)) / initalpha;
        n[1] -= (alpha * (n[1] - g)) / initalpha;
        n[2] -= (alpha * (n[2] - r)) / initalpha;
    }

    private int contest(int b, int g, int r) {
        int bestd = ~(1 << 31);
        int bestbiasd = bestd;
        int bestpos = -1;
        int bestbiaspos = bestpos;

        for (int i = 0; i < netsize; i++) {
            int[] n = network[i];
            int dist = n[0] - b;
            if (dist < 0) dist = -dist;
            int a = n[1] - g;
            if (a < 0) a = -a;
            dist += a;
            a = n[2] - r;
            if (a < 0) a = -a;
            dist += a;
            if (dist < bestd) {
                bestd = dist;
                bestpos = i;
            }
            int biasdist = dist - ((bias[i]) >> (intbiasshift - netbiasshift));
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist;
                bestbiaspos = i;
            }
            int betafreq = (freq[i] >> betashift);
            freq[i] -= betafreq;
            bias[i] += (betafreq << gammashift);
        }
        freq[bestpos] += beta;
        bias[bestpos] -= betagamma;
        return bestbiaspos;
    }
}
