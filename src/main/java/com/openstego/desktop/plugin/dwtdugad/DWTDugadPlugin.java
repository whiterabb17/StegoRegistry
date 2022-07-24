/*
 * Steganography utility to hide messages into cover files
 * Author: Samir Vaidya (mailto:syvaidya@gmail.com)
 * Copyright (c) Samir Vaidya
 */

package com.openstego.desktop.plugin.dwtdugad;

import com.openstego.desktop.OpenStegoException;
import com.openstego.desktop.plugin.template.image.WMImagePluginTemplate;
import com.openstego.desktop.util.ImageHolder;
import com.openstego.desktop.util.ImageUtil;
import com.openstego.desktop.util.LabelUtil;
import com.openstego.desktop.util.StringUtil;
import com.openstego.desktop.util.dwt.DWT;
import com.openstego.desktop.util.dwt.Image;
import com.openstego.desktop.util.dwt.ImageTree;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * Plugin for OpenStego which implements the DWT based algorithm by Dugad.
 * <p>
 * This class is based on the code provided by Peter Meerwald at: <a
 * href="http://www.cosy.sbg.ac.at/~pmeerw/Watermarking/">http://www.cosy.sbg.ac.at/~pmeerw/Watermarking/</a>
 * <p>
 * Refer to his thesis on watermarking: Peter Meerwald, Digital Image Watermarking in the Wavelet Transfer Domain,
 * Master's Thesis, Department of Scientific Computing, University of Salzburg, Austria, January 2001.
 */
public class DWTDugadPlugin extends WMImagePluginTemplate {

    /**
     * LabelUtil instance to retrieve labels
     */
    private static final LabelUtil labelUtil = LabelUtil.getInstance(DWTDugadPlugin.NAMESPACE);

    /**
     * Constant for Namespace to use for this plugin
     */
    public final static String NAMESPACE = "DWTDUGAD";

    private static final String SIG_MARKER = "DGSG";
    private static final String WM_MARKER = "DGWM";

    /**
     * Default constructor
     */
    public DWTDugadPlugin() {
        LabelUtil.addNamespace(NAMESPACE, "i18n.DWTDugadPluginLabels");
        DWTDugadErrors.init(); // Initialize error codes
    }

    /**
     * Gives the name of the plugin
     *
     * @return Name of the plugin
     */
    @Override
    public String getName() {
        return "DWTDugad";
    }

    /**
     * Gives a short description of the plugin
     *
     * @return Short description of the plugin
     */
    @Override
    public String getDescription() {
        return labelUtil.getString("plugin.description");
    }

    /**
     * Method to embed the message into the cover data
     *
     * @param msg           Message to be embedded
     * @param msgFileName   Name of the message file. If this value is provided, then the filename should be embedded in
     *                      the cover data
     * @param cover         Cover data into which message needs to be embedded
     * @param coverFileName Name of the cover file
     * @param stegoFileName Name of the output stego file
     * @return Stego data containing the message
     * @throws OpenStegoException Processing issues
     */
    @Override
    public byte[] embedData(byte[] msg, String msgFileName, byte[] cover, String coverFileName, String stegoFileName) throws OpenStegoException {
        ImageHolder image;
        List<int[][]> yuv;
        DWT dwt;
        ImageTree dwtTree;
        ImageTree s;
        Signature sig;
        int[][] luminance;
        int imgType;
        int cols;
        int rows;

        // Cover file is mandatory
        if (cover == null) {
            throw new OpenStegoException(null, NAMESPACE, DWTDugadErrors.ERR_NO_COVER_FILE);
        } else {
            image = ImageUtil.byteArrayToImage(cover, coverFileName);
        }

        imgType = image.getImage().getType();
        cols = image.getImage().getWidth();
        rows = image.getImage().getHeight();
        yuv = ImageUtil.getYuvFromImage(image.getImage());
        luminance = yuv.get(0);

        sig = new Signature(msg);

        // Wavelet transform
        dwt = new DWT(cols, rows, sig.filterID, sig.decompositionLevel, sig.waveletFilterMethod);
        dwtTree = dwt.forwardDWT(luminance);
        s = dwtTree;

        // Embed watermark in all subbands of a decomposition level
        for (int i = 0; i < sig.decompositionLevel; i++) {
            wmSubBand(s.getHorizontal().getImage(), sig.watermark, sig.watermarkLength, sig.alpha, sig.castingThreshold);
            wmSubBand(s.getVertical().getImage(), sig.watermark, sig.watermarkLength, sig.alpha, sig.castingThreshold);
            wmSubBand(s.getDiagonal().getImage(), sig.watermark, sig.watermarkLength, sig.alpha, sig.castingThreshold);
            s = s.getCoarse();
        }

        dwt.inverseDWT(dwtTree, luminance);
        yuv.set(0, luminance);
        image.setImage(ImageUtil.getImageFromYuv(yuv, imgType));

        return ImageUtil.imageToByteArray(image, stegoFileName, this);
    }

