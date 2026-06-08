package com.example.util.gif;

import android.graphics.Bitmap;

import java.io.InputStream;
import java.util.ArrayList;

/**
 * Decodes an (animated) GIF into individual {@link Bitmap} frames with per-frame delays.
 *
 * Android port of Kevin Weiner's GifDecoder (FilmstripGifDecoder). Public domain.
 * Only the bits needed for frame extraction are kept.
 */
public class GifDecoder {

    public static final int STATUS_OK = 0;
    public static final int STATUS_FORMAT_ERROR = 1;
    public static final int STATUS_OPEN_ERROR = 2;

    private InputStream in;
    private int status;

    private int width;     // full image width
    private int height;    // full image height
    private boolean gctFlag; // global color table used
    private int gctSize;   // size of global color table
    private int loopCount = 1;

    private int[] gct;     // global color table
    private int[] lct;     // local color table
    private int[] act;     // active color table

    private int bgIndex;   // background color index
    private int bgColor;   // background color
    private int lastBgColor;
    private int pixelAspect;

    private boolean lctFlag;
    private boolean interlace;
    private int lctSize;

    private int ix, iy, iw, ih; // current image rectangle
    private int lrx, lry, lrw, lrh;
    private Bitmap image;       // current frame
    private Bitmap lastBitmap;

    private byte[] block = new byte[256];
    private int blockSize = 0;

    private int dispose = 0;
    private int lastDispose = 0;
    private boolean transparency = false;
    private int delay = 0;
    private int transIndex;

    private static final int MaxStackSize = 4096;
    private short[] prefix;
    private byte[] suffix;
    private byte[] pixelStack;
    private byte[] pixels;

    private ArrayList<GifFrame> frames;
    private int frameCount;

    private static class GifFrame {
        public Bitmap image;
        public int delay;
        public GifFrame(Bitmap im, int del) {
            image = im;
            delay = del;
        }
    }

