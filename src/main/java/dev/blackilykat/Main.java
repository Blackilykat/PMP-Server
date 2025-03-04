/*
 * Copyright (C) 2025 Blackilykat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.blackilykat;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.blackilykat.messages.LibraryHashesMessage;
import dev.blackilykat.messages.PlaybackSessionCreateMessage;
import dev.blackilykat.messages.PlaybackSessionListMessage;
import dev.blackilykat.messages.PlaybackSessionUpdateMessage;
import dev.blackilykat.messages.TestMessage;
import dev.blackilykat.messages.WelcomeMessage;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;

public class Main {
    public static ArrayList<Client> clients = new ArrayList<>();
    public static int clientIdCounter = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("Initializing database...");
        Storage.init();
        System.out.println("Initialized database");

        System.out.println("Preparing SSL...");
        SSLContext sslContext;
        try {
            Security.addProvider(new BouncyCastleProvider());
            char[] keyPassword = "key".toCharArray();
            File keyStoreFile = new File("keystore.jks");
            KeyStore keyStore = KeyStore.getInstance("BCFKS", "BC");
            if(!keyStoreFile.exists()) {
                // https://github.com/rodbate/bouncycastle-examples/blob/master/src/main/java/bcfipsin100/tls/Simple.java
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(2048);
                KeyPair keyPair = keyPairGenerator.generateKeyPair();

                X509v1CertificateBuilder builder = new JcaX509v1CertificateBuilder(
                        new X500Name("CN=PMP Server"),
                        BigInteger.valueOf(System.currentTimeMillis()),
                        new Date(System.currentTimeMillis() - 5000L),
                        new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 2000), // expires in 2000 years (basically never)
                        new X500Name("CN=PMP Server"),
                        keyPair.getPublic()
                );
                JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder("SHA384withRSA").setProvider("BC");
                X509Certificate certificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signerBuilder.build(keyPair.getPrivate())));

                keyStore.load(null, null);
                keyStore.setKeyEntry("Key", keyPair.getPrivate(), keyPassword, new X509Certificate[]{certificate});
                keyStore.store(new FileOutputStream(keyStoreFile), null);
            } else {
                keyStore.load(new FileInputStream(keyStoreFile), null);
            }

            KeyManagerFactory factory = KeyManagerFactory.getInstance("SunX509");
            factory.init(keyStore, keyPassword);

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(factory.getKeyManagers(), null, SecureRandom.getInstance("DEFAULT", "BC"));
        } catch(OperatorCreationException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Prepared SSL");

        System.out.println("Starting file transfer server...");
        HttpsServer fileTransferHttpServer = HttpsServer.create(new InetSocketAddress(5001), 0);
        fileTransferHttpServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        fileTransferHttpServer.createContext("/", new FileTransferHttpHandler());
        fileTransferHttpServer.start();
        System.out.println("Started file transfer server");

        System.out.println("Starting main server");

        SSLServerSocket serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(5000);

        while(true) {
            Client client = new Client((SSLSocket) serverSocket.accept(), clientIdCounter++);
            client.startSending();
            client.send(new WelcomeMessage(client.clientId, Storage.getCurrentActionID()));
            System.out.println("Connected to client " + client);
            System.out.println("All connected clients: " + clients.toString());
            Client.broadcast(new TestMessage(client.clientId));
            client.send(LibraryHashesMessage.create());
            client.send(new PlaybackSessionListMessage(PlaybackSession.getAvailableSessions()));
            client.startReceiving();
        }
    }
}
