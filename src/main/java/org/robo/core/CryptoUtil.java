package org.robo.core;


import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public class CryptoUtil {

    // AES CBC encryption, writes IV (16 bytes) first and then ciphertext
    // AES CBC encryption using user-provided keyBytes and ivBytes
    /**
     * AES encryption using CBC or ECB mode.
     * If ivBytes is provided, uses CBC; otherwise, ECB.
     *
     * @param keyBytes   Secret key as UTF-8 bytes (16, 24, 32 bytes for 128/192/256-bit AES)
     * @param ivBytes    Initialization Vector as UTF-8 bytes (16 bytes for CBC). Null for ECB.
     * @param inputFile  Plaintext file to encrypt
     * @param outFile    Output file for encrypted data
     * @throws Exception
     */
    /**
     * Encrypts a file using AES in CBC or ECB mode.
     * Writes Base64-encoded output to outFile.
     *
     * @param keyBytes AES key (16/24/32 bytes)
     * @param ivBytes  Initialization Vector (16 bytes for CBC), or null for ECB
     * @param inputFile  Input plaintext file
     * @param outFile    Output encrypted file (Base64)
     * @throws Exception
     */
    public static void encryptFileAES_CBC(byte[] keyBytes, byte[] ivBytes, File inputFile, File outFile) throws Exception {
        if (keyBytes == null) throw new IllegalArgumentException("Key cannot be null");
        if (!(keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32))
            throw new IllegalArgumentException("AES key must be 16/24/32 bytes");

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher;

        boolean isCBC = ivBytes != null;
        if (isCBC) {
            if (ivBytes.length != 16) throw new IllegalArgumentException("IV must be exactly 16 bytes for CBC mode");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        } else {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        }

        // Read entire file
        byte[] inputBytes;
        try (FileInputStream fis = new FileInputStream(inputFile)) {
            inputBytes = fis.readAllBytes();
        }

        // Encrypt
        byte[] encrypted = cipher.doFinal(inputBytes);

        // Encode to Base64 exactly like FE
        String base64Output = Base64.getEncoder().encodeToString(encrypted);

        // Write Base64 string to output file
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(base64Output.getBytes(StandardCharsets.UTF_8));
        }
    }


    // Derive key from passphrase using PBKDF2 and encrypt using AES-CBC
    public static void encryptFileWithPassword(String passphrase, File inputFile, File outFile) throws Exception {
        // generate salt randomly and write it to out file so decryptor can use it
        SecureRandom sr = new SecureRandom();
        byte[] salt = new byte[16];
        sr.nextBytes(salt);

        // PBKDF2 with HmacSHA256
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, 100_000, 256);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();

        // write salt (16 bytes) then IV (16 bytes) then ciphertext
        byte[] iv = new byte[16];
        sr.nextBytes(iv);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outFile)) {

            // write salt then IV
            fos.write(salt);
            fos.write(iv);

            byte[] buffer = new byte[4096];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                byte[] out = cipher.update(buffer, 0, n);
                if (out != null) fos.write(out);
            }

            byte[] finalBytes = cipher.doFinal();
            if (finalBytes != null) fos.write(finalBytes);
        }
    }


    // Helper - hex string to bytes
    public static byte[] hexStringToBytes(String hex) {
        hex = hex.replaceAll("[^0-9A-Fa-f]", "");
        int len = hex.length();
        if (len % 2 != 0) throw new IllegalArgumentException("Hex string length must be even");
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
