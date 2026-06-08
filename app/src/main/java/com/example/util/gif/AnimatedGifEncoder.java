package com.example.util.gif;

import android.graphics.Bitmap;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Encodes a GIF file (static or animated) from a sequence of {@link Bitmap} frames.
 *
 * Android port of Kevin Weiner's AnimatedGifEncoder (FileHook port of giflib /
 * Jef Poskanzer's Java GIF encoder). Public domain — no copyright.
 * Source lineage: https://github.com/nbadal/android-gif-encoder
 */
public class AnimatedGifEncoder {

    private int width;            // image size
    private int height;
    private Integer transparent = null; // transparent color if given
    private int transIndex;       // transparent index in color table
    private int repeat = -1;      // no repeat
    private int delay = 0;        // frame delay (hundredths)
    private boolean started = false;
    private OutputStream out;
    private Bitmap image;         // current frame
    private byte[] pixels;        // BGR byte array from frame
    private byte[] indexedPixels; // converted frame indexed to palette
    private int colorDepth;       // number of bit planes
    private byte[] colorTab;      // RGB palette
    private boolean[] usedEntry = new boolean[256]; // active palette entries
    private int palSize = 7;      // color table size (bits-1)
    private int dispose = -1;     // disposal code (-1 = use default)
    private boolean closeStream = false;
    private boolean firstFrame = true;
    private boolean sizeSet = false; // if false, get size from first frame
    private int sample = 10;      // default sample interval for quantizer

    /** Sets the delay time between each frame, in milliseconds. */
    public void setDelay(int ms) {
        delay = Math.round(ms / 10.0f);
    }

    /** Sets the GIF frame disposal code for the last added frame. */
    public void setDispose(int code) {
        if (code >= 0) {
            dispose = code;
        }
    }

    /** Sets the number of times the set of GIF frames should be played. 0 = loop forever. */
    public void setRepeat(int iter) {
        if (iter >= 0) {
            repeat = iter;
        }
    }

    /** Sets the transparent color for the last added frame and any subsequent frames. */
    public void setTransparent(int color) {
        transparent = color;
    }

    /** Sets quality of color quantization (1 best/slowest, 20+ fast/worse). */
    public void setQuality(int quality) {
        if (quality < 1) quality = 1;
        sample = quality;
    }

    /** Sets the GIF frame size. */
    public void setSize(int w, int h) {
        width = w;
        height = h;
        if (width < 1) width = 320;
        if (height < 1) height = 240;
        sizeSet = true;
    }

    /** Adds next GIF frame. First frame sets the GIF size if not specified. */
    public boolean addFrame(Bitmap im) {
        if ((im == null) || !started) {
            return false;
        }
        boolean ok = true;
        try {
            if (!sizeSet) {
                setSize(im.getWidth(), im.getHeight());
            }
            image = im;
            getImagePixels();      // convert to correct format if necessary
            analyzePixels();       // build color table & map pixels
            if (firstFrame) {
                writeLSD();        // logical screen descriptor
                writePalette();    // global color table
                if (repeat >= 0) {
                    writeNetscapeExt();
                }
            }
            writeGraphicCtrlExt(); // write graphic control extension
            writeImageDesc();      // image descriptor
            if (!firstFrame) {
                writePalette();    // local color table
            }
            writePixels();         // encode and write pixel data
            firstFrame = false;
        } catch (IOException e) {
            ok = false;
        }
        return ok;
    }

    /** Flushes any pending data and closes output file. */
    public boolean finish() {
        if (!started) return false;
        boolean ok = true;
        started = false;
        try {
            out.write(0x3b); // gif trailer
            out.flush();
            if (closeStream) {
                out.close();
            }
        } catch (IOException e) {
            ok = false;
        }
        // reset for subsequent use
        transIndex = 0;
        out = null;
        image = null;
        pixels = null;
        indexedPixels = null;
        colorTab = null;
        closeStream = false;
        firstFrame = true;
        return ok;
    }

    /** Initiates GIF file creation on the given stream. */
    public boolean start(OutputStream os) {
        if (os == null) return false;
        boolean ok = true;
        closeStream = false;
        out = os;
        try {
            writeString("GIF89a"); // header
        } catch (IOException e) {
            ok = false;
        }
        return started = ok;
    }

