/*
 * Steganography utility to hide messages into cover files
 * Author: Samir Vaidya (mailto:syvaidya@gmail.com)
 * Copyright (c) Samir Vaidya
 */

package com.openstego.desktop.plugin.template.dct;

import com.openstego.desktop.OpenStegoConfig;
import com.openstego.desktop.OpenStegoException;
import com.openstego.desktop.util.CommonUtil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * This class holds the header data for the data that needs to be embedded in the image.
 * First, the header data gets written inside the image, and then the actual data is written.
 */
public class DCTDataHeader {
    /**
     * Magic string at the start of the header to identify OpenStego embedded data
     */
    public static final byte[] DATA_STAMP = "OSDCT".getBytes(StandardCharsets.UTF_8);

    /**
     * Header version to distinguish between various versions of data embedding. This should be changed to next
     * version, in case the structure of the header is changed.
     */
    public static final byte[] HEADER_VERSION = new byte[]{(byte) 1};

    /**
     * Length of the fixed portion of the header
     */
    private static final int FIXED_HEADER_LENGTH = 7;

    /**
     * Length of the data embedded in the image (excluding the header data)
     */
    private final int dataLength;

    /**
     * Name of the file being embedded in the image (as byte array)
     */
    private final byte[] fileName;

    /**
     * OpenStegoConfig instance to hold the configuration data
     */
    private final OpenStegoConfig config;

    /**
     * This constructor should normally be used when writing the data.
     *
     * @param dataLength Length of the data embedded in the image (excluding the header data)
     * @param fileName   Name of the file of data being embedded
     * @param config     OpenStegoConfig instance to hold the configuration data
     */
    public DCTDataHeader(int dataLength, String fileName, OpenStegoConfig config) {
        this.dataLength = dataLength;
        this.config = config;

        if (fileName == null) {
            this.fileName = new byte[0];
        } else {
            this.fileName = fileName.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * This constructor should be used when reading embedded data from an InputStream.
     *
     * @param dataInStream Data input stream containing the embedded data
     * @param config       OpenStegoConfig instance to hold the configuration data
     * @throws OpenStegoException Processing issues
     */
    public DCTDataHeader(InputStream dataInStream, OpenStegoConfig config) throws OpenStegoException {
        int stampLen;
        int versionLen;
        int fileNameLen;
        int n;
        byte[] header;
        byte[] stamp;
        byte[] version;

        stampLen = DATA_STAMP.length;
        versionLen = HEADER_VERSION.length;
        header = new byte[FIXED_HEADER_LENGTH];
        stamp = new byte[stampLen];
        version = new byte[versionLen];

        try {
            n = dataInStream.read(stamp, 0, stampLen);
            if (n == -1 || !(new String(stamp)).equals(new String(DATA_STAMP))) {
                throw new OpenStegoException(null, DCTPluginTemplate.NAMESPACE, DCTErrors.INVALID_STEGO_HEADER);
            }

            n = dataInStream.read(version, 0, versionLen);
            if (n == -1 || !(new String(version)).equals(new String(HEADER_VERSION))) {
                throw new OpenStegoException(null, DCTPluginTemplate.NAMESPACE, DCTErrors.INVALID_HEADER_VERSION);
            }

            n = dataInStream.read(header, 0, FIXED_HEADER_LENGTH);
            if (n < FIXED_HEADER_LENGTH) {
                throw new OpenStegoException(null, DCTPluginTemplate.NAMESPACE, DCTErrors.INVALID_STEGO_HEADER);
            }
            this.dataLength = (CommonUtil.byteToInt(header[0]) + (CommonUtil.byteToInt(header[1]) << 8) + (CommonUtil.byteToInt(header[2]) << 16)
                    + (CommonUtil.byteToInt(header[3]) << 24));
            fileNameLen = header[4];
            config.setUseCompression(header[5] == 1);
            config.setUseEncryption(header[6] == 1);

            if (fileNameLen == 0) {
                this.fileName = new byte[0];
            } else {
                this.fileName = new byte[fileNameLen];
                n = dataInStream.read(this.fileName, 0, fileNameLen);
                if (n < fileNameLen) {
                    throw new OpenStegoException(null, DCTPluginTemplate.NAMESPACE, DCTErrors.INVALID_STEGO_HEADER);
                }
            }
        } catch (OpenStegoException osEx) {
            throw osEx;
        } catch (Exception ex) {
            throw new OpenStegoException(ex);
        }

        this.config = config;
    }

    /**
     * This method generates the header in the form of byte array based on the parameters provided in the constructor.
     *
     * @return Header data
     */
    public byte[] getHeaderData() {
        byte[] out;
        int stampLen;
        int versionLen;
        int currIndex = 0;

        stampLen = DATA_STAMP.length;
        versionLen = HEADER_VERSION.length;
        out = new byte[stampLen + versionLen + FIXED_HEADER_LENGTH + this.fileName.length];

        System.arraycopy(DATA_STAMP, 0, out, currIndex, stampLen);
        currIndex += stampLen;

        System.arraycopy(HEADER_VERSION, 0, out, currIndex, versionLen);
        currIndex += versionLen;

        out[currIndex++] = (byte) ((this.dataLength & 0x000000FF));
        out[currIndex++] = (byte) ((this.dataLength & 0x0000FF00) >> 8);
        out[currIndex++] = (byte) ((this.dataLength & 0x00FF0000) >> 16);
        out[currIndex++] = (byte) ((this.dataLength & 0xFF000000) >> 24);
        out[currIndex++] = (byte) this.fileName.length;
        out[currIndex++] = (byte) (this.config.isUseCompression() ? 1 : 0);
        out[currIndex++] = (byte) (this.config.isUseEncryption() ? 1 : 0);

        if (this.fileName.length > 0) {
            System.arraycopy(this.fileName, 0, out, currIndex, this.fileName.length);
            //currIndex += this.fileName.length;
        }

        return out;
    }

    /**
     * Get Method for dataLength
     *
     * @return dataLength
     */
    public int getDataLength() {
        return this.dataLength;
    }

    /**
     * Get Method for fileName
     *
     * @return fileName
     */
    public String getFileName() {
        return new String(this.fileName, StandardCharsets.UTF_8);
    }

    /**
     * Method to get size of the current header
     *
     * @return Header size
     */
    public int getHeaderSize() {
        return DATA_STAMP.length + HEADER_VERSION.length + FIXED_HEADER_LENGTH + this.fileName.length;
    }

    /**
     * Method to get the maximum possible size of the header
     *
     * @return Maximum possible header size
     */
    public static int getMaxHeaderSize() {
        // Max file name length assumed to be 256
        return DATA_STAMP.length + HEADER_VERSION.length + FIXED_HEADER_LENGTH + 256;
    }
}