    /**
     * Method to extract the message from the stego data
     *
     * @param stegoData     Stego data containing the message
     * @param stegoFileName Name of the stego file
     * @param origSigData   Optional signature data file for watermark
     * @return Extracted message
     * @throws OpenStegoException Processing issues
     */
    @Override
    public byte[] extractData(byte[] stegoData, String stegoFileName, byte[] origSigData) throws OpenStegoException {
        ImageHolder image;
        DWT dwt;
        ImageTree dwtTree;
        ImageTree s;
        Signature sig;
        int[][] luminance;
        int cols;
        int rows;
        Object[] vals;

        image = ImageUtil.byteArrayToImage(stegoData, stegoFileName);

        cols = image.getImage().getWidth();
        rows = image.getImage().getHeight();
        luminance = ImageUtil.getYuvFromImage(image.getImage()).get(0);
        sig = new Signature(origSigData);

        // Wavelet transform
        dwt = new DWT(cols, rows, sig.filterID, sig.decompositionLevel, sig.waveletFilterMethod);
        dwtTree = dwt.forwardDWT(luminance);
        s = dwtTree;

        try (
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)
        ) {
            oos.writeBytes(WM_MARKER);
            oos.writeInt(sig.decompositionLevel);
            oos.writeDouble(sig.alpha);

            for (int i = 0; i < sig.decompositionLevel; i++) {
                vals = invWmSubBand(s.getHorizontal().getImage(), sig.watermark, sig.watermarkLength, sig.detectionThreshold);
                oos.writeInt((Integer) vals[0]);
                oos.writeDouble((Double) vals[1]);
                oos.writeDouble((Double) vals[2]);

                vals = invWmSubBand(s.getVertical().getImage(), sig.watermark, sig.watermarkLength, sig.detectionThreshold);
                oos.writeInt((Integer) vals[0]);
                oos.writeDouble((Double) vals[1]);
                oos.writeDouble((Double) vals[2]);

                vals = invWmSubBand(s.getDiagonal().getImage(), sig.watermark, sig.watermarkLength, sig.detectionThreshold);
                oos.writeInt((Integer) vals[0]);
                oos.writeDouble((Double) vals[1]);
                oos.writeDouble((Double) vals[2]);

                s = s.getCoarse();
            }

            oos.flush();
            return baos.toByteArray();
        } catch (IOException ioEx) {
            throw new OpenStegoException(ioEx);
        }
    }

    /**
     * Method to generate the signature data
     *
     * @return Signature data
     * @throws OpenStegoException Processing issues
     */
    @Override
    public byte[] generateSignature() throws OpenStegoException {
        Random rand;
        Signature sig;

        rand = new Random(StringUtil.passwordHash(this.config.getPassword()));
        sig = new Signature(rand);

        return sig.getSigData();
    }

    /**
     * Method to check the correlation between original signature and the extracted watermark
     *
     * @param origSigData   Original signature data
     * @param watermarkData Extracted watermark data
     * @return Correlation
     * @throws OpenStegoException Processing issues
     */
    @Override
    public double getWatermarkCorrelation(byte[] origSigData, byte[] watermarkData) throws OpenStegoException {
        ObjectInputStream ois;
        byte[] markArr = new byte[WM_MARKER.length()];
        int level;
        int n;
        int ok = 0;
        int m;
        double z;
        double v;
        double alpha;

        try {
            ois = new ObjectInputStream(new ByteArrayInputStream(watermarkData));
            n = ois.read(markArr, 0, WM_MARKER.length());
            if (n == -1 || !WM_MARKER.equals(new String(markArr))) {
                throw new OpenStegoException(null, NAMESPACE, DWTDugadErrors.ERR_SIG_NOT_VALID);
            }

            level = ois.readInt();
            alpha = ois.readDouble();
            n = level * 3;

            for (int i = 0; i < level; i++) {
                // HL subband
                m = ois.readInt();
                z = ois.readDouble();
                v = ois.readDouble();
                if (m != 0) {
                    ok += (z > v * alpha) ? 1 : 0;
                    // diff += ((z - v * alpha) / (1.0 * m));
                } else {
                    n--;
                }

                // LH subband
                m = ois.readInt();
                z = ois.readDouble();
                v = ois.readDouble();
                if (m != 0) {
                    ok += (z > v * alpha) ? 1 : 0;
                    // diff += ((z - v * alpha) / (1.0 * m));
                } else {
                    n--;
                }

                // HH subband
                m = ois.readInt();
                z = ois.readDouble();
                v = ois.readDouble();
                if (m != 0) {
                    ok += (z > v * alpha) ? 1 : 0;
                    // diff += ((z - v * alpha) / (1.0 * m));
                } else {
                    n--;
                }
            }
        } catch (IOException ioEx) {
            throw new OpenStegoException(ioEx);
        }

        return (double) ok / (double) n;
    }

    /**
     * Method to get the usage details of the plugin
     *
     * @return Usage details of the plugin
     */
    @Override
    public String getUsage() {
        return labelUtil.getString("plugin.usage");
    }

    /**
     * Embeds a watermark of 'n' normally distributed values into 'a' coefficents greater than threshold value of a
     * subband
     */
    private void wmSubBand(Image img, double[] wm, int n, double a, double threshold) {
        for (int i = 0; i < img.getWidth() * img.getHeight(); i++) {
            if (Math.abs(img.getData()[i]) > threshold) {
                img.getData()[i] += (a * Math.abs(img.getData()[i]) * wm[i % n]);
            }
        }
    }

    /**
     * Extracts the watermark data from subband
     */
    private Object[] invWmSubBand(Image img, double[] wm, int n, double threshold) {
        int m = 0;
        double z = 0.0;
        double v = 0.0;

        for (int i = 0; i < img.getWidth() * img.getHeight(); i++) {
            if (img.getData()[i] > threshold) {
                z += (img.getData()[i] * wm[i % n]);
                v += Math.abs(img.getData()[i]);
                m++;
            }
        }

        return new Object[]{m, z, v};
    }

    /**
     * Private class for the data structure required for the signature
     */
    private static class Signature {
        /**
         * Signature stamp
         */
        private final byte[] sig = SIG_MARKER.getBytes(StandardCharsets.UTF_8);

        /**
         * Length of the watermark (in bits)
         */
        private int watermarkLength = 1000;

        /**
         * Wavelet filter method
         */
        private int waveletFilterMethod = 2;

        /**
         * Filter number
         */
        private int filterID = 1;

        /**
         * Embedding level
         */
        private int decompositionLevel = 3;

        /**
         * Alpha factor
         */
        private double alpha = 0.2;

        /**
         * Casting threshold
         */
        private double castingThreshold = 40.0;

        /**
         * Detection threshold
         */
        private double detectionThreshold = 50.0;

        /**
         * Watermark data
         */
        private final double[] watermark;

        /**
         * Constructor which generates the watermark data using the given randomizer
         *
         * @param rand Randomizer to use for generating watermark data
         */
        public Signature(Random rand) {
            double x, x1, x2, r;
            this.watermark = new double[this.watermarkLength];

            for (int i = 0; i < this.watermarkLength; i += 2) {
                do {
                    x1 = 2.0 * rand.nextDouble() - 1.0;
                    x2 = 2.0 * rand.nextDouble() - 1.0;
                    x = x1 * x1 + x2 * x2;
                } while (x >= 1.0);
                r = Math.sqrt((-2.0) * Math.log(x) / x);
                x1 *= r;
                x2 *= r;

                this.watermark[i] = x1;
                this.watermark[i + 1] = x2;
            }
        }

        /**
         * Constructor that takes existing the signature data
         *
         * @param sigData Existing signature data
         * @throws OpenStegoException Processing issues
         */
        public Signature(byte[] sigData) throws OpenStegoException {
            ObjectInputStream ois;
            int n;
            byte[] inputSig = new byte[this.sig.length];

            try {
                ois = new ObjectInputStream(new ByteArrayInputStream(sigData));
                n = ois.read(inputSig, 0, this.sig.length);
                if (n == -1 || !(new String(this.sig)).equals(new String(inputSig))) {
                    throw new OpenStegoException(null, NAMESPACE, DWTDugadErrors.ERR_SIG_NOT_VALID);
                }

                this.watermarkLength = ois.readInt();
                this.waveletFilterMethod = ois.readInt();
                this.filterID = ois.readInt();
                this.decompositionLevel = ois.readInt();
                this.alpha = ois.readDouble();
                this.castingThreshold = ois.readDouble();
                this.detectionThreshold = ois.readDouble();

                this.watermark = new double[this.watermarkLength];
                for (int i = 0; i < this.watermarkLength; i++) {
                    this.watermark[i] = ois.readDouble();
                }
            } catch (IOException ioEx) {
                throw new OpenStegoException(ioEx);
            }
        }

        /**
         * Get the signature data generated
         *
         * @return Signature data
         * @throws OpenStegoException Processing issues
         */
        public byte[] getSigData() throws OpenStegoException {
            try (
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos)
            ) {
                oos.write(this.sig);
                oos.writeInt(this.watermarkLength);
                oos.writeInt(this.waveletFilterMethod);
                oos.writeInt(this.filterID);
                oos.writeInt(this.decompositionLevel);
                oos.writeDouble(this.alpha);
                oos.writeDouble(this.castingThreshold);
                oos.writeDouble(this.detectionThreshold);

                for (int i = 0; i < this.watermarkLength; i++) {
                    oos.writeDouble(this.watermark[i]);
                }

                oos.flush();
                return baos.toByteArray();
            } catch (IOException ioEx) {
                throw new OpenStegoException(ioEx);
            }
        }
    }

}