    private void analyzePixels() {
        int len = pixels.length;
        int nPix = len / 3;
        indexedPixels = new byte[nPix];
        NeuQuant nq = new NeuQuant(pixels, len, sample);
        colorTab = nq.process(); // create reduced palette
        // convert map from BGR to RGB
        for (int i = 0; i < colorTab.length; i += 3) {
            byte temp = colorTab[i];
            colorTab[i] = colorTab[i + 2];
            colorTab[i + 2] = temp;
            usedEntry[i / 3] = false;
        }
        // map image pixels to new palette
        int k = 0;
        for (int i = 0; i < nPix; i++) {
            int index = nq.map(pixels[k++] & 0xff, pixels[k++] & 0xff, pixels[k++] & 0xff);
            usedEntry[index] = true;
            indexedPixels[i] = (byte) index;
        }
        pixels = null;
        colorDepth = 8;
        palSize = 7;
        // get closest match to transparent color if specified
        if (transparent != null) {
            transIndex = findClosest(transparent);
        }
    }

    private int findClosest(int color) {
        if (colorTab == null) return -1;
        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;
        int minpos = 0;
        int dmin = 256 * 256 * 256;
        int len = colorTab.length;
        for (int i = 0; i < len; ) {
            int dr = r - (colorTab[i++] & 0xff);
            int dg = g - (colorTab[i++] & 0xff);
            int db = b - (colorTab[i++] & 0xff);
            int d = dr * dr + dg * dg + db * db;
            int index = i / 3;
            if (usedEntry[index] && (d < dmin)) {
                dmin = d;
                minpos = index;
            }
        }
        return minpos;
    }

    private void getImagePixels() {
        int w = image.getWidth();
        int h = image.getHeight();
        if ((w != width) || (h != height)) {
            Bitmap temp = Bitmap.createScaledBitmap(image, width, height, true);
            image = temp;
        }
        int[] data = new int[width * height];
        image.getPixels(data, 0, width, 0, 0, width, height);
        pixels = new byte[data.length * 3];
        for (int i = 0; i < data.length; i++) {
            int td = data[i];
            int idx = i * 3;
            pixels[idx] = (byte) (td & 0xFF);          // B
            pixels[idx + 1] = (byte) ((td >> 8) & 0xFF); // G
            pixels[idx + 2] = (byte) ((td >> 16) & 0xFF); // R
        }
    }

    private void writeGraphicCtrlExt() throws IOException {
        out.write(0x21); // extension introducer
        out.write(0xf9); // GCE label
        out.write(4);    // data block size
        int transp, disp;
        if (transparent == null) {
            transp = 0;
            disp = 0; // dispose = no action
        } else {
            transp = 1;
            disp = 2; // force clear if using transparent color
        }
        if (dispose >= 0) {
            disp = dispose & 7; // user override
        }
        disp <<= 2;
        out.write(0 | disp | 0 | transp);
        writeShort(delay); // delay x 1/100 sec
        out.write(transIndex); // transparent color index
        out.write(0); // block terminator
    }

    private void writeImageDesc() throws IOException {
        out.write(0x2c); // image separator
        writeShort(0);   // image position x,y = 0,0
        writeShort(0);
        writeShort(width);  // image size
        writeShort(height);
        if (firstFrame) {
            out.write(0); // no LCT - GCT used for first (or only) frame
        } else {
            out.write(0x80 | 0 | 0 | 0 | palSize); // specify normal LCT
        }
    }

    private void writeLSD() throws IOException {
        writeShort(width);  // logical screen size
        writeShort(height);
        out.write((0x80 | 0x70 | 0x00 | palSize)); // packed fields
        out.write(0); // background color index
        out.write(0); // pixel aspect ratio - assume 1:1
    }

    private void writeNetscapeExt() throws IOException {
        out.write(0x21);    // extension introducer
        out.write(0xff);    // app extension label
        out.write(11);      // block size
        writeString("NETSCAPE2.0"); // app id + auth code
        out.write(3);       // sub-block size
        out.write(1);       // loop sub-block id
        writeShort(repeat); // loop count (extra iterations, 0=repeat forever)
        out.write(0);       // block terminator
    }

    private void writePalette() throws IOException {
        out.write(colorTab, 0, colorTab.length);
        int n = (3 * 256) - colorTab.length;
        for (int i = 0; i < n; i++) {
            out.write(0);
        }
    }

    private void writePixels() throws IOException {
        LZWEncoder encoder = new LZWEncoder(width, height, indexedPixels, colorDepth);
        encoder.encode(out);
    }

    private void writeShort(int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private void writeString(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            out.write((byte) s.charAt(i));
        }
    }
}