    public int getDelay(int n) {
        delay = -1;
        if ((n >= 0) && (n < frameCount)) {
            delay = frames.get(n).delay;
        }
        return delay;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public Bitmap getFrame(int n) {
        if (frameCount <= 0) return null;
        n = n % frameCount;
        return frames.get(n).image;
    }

    public int getLoopCount() {
        return loopCount;
    }

    private void setPixels() {
        int[] dest = new int[width * height];
        if (lastDispose > 0) {
            if (lastDispose == 3) {
                int n = frameCount - 2;
                if (n > 0) {
                    lastBitmap = getFrame(n - 1);
                } else {
                    lastBitmap = null;
                }
            }
            if (lastBitmap != null) {
                lastBitmap.getPixels(dest, 0, width, 0, 0, width, height);
                if (lastDispose == 2) {
                    int c = 0;
                    if (!transparency) {
                        c = lastBgColor;
                    }
                    for (int i = 0; i < lrh; i++) {
                        int n1 = (lry + i) * width + lrx;
                        int n2 = n1 + lrw;
                        for (int k = n1; k < n2; k++) {
                            dest[k] = c;
                        }
                    }
                }
            }
        }

        int pass = 1;
        int inc = 8;
        int iline = 0;
        for (int i = 0; i < ih; i++) {
            int line = i;
            if (interlace) {
                if (iline >= ih) {
                    pass++;
                    switch (pass) {
                        case 2: iline = 4; break;
                        case 3: iline = 2; inc = 4; break;
                        case 4: iline = 1; inc = 2; break;
                        default: break;
                    }
                }
                line = iline;
                iline += inc;
            }
            line += iy;
            if (line < height) {
                int k = line * width;
                int dx = k + ix;
                int dlim = dx + iw;
                if ((k + width) < dlim) {
                    dlim = k + width;
                }
                int sx = i * iw;
                while (dx < dlim) {
                    int index = ((int) pixels[sx++]) & 0xff;
                    int c = act[index];
                    if (c != 0) {
                        dest[dx] = c;
                    }
                    dx++;
                }
            }
        }
        image = Bitmap.createBitmap(dest, width, height, Bitmap.Config.ARGB_8888);
    }

    public int read(InputStream is) {
        init();
        if (is != null) {
            in = is;
            readHeader();
            if (!err()) {
                readContents();
                if (frameCount < 0) {
                    status = STATUS_FORMAT_ERROR;
                }
            }
        } else {
            status = STATUS_OPEN_ERROR;
        }
        try {
            if (is != null) is.close();
        } catch (Exception e) { /* ignore */ }
        return status;
    }

    private void decodeBitmapData() {
        int nullCode = -1;
        int npix = iw * ih;

        if ((pixels == null) || (pixels.length < npix)) {
            pixels = new byte[npix];
        }
        if (prefix == null) prefix = new short[MaxStackSize];
        if (suffix == null) suffix = new byte[MaxStackSize];
        if (pixelStack == null) pixelStack = new byte[MaxStackSize + 1];

        int data_size = readByte();
        int clear = 1 << data_size;
        int end_of_information = clear + 1;
        int available = clear + 2;
        int old_code = nullCode;
        int code_size = data_size + 1;
        int code_mask = (1 << code_size) - 1;
        for (int code = 0; code < clear; code++) {
            prefix[code] = 0;
            suffix[code] = (byte) code;
        }

        int datum = 0, bits = 0, first = 0, top = 0, pi = 0, bi = 0, count = 0;
        int i = 0;
        for (i = 0; i < npix; ) {
            if (top == 0) {
                if (bits < code_size) {
                    if (count == 0) {
                        count = readBlock();
                        if (count <= 0) break;
                        bi = 0;
                    }
                    datum += (((int) block[bi]) & 0xff) << bits;
                    bits += 8;
                    bi++;
                    count--;
                    continue;
                }

                int code = datum & code_mask;
                datum >>= code_size;
                bits -= code_size;

                if ((code > available) || (code == end_of_information)) break;
                if (code == clear) {
                    code_size = data_size + 1;
                    code_mask = (1 << code_size) - 1;
                    available = clear + 2;
                    old_code = nullCode;
                    continue;
                }
                if (old_code == nullCode) {
                    pixelStack[top++] = suffix[code];
                    old_code = code;
                    first = code;
                    continue;
                }
                int in_code = code;
                if (code == available) {
                    pixelStack[top++] = (byte) first;
                    code = old_code;
                }
                while (code > clear) {
                    pixelStack[top++] = suffix[code];
                    code = prefix[code];
                }
                first = ((int) suffix[code]) & 0xff;

                if (available >= MaxStackSize) break;
                pixelStack[top++] = (byte) first;
                prefix[available] = (short) old_code;
                suffix[available] = (byte) first;
                available++;

                if (((available & code_mask) == 0) && (available < MaxStackSize)) {
                    code_size++;
                    code_mask += available;
                }
                old_code = in_code;
            }
            top--;
            pixels[pi++] = pixelStack[top];
            i++;
        }

        for (i = pi; i < npix; i++) {
            pixels[i] = 0;
        }
    }

    private boolean err() {
        return status != STATUS_OK;
    }

    private void init() {
        status = STATUS_OK;
        frameCount = 0;
        frames = new ArrayList<GifFrame>();
        gct = null;
        lct = null;
    }

    private int readByte() {
        int curByte = 0;
        try {
            curByte = in.read();
        } catch (Exception e) {
            status = STATUS_FORMAT_ERROR;
        }
        return curByte;
    }

    private int readBlock() {
        blockSize = readByte();
        int n = 0;
        if (blockSize > 0) {
            try {
                int count;
                while (n < blockSize) {
                    count = in.read(block, n, blockSize - n);
                    if (count == -1) break;
                    n += count;
                }
            } catch (Exception e) { /* ignore */ }
            if (n < blockSize) {
                status = STATUS_FORMAT_ERROR;
            }
        }
        return n;
    }

    private int[] readColorTable(int ncolors) {
        int nbytes = 3 * ncolors;
        int[] tab = null;
        byte[] c = new byte[nbytes];
        int n = 0;
        try {
            n = in.read(c);
        } catch (Exception e) { /* ignore */ }
        if (n < nbytes) {
            status = STATUS_FORMAT_ERROR;
        } else {
            tab = new int[256];
            int i = 0;
            int j = 0;
            while (i < ncolors) {
                int r = ((int) c[j++]) & 0xff;
                int g = ((int) c[j++]) & 0xff;
                int b = ((int) c[j++]) & 0xff;
                tab[i++] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        return tab;
    }

    private void readContents() {
        boolean done = false;
        while (!(done || err())) {
            int code = readByte();
            switch (code) {
                case 0x2C: // image separator
                    readBitmap();
                    break;
                case 0x21: // extension
                    code = readByte();
                    switch (code) {
                        case 0xf9: // graphics control extension
                            readGraphicControlExt();
                            break;
                        case 0xff: // application extension
                            readBlock();
                            StringBuilder app = new StringBuilder();
                            for (int i = 0; i < 11; i++) {
                                app.append((char) block[i]);
                            }
                            if (app.toString().equals("NETSCAPE2.0")) {
                                readNetscapeExt();
                            } else {
                                skip();
                            }
                            break;
                        case 0xfe: // comment extension
                            skip();
                            break;
                        case 0x01: // plain text extension
                            skip();
                            break;
                        default: // uninteresting extension
                            skip();
                    }
                    break;
                case 0x3b: // terminator
                    done = true;
                    break;
                case 0x00: // bad byte, but keep going and stop on terminator
                    break;
                default:
                    status = STATUS_FORMAT_ERROR;
            }
        }
    }

    private void readGraphicControlExt() {
        readByte(); // block size
        int packed = readByte();
        dispose = (packed & 0x1c) >> 2;
        if (dispose == 0) {
            dispose = 1;
        }
        transparency = (packed & 1) != 0;
        delay = readShort() * 10;
        transIndex = readByte();
        readByte(); // block terminator
    }

    private void readHeader() {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            id.append((char) readByte());
        }
        if (!id.toString().startsWith("GIF")) {
            status = STATUS_FORMAT_ERROR;
            return;
        }
        readLSD();
        if (gctFlag && !err()) {
            gct = readColorTable(gctSize);
            bgColor = gct[bgIndex];
        }
    }

    private void readBitmap() {
        ix = readShort();
        iy = readShort();
        iw = readShort();
        ih = readShort();

        int packed = readByte();
        lctFlag = (packed & 0x80) != 0;
        interlace = (packed & 0x40) != 0;
        lctSize = 2 << (packed & 7);

        if (lctFlag) {
            lct = readColorTable(lctSize);
            act = lct;
        } else {
            act = gct;
            if (bgIndex == transIndex) {
                bgColor = 0;
            }
        }
        int save = 0;
        if (transparency) {
            save = act[transIndex];
            act[transIndex] = 0;
        }

        if (act == null) {
            status = STATUS_FORMAT_ERROR;
        }
        if (err()) return;

        decodeBitmapData();
        skip();
        if (err()) return;

        frameCount++;
        image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        setPixels();

        frames.add(new GifFrame(image, delay));

        if (transparency) {
            act[transIndex] = save;
        }
        resetFrame();
    }

    private void readLSD() {
        width = readShort();
        height = readShort();
        int packed = readByte();
        gctFlag = (packed & 0x80) != 0;
        gctSize = 2 << (packed & 7);
        bgIndex = readByte();
        pixelAspect = readByte();
    }

    private void readNetscapeExt() {
        do {
            readBlock();
            if (block[0] == 1) {
                int b1 = ((int) block[1]) & 0xff;
                int b2 = ((int) block[2]) & 0xff;
                loopCount = (b2 << 8) | b1;
            }
        } while ((blockSize > 0) && !err());
    }

    private int readShort() {
        return readByte() | (readByte() << 8);
    }

    private void resetFrame() {
        lastDispose = dispose;
        lrx = ix;
        lry = iy;
        lrw = iw;
        lrh = ih;
        lastBitmap = image;
        lastBgColor = bgColor;
        dispose = 0;
        transparency = false;
        delay = 0;
        lct = null;
    }

    private void skip() {
        do {
            readBlock();
        } while ((blockSize > 0) && !err());
    }
}
