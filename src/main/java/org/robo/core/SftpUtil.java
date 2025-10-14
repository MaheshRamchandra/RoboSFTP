package org.robo.core;


import com.jcraft.jsch.*;
import java.io.File;

public class SftpUtil {

    public static void uploadWithPassword(String host, int port, String username, String password,
                                          File localFile, String remoteDir, String remoteFileName) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(10000);

        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp sftp = (ChannelSftp) channel;
        try {
            try {
                sftp.stat(remoteDir);
            } catch (SftpException e) {
                // try create
                sftp.mkdir(remoteDir);
            }
            String remotePath = remoteDir.endsWith("/") ? remoteDir + remoteFileName : remoteDir + "/" + remoteFileName;
            sftp.put(localFile.getAbsolutePath(), remotePath);
        } finally {
            sftp.exit();
            session.disconnect();
        }
    }

    public static void uploadWithPrivateKey(String host, int port, String username, File privateKeyFile, String passphrase,
                                            File localFile, String remoteDir, String remoteFileName) throws Exception {
        JSch jsch = new JSch();
        if (passphrase != null && !passphrase.isEmpty()) {
            jsch.addIdentity(privateKeyFile.getAbsolutePath(), passphrase);
        } else {
            jsch.addIdentity(privateKeyFile.getAbsolutePath());
        }

        Session session = jsch.getSession(username, host, port);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(10000);

        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp sftp = (ChannelSftp) channel;
        try {
            try {
                sftp.stat(remoteDir);
            } catch (SftpException e) {
                sftp.mkdir(remoteDir);
            }
            String remotePath = remoteDir.endsWith("/") ? remoteDir + remoteFileName : remoteDir + "/" + remoteFileName;
            sftp.put(localFile.getAbsolutePath(), remotePath);
        } finally {
            sftp.exit();
            session.disconnect();
        }
    }
}

